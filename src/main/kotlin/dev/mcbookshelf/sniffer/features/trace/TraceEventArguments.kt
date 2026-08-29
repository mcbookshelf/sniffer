package dev.mcbookshelf.sniffer.features.trace

import org.eclipse.lsp4j.debug.Source

/**
 * @property traceId identifies the trace every following event belongs to
 * @property command the command being traced, as it was typed
 * @author theogiraudet
 */
data class TraceStartedArguments(
    val traceId: Int,
    val command: String,
)

/**
 * @property traceId the trace this call belongs to
 * @property function location of the function being entered, as `namespace:path`
 * @property source where that function can be opened, `null` when it could not be resolved
 * @property callerLine one indexed line of the call site, `null` when the call is a root of the trace
 * @property executor what the function runs as, as the game names it
 * @author theogiraudet
 */
data class TraceCallArguments(
    val traceId: Int,
    val function: String,
    val source: Source?,
    val callerLine: Int?,
    val executor: String,
)

/**
 * @property traceId the trace this return belongs to
 * @property line one indexed last line the returning function ran, `null` when it ran none
 * @author theogiraudet
 */
data class TraceReturnArguments(
    val traceId: Int,
    val line: Int?,
)

/**
 * @property traceId the trace that is over
 * @property reason one of [TraceEndReason]
 * @author theogiraudet
 */
data class TraceEndedArguments(
    val traceId: Int,
    val reason: String,
)

/**
 * Why a trace stopped, so a partial graph is never shown as a complete one.
 *
 * @author theogiraudet
 */
object TraceEndReason {
    /** The traced execution drained to its end. */
    const val COMPLETED = "completed"

    /** The debugger state was reset, or the editor went away, before the execution was over. */
    const val CANCELLED = "cancelled"
}
