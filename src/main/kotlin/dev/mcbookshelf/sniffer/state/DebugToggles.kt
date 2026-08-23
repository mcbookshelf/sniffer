package dev.mcbookshelf.sniffer.state

/**
 * Local mirror of the debug mode of this player, which the HUD overlay reads to pick its icons.
 * [DebugModeState] holds the truth and this value is refreshed from the payload it broadcasts.
 * On a dedicated server nothing reads it.
 *
 * @author theogiraudet
 */
object DebugToggles {

    @JvmStatic
    var debugMode: Boolean = false
}