package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieves a slice of the debug call stack.
 *
 * @property startFrame zero indexed position of the first frame to return
 * @property maxLevels how many frames to return at most
 * @author theogiraudet
 */
data class GetStackTraceInput(val startFrame: Int, val maxLevels: Int) : IInput
