package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.state.RealPath
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of a paginated stack trace query.
 *
 * @property frames the requested slice of the call stack.
 * @property totalFrames total number of frames in the full stack.
 */
data class StackTraceOutput(
    val frames: List<StackFrameData>,
    val totalFrames: Int,
) : Output

/**
 * Domain representation of a single stack frame.
 *
 * @property id unique scope/frame ID.
 * @property functionName the Minecraft function path (e.g. "namespace:path").
 * @property line 0-indexed line number within the function.
 * @property path the resolved filesystem path and kind, or null if unresolved.
 */
data class StackFrameData(
    val id: Int,
    val functionName: String,
    val line: Int,
    val path: RealPath?,
)
