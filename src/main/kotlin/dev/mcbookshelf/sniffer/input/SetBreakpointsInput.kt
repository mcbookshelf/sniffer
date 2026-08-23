package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Sets the breakpoints of a file, replacing the ones it already had.
 *
 * @property filePath filesystem path of the source file
 * @property lines zero indexed lines to put a breakpoint on
 * @author theogiraudet
 */
data class SetBreakpointsInput(val filePath: String?, val lines: List<Int>) : IInput
