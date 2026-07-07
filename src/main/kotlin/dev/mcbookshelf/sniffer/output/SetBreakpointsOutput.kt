package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of setting the breakpoints of a file.
 *
 * @property results one entry per requested breakpoint line
 * @author theogiraudet
 */
data class SetBreakpointsOutput(val results: List<BreakpointResult>) : Output

/**
 * What became of a single requested breakpoint.
 *
 * @property line zero indexed line the breakpoint was requested on
 * @property id unique id of the breakpoint, `null` when it could not be set
 * @property verified whether the line was mapped to a function
 * @property message why the breakpoint could not be verified, `null` when it was
 * @author theogiraudet
 */
data class BreakpointResult(
    val line: Int,
    val id: Int?,
    val verified: Boolean,
    val message: String? = null,
)
