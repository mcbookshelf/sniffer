package dev.mcbookshelf.sniffer.client.state

/**
 * Local mirror of whether a DAP client is attached, which the HUD reads each frame to pick its status icon.
 *
 * @author theogiraudet
 */
object ClientConnectionState {

    @JvmStatic
    @Volatile
    var connected: Boolean = false
}
