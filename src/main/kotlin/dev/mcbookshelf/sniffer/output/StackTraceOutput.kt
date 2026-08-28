package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.domain.RealPath

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
 * @property functionName location of the function, as `namespace:path`
 * @property line zero indexed line inside that function
 * @property path where the function was loaded from, `null` if it could not be resolved
 * @author theogiraudet
 */
data class StackFrameData(
    val id: Int,
    val functionName: String,
    val line: Int,
    val path: RealPath?,
)
