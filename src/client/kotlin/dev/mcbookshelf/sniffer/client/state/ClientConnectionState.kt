package dev.mcbookshelf.sniffer.client.state

/**
 * Client-local mirror of the server's DAP connection flag, synced via
 * [dev.mcbookshelf.sniffer.network.SetDapConnectedPayload]. Read by the HUD
 * overlay each frame to pick between the "connected" and "disconnected"
 * status icons.
 */
object ClientConnectionState {

    @JvmStatic
    @Volatile
    var connected: Boolean = false
}
