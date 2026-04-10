package dev.mcbookshelf.sniffer.state

import java.util.UUID

/**
 * Server-side, per-player debug-mode state.
 *
 * Debug mode is a gamemode-like, HUD-only toggle: it does **not** gate any
 * debugging capability (breakpoints still fire regardless) — it only controls
 * whether the player sees the Sniffer HUD overlay. Several players on the
 * same server can therefore have different values.
 *
 * Persistence is in-memory for the lifetime of the server: a player who
 * reconnects within the same session keeps their setting. The map is cleared
 * on server start / stop via [clear].
 */
object DebugModeState {

    private val states: MutableMap<UUID, Boolean> = HashMap()

    @JvmStatic
    fun isEnabled(player: UUID): Boolean = states[player] ?: false

    @JvmStatic
    fun setEnabled(player: UUID, enabled: Boolean) {
        states[player] = enabled
    }

    @JvmStatic
    fun clear() {
        states.clear()
    }
}