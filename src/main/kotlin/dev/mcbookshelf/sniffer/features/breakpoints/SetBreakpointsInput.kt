package dev.mcbookshelf.sniffer.features.breakpoints

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * A single requested breakpoint.
 *
 * @property line zero indexed line to put the breakpoint on
 * @property condition condition command, `null` when unconditional.
 *   The breakpoint only pauses when that command reports success.
 * @author theogiraudet
 */
data class BreakpointSpec(val line: Int, val condition: String? = null)

/**
 * Sets the breakpoints of a file, replacing the ones it already had.
 *
 * @property filePath filesystem path of the source file
 * @property breakpoints the requested breakpoints
 * @author theogiraudet
 */
data class SetBreakpointsInput(val filePath: String?, val breakpoints: List<BreakpointSpec>) : IInput
