package dev.mcbookshelf.sniffer.features.breakpoints

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Triggers a breakpoint at the current position, which is what `/breakpoint` does.
 *
 * @property condition command gating the halt, `null` when unconditional.
 *   The breakpoint only triggers when that command reports success.
 * @author theogiraudet
 */
data class TriggerBreakpointInput(val condition: String? = null) : IInput
