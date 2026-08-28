package dev.mcbookshelf.sniffer.features.callstack

import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.source.FunctionIdentity

/**
 * Result of a paginated stack trace query.
 *
 * @property frames the requested slice of the call stack
 * @property totalFrames how many frames the whole stack holds
 * @author theogiraudet
 */
data class StackTraceOutput(
    val frames: List<StackFrameData>,
    val totalFrames: Int,
) : Output

/**
 * A single frame of the call stack.
 *
 * @property id id of the scope the frame stands for
 * @property identity the function this frame runs, located as well as named
 * @property line zero indexed line inside that function
 * @author theogiraudet
 */
data class StackFrameData(
    val id: Int,
    val identity: FunctionIdentity,
    val line: Int,
)
