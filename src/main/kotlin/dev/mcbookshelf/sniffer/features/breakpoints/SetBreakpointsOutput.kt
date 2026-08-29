package dev.mcbookshelf.sniffer.features.breakpoints

import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.source.Line

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
 * @property line the line the breakpoint was requested on
 * @property id unique id of the breakpoint, `null` when it could not be set
 * @property verified whether the line was mapped to a function
 * @property message why the breakpoint could not be verified, `null` when it was
 * @author theogiraudet
 */
data class BreakpointResult(
    val line: Line,
    val id: Int?,
    val verified: Boolean,
    val message: String? = null,
)
