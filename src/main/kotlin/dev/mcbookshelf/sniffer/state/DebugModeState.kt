package dev.mcbookshelf.sniffer.state

import java.util.UUID

/**
 * Whether each player has debug mode on, as the server knows it.
 *
 * Debug mode gates nothing, breakpoints fire either way, it only decides whether the player sees the HUD overlay,
 * so two players on the same server can hold different values.
 * The values live in memory for as long as the server runs, and survive a reconnect.
 *
 * @author theogiraudet
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