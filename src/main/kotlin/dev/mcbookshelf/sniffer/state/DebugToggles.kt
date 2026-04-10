package dev.mcbookshelf.sniffer.state

/**
 * Client-local mirror of this player's debug-mode state.
 *
 * Debug mode is HUD-only — it never gates breakpoint firing or any other
 * debugging capability. The server is the source of truth
 * ([DebugModeState]); this singleton is updated whenever the client receives
 * a [dev.mcbookshelf.sniffer.network.SetDebugModePayload]. The HUD overlay reads
 * it to decide what icons to draw.
 *
 * On a dedicated server this singleton holds no meaningful state — the field
 * is only consulted from client-side code.
 */
object DebugToggles {

    @JvmStatic
    var debugMode: Boolean = false
}