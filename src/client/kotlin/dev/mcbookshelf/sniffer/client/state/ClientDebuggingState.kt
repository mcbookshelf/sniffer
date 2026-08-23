package dev.mcbookshelf.sniffer.client.state

/**
 * Local mirror of whether an execution is paused, which the HUD reads each frame to draw the bug icon.
 *
 * @author theogiraudet
 */
object ClientDebuggingState {

    @JvmStatic
    @Volatile
    var debugging: Boolean = false
}
