package dev.mcbookshelf.sniffer.state

import dev.mcbookshelf.sniffer.network.SetDapConnectedPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

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
