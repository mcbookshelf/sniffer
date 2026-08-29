package dev.mcbookshelf.sniffer.features.trace

import dev.mcbookshelf.sniffer.dap.DapRemote
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification

/**
 * The call graph of a traced execution, streamed to the editor as it is walked.
 *
 * A trace is one [traceStarted], then [traceCall] and [traceReturn] pairing up like brackets,
 * then one [traceEnded].
 *
 * @author theogiraudet
 */
interface TraceClient : DapRemote {

    /** A trace has begun, and every event until [traceEnded] belongs to it. */
    @JsonNotification("snifferTraceStarted")
    fun traceStarted(args: TraceStartedArguments)

    /** A function has been entered, one level below the call currently open. */
    @JsonNotification("snifferTraceCall")
    fun traceCall(args: TraceCallArguments)

    /** The call currently open has returned, normally or through `/return`. */
    @JsonNotification("snifferTraceReturn")
    fun traceReturn(args: TraceReturnArguments)

    /** No further event belongs to this trace. */
    @JsonNotification("snifferTraceEnded")
    fun traceEnded(args: TraceEndedArguments)
}
