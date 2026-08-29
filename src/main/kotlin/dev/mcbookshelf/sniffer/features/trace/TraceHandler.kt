package dev.mcbookshelf.sniffer.features.trace

import dev.mcbookshelf.sniffer.dispatch.Ack
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.callstack.ControlFlowObserver
import dev.mcbookshelf.sniffer.features.callstack.DebugScope
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.features.source.SourceFactory
import dev.mcbookshelf.sniffer.features.stepping.PausedExecutionStore
import dev.mcbookshelf.sniffer.mixin.CommandsAccessor
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.commands.execution.ExecutionContext

/**
 * Streams the calls and returns an execution goes through to the editor,
 * between the opening and the closing of a trace.
 *
 * @param client the editor to stream to, looked up on every event since a reconnection replaces the proxy
 * @author theogiraudet
 */
class TraceHandler(private val client: () -> TraceClient?) : Handler<TraceInput>, ControlFlowObserver {

    override val inputType = TraceInput::class

    /**
     * The execution being traced, which is what says the trace is over when it closes.
     * Several executions run in a tick, and a trace held on a breakpoint outlives the ones that ran meanwhile.
     */
    private var tracedContext: ExecutionContext<*>? = null

    override fun handle(input: TraceInput, ctx: Context): Output {
        if (!input.start) {
            terminate()
            return Ack
        }
        val client = client() ?: return NoClientAttached
        if (TraceState.currentTraceId != null) return AlreadyTracing

        val traceId = TraceState.open()
        tracedContext = CommandsAccessor.getCurrentExecutionContext().get()
        ScopeManager.get().observe(this)
        client.traceStarted(TraceStartedArguments(traceId, input.command.orEmpty()))
        return Ack
    }

    override fun onNewScope(scope: DebugScope) {
        val traceId = TraceState.currentTraceId ?: return
        client()?.traceCall(
            TraceCallArguments(
                traceId,
                scope.identity.minecraftPath,
                SourceFactory.toSource(scope.identity),
                scope.callerLine?.inEditor,
                scope.executor.name(),
            )
        )
    }

    override fun onUnscope(scope: DebugScope) {
        val traceId = TraceState.currentTraceId ?: return
        client()?.traceReturn(TraceReturnArguments(traceId, scope.line?.inEditor))
    }

    /** The debugger state is thrown away, so the trace goes with it whatever it was waiting for. */
    override fun onClear() {
        close(TraceEndReason.CANCELLED)
    }

    override fun onExecutionComplete(context: ExecutionContext<*>) {
        if (context === tracedContext) close(TraceEndReason.COMPLETED)
    }

    /**
     * Ends the trace, unless a breakpoint is still holding the execution, in which case it is not over yet
     * and the closing is left to [onExecutionComplete].
     */
    private fun terminate() {
        if (!PausedExecutionStore.isPaused()) close(TraceEndReason.COMPLETED)
    }

    private fun close(reason: String) {
        val traceId = TraceState.close() ?: return
        ScopeManager.get().unobserve(this)
        tracedContext = null
        client()?.traceEnded(TraceEndedArguments(traceId, reason))
    }

    private fun ExecutionCommandSource<*>.name(): String =
        (this as? CommandSourceStack)?.textName ?: toString()
}
