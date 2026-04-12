package dev.mcbookshelf.sniffer.state

import dev.mcbookshelf.sniffer.network.SetDapConnectedPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server-side authoritative flag for whether a DAP client is currently
 * attached to the WebSocket server. The HUD overlay lives in the client
 * process and cannot read this directly on a dedicated server, so every
 * change broadcasts a [SetDapConnectedPayload] to all online players; the
 * client mirror is [dev.mcbookshelf.sniffer.client.state.ClientConnectionState].
 *
 * Mutated from the Tyrus IO / server thread when sessions open, close, or
 * finish the auth handshake.
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

    /**
     * Send a HUD-state payload to every currently online player.
     * Shared by sibling state holders (e.g. [SteppingState]) that need the
     * same broadcast semantics.
     */
    @JvmStatic
    fun broadcast(payload: CustomPacketPayload) {
        val server = runCatching { ServerReference.get() }.getOrNull() ?: return
        for (player in PlayerLookup.all(server)) {
            try {
                ServerPlayNetworking.send(player, payload)
            } catch (_: Exception) {
                // Best-effort: a player may be mid-disconnect.
            }
        }
    }
}
