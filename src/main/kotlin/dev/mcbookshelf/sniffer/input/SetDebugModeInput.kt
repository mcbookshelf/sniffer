package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Enable or disable the debug subsystem at runtime.
 *
 * @param enabled `true` to turn debugging on, `false` to turn it off.
 */
data class SetDebugModeInput(val enabled: Boolean) : IInput
