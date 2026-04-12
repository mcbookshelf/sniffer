package dev.mcbookshelf.sniffer.client.state

/**
 * Client-local mirror of the server's [dev.mcbookshelf.sniffer.state.SteppingState.isDebugging]
 * flag, synced via [dev.mcbookshelf.sniffer.network.SetDebuggingPayload]. Read
 * by the HUD overlay each frame to decide whether to draw the bug icon.
 */
object ClientDebuggingState {

    @JvmStatic
    @Volatile
    var debugging: Boolean = false
}
