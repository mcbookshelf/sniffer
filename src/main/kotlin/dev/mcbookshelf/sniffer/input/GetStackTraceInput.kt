package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieve the debug call stack with pagination.
 *
 * @property startFrame index of the first frame to return (0-based).
 * @property maxLevels maximum number of frames to return.
 */
data class GetStackTraceInput(val startFrame: Int, val maxLevels: Int) : IInput
