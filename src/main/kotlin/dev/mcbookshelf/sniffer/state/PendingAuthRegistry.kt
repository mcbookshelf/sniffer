package dev.mcbookshelf.sniffer.state

import dev.mcbookshelf.sniffer.util.Extension.addSnifferPrefix
import jakarta.websocket.CloseReason
import jakarta.websocket.Session
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Tracks DAP WebSocket connections that are awaiting in-game approval from
 * a Minecraft operator.
 *
 * Thread-safety: mutated from Tyrus IO threads (onOpen / onClose), the
 * server thread (packet receiver), and the timeout scheduler. All state
 * mutations go through the [lock] monitor; lookups by [Session] use a
 * secondary index maintained alongside the primary by-UUID index.
 */
object PendingAuthRegistry {
    private val logger = LoggerFactory.getLogger("sniffer")

    data class PendingAuth(
        val requestId: UUID,
        val session: Session,
        val playerUuid: UUID,
        val onApproved: () -> Unit,
        val onRejected: () -> Unit,
        var timeoutTask: ScheduledFuture<*>? = null,
    )

    private val byPlayer = HashMap<UUID, PendingAuth>()
    private val bySession = HashMap<Session, PendingAuth>()
    private val lock = Any()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "sniffer-auth-timeout").apply { isDaemon = true }
    }

    /**
     * Register a new pending auth, superseding any prior pending request for
     * the same player. The previous request (if any) is cancelled and its
     * session closed with a policy-violation reason.
     */
    fun register(pending: PendingAuth, timeoutSeconds: Int) {
        val superseded = synchronized(lock) {
            val prior = byPlayer.remove(pending.playerUuid)
            prior?.let { bySession.remove(it.session) }
            byPlayer[pending.playerUuid] = pending
            bySession[pending.session] = pending
            prior
        }
        superseded?.let {
            it.timeoutTask?.cancel(false)
            logger.info("Superseded pending auth for player {}", it.playerUuid)
            closeSession(it.session, "superseded by newer auth request")
        }

        pending.timeoutTask = scheduler.schedule({
            val timedOut = synchronized(lock) {
                val current = byPlayer[pending.playerUuid]
                if (current === pending) {
                    byPlayer.remove(pending.playerUuid)
                    bySession.remove(pending.session)
                    pending
                } else null
            }
            if (timedOut != null) {
                logger.info("Auth prompt timed out for player {}", pending.playerUuid)
                try { pending.onRejected() } catch (e: Exception) { logger.error("onRejected failed", e) }
                notifyPlayerTimedOut(pending.playerUuid)
                closeSession(pending.session, "auth prompt timed out")
            }
        }, timeoutSeconds.toLong(), TimeUnit.SECONDS)
    }

    /**
     * Resolve a pending auth by player UUID. Called from the serverbound
     * payload receiver after the player clicks Accept or Reject.
     */
    fun resolve(playerUuid: UUID, requestId: UUID, accepted: Boolean) {
        val pending = synchronized(lock) {
            val current = byPlayer[playerUuid] ?: return
            if (current.requestId != requestId) return
            byPlayer.remove(playerUuid)
            bySession.remove(current.session)
            current
        }
        pending.timeoutTask?.cancel(false)
        if (accepted) {
            try { pending.onApproved() } catch (e: Exception) { logger.error("onApproved failed", e) }
        } else {
            try { pending.onRejected() } catch (e: Exception) { logger.error("onRejected failed", e) }
            closeSession(pending.session, "auth rejected by player")
        }
    }

    /**
     * Cancel any pending auth tied to the given session. Called from
     * `WebSocketServer.onClose` / `onError` so a dropped client cannot leak
     * a pending prompt.
     */
    fun cancel(session: Session) {
        val pending = synchronized(lock) {
            val current = bySession.remove(session) ?: return
            byPlayer.remove(current.playerUuid)
            current
        }
        pending.timeoutTask?.cancel(false)
        try { pending.onRejected() } catch (e: Exception) { logger.error("onRejected failed", e) }
    }

    /** Server stop: drop everything. */
    fun clearAll() {
        val all = synchronized(lock) {
            val snapshot = byPlayer.values.toList()
            byPlayer.clear()
            bySession.clear()
            snapshot
        }
        all.forEach {
            it.timeoutTask?.cancel(false)
            closeSession(it.session, "server stopping")
        }
    }

    private fun notifyPlayerTimedOut(playerUuid: java.util.UUID) {
        val server = runCatching { ServerReference.get() }.getOrNull() ?: return
        server.execute {
            val player = server.playerList.getPlayer(playerUuid) ?: return@execute
            player.sendSystemMessage(addSnifferPrefix(Component.translatable("sniffer.auth.timed_out")))
        }
    }

    private fun closeSession(session: Session, reason: String) {
        try {
            if (session.isOpen) {
                session.close(CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason))
            }
        } catch (e: Exception) {
            logger.warn("Failed to close session: {}", e.message)
        }
    }
}
