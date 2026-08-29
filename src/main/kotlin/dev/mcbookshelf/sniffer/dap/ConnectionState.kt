package dev.mcbookshelf.sniffer.dap

import dev.mcbookshelf.sniffer.network.SetDapConnectedPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer

/**
 * Whether a DAP client is attached, as the server sees it.
 *
 * The HUD overlay runs in the client process and cannot read this on a dedicated server,
 * so every change is broadcast as a [SetDapConnectedPayload].
 * Mutated from the Tyrus and server threads, when a session opens, closes or finishes its handshake.
 *
 * @author theogiraudet
 */
object ConnectionState {

    @Volatile
    private var connected: Boolean = false

    @JvmStatic
    fun isConnected(): Boolean = connected

    @JvmStatic
    fun setConnected(value: Boolean) {
        if (connected == value) return
        connected = value
        broadcast(SetDapConnectedPayload(value))
    }

    @Volatile
    private var attachedUser: String? = null

    /**
     * Records the player the session named, as the `user` parameter of its URL, `null` when it named none.
     */
    @JvmStatic
    fun setAttachedUser(user: String?) {
        attachedUser = user
    }

    /**
     * The player the debugger is attached to, `null` when the session named none and none can be assumed.
     *
     * The rule is the one the approval prompt uses to decide whom to ask: the player the URL named,
     * or the host of a singleplayer world, which the URL may leave out.
     */
    @JvmStatic
    fun attachedPlayer(): ServerPlayer? {
        val server = runCatching { ServerReference.get() }.getOrNull() ?: return null
        attachedUser?.let { return server.playerList.getPlayerByName(it) }
        if (!server.isSingleplayer) return null
        return server.singleplayerProfile?.let { server.playerList.getPlayer(it.id) }
    }

    /** Sends a HUD payload to every online player, for any state holder needing it. */
    @JvmStatic
    fun broadcast(payload: CustomPacketPayload) {
        val server = runCatching { ServerReference.get() }.getOrNull() ?: return
        for (player in PlayerLookup.all(server)) {
            try {
                ServerPlayNetworking.send(player, payload)
            } catch (_: Exception) {
                // A player may be in the middle of a disconnect.
            }
        }
    }
}
