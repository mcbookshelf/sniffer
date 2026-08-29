package dev.mcbookshelf.sniffer.dap

import dev.mcbookshelf.sniffer.chat.SnifferChat
import jakarta.websocket.CloseReason
import jakarta.websocket.Session
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Tracks the DAP connections awaiting an in game approval from an operator.
 *
 * Mutations come from the Tyrus IO threads, the server thread and the timeout scheduler,
 * so they all go through the [lock] monitor.
 * Sessions get their own index, maintained alongside the one keyed by player.
 *
 * @author theogiraudet
 */
object PendingAuthRegistry {
    private val logger = LoggerFactory.getLogger("sniffer")

    /**
     * One connection waiting for its player to answer.
     *
     * @property requestId identifies the request, and is echoed back by the answer
     * @property session the WebSocket connection being authenticated
     * @property playerUuid the player being asked
     * @property onApproved run when the player accepts
     * @property onRejected run when the player refuses, when the prompt times out, or when the socket drops
     * @property timeoutTask the scheduled rejection, cancelled as soon as the request is resolved
     */
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

    /**
     * How long a player is left alone after rejecting a prompt or letting it time out.
     * Requests arriving in that window are refused without prompting, so a remote client cannot spam the modal.
     */
    private const val REJECTION_COOLDOWN_MS = 10_000L
    private val rejectionCooldownUntil = ConcurrentHashMap<UUID, Long>()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "sniffer-auth-timeout").apply { isDaemon = true }
    }

    /**
     * Registers a new pending request, superseding any earlier one for the same player.
     * The superseded request is cancelled and its session closed.
     *
     * @param pending the request to register, already carrying its callbacks
     * @param timeoutSeconds how long the player has to answer before the request is rejected
     */
    fun register(pending: PendingAuth, timeoutSeconds: Int) {
        val cooldownUntil = rejectionCooldownUntil[pending.playerUuid] ?: 0L
        if (System.currentTimeMillis() < cooldownUntil) {
            logger.info("Auth request for player {} refused: rejection cooldown active", pending.playerUuid)
            try { pending.onRejected() } catch (e: Exception) { logger.error("onRejected failed", e) }
            closeSession(pending.session, "auth recently rejected, retry later")
            return
        }
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
                startCooldown(pending.playerUuid)
                try { pending.onRejected() } catch (e: Exception) { logger.error("onRejected failed", e) }
                notifyPlayerTimedOut(pending.playerUuid)
                closeSession(pending.session, "auth prompt timed out")
            }
        }, timeoutSeconds.toLong(), TimeUnit.SECONDS)
    }

    /** Resolves a pending request, from the payload the player sends back by clicking Accept or Reject. */
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
            startCooldown(playerUuid)
            try { pending.onRejected() } catch (e: Exception) { logger.error("onRejected failed", e) }
            closeSession(pending.session, "auth rejected by player")
        }
    }

    private fun startCooldown(playerUuid: UUID) {
        rejectionCooldownUntil[playerUuid] = System.currentTimeMillis() + REJECTION_COOLDOWN_MS
    }

    /** Cancels the request tied to [session], so a dropped client cannot leave a prompt behind. */
    fun cancel(session: Session) {
        val pending = synchronized(lock) {
            val current = bySession.remove(session) ?: return
            byPlayer.remove(current.playerUuid)
            current
        }
        pending.timeoutTask?.cancel(false)
        try { pending.onRejected() } catch (e: Exception) { logger.error("onRejected failed", e) }
    }

    /** Drops every pending request and cooldown, on server stop. */
    fun clearAll() {
        rejectionCooldownUntil.clear()
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
            SnifferChat.tell(player, "sniffer.auth.timed_out")
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
