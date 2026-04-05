package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Set breakpoints for a file, replacing any previously set breakpoints for that file.
 *
 * @property filePath the filesystem path of the source file.
 * @property lines 0-indexed line numbers where breakpoints should be placed.
 */
data class SetBreakpointsInput(val filePath: String?, val lines: List<Int>) : IInput
