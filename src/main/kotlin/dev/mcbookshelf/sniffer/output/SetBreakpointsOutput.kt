package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of setting breakpoints for a file.
 *
 * @property results one entry per requested breakpoint line.
 */
data class SetBreakpointsOutput(val results: List<BreakpointResult>) : Output

/**
 * Verification result for a single breakpoint.
 *
 * @property line the 0-indexed line number.
 * @property id the unique breakpoint ID, or null if verification failed.
 * @property verified whether the breakpoint was successfully mapped to a function.
 */
data class BreakpointResult(
    val line: Int,
    val id: Int?,
    val verified: Boolean,
)
