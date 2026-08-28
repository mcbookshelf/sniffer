package dev.mcbookshelf.sniffer.features.breakpoints

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * What came of a trigger request.
 *
 * A condition that fails leaves [triggered] false with no [error]: the halt was declined, not botched,
 * which is what lets an entrypoint stay quiet about it rather than report a failure on every pass through a hot function.
 *
 * @property triggered whether execution was actually halted
 * @property error why the condition could not be run, `null` when there was nothing wrong with it
 * @author theogiraudet
 */
data class TriggerBreakpointOutput(val triggered: Boolean, val error: String? = null) : Output
