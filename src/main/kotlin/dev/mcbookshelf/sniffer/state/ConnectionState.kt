package dev.mcbookshelf.sniffer.state

/**
 * Whether a DAP client is currently connected via WebSocket.
 *
 * Read by the client-side HUD overlay; set by [dev.mcbookshelf.sniffer.dap.WebSocketServer].
 */
object ConnectionState {

    @JvmStatic
    var clientConnected: Boolean = false
}
