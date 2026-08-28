package dev.mcbookshelf.sniffer.features.debugmode

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Turns the HUD overlay of a player on or off.
 *
 * @property enabled `true` to show it, `false` to hide it
 * @author theogiraudet
 */
data class SetDebugModeInput(val enabled: Boolean) : IInput
