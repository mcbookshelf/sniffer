package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.dap.ConnectionState
import dev.mcbookshelf.sniffer.features.trace.TraceEndReason
import dev.mcbookshelf.sniffer.gametest.support.*
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer

/**
 * `/trace`, whose graph only ever reaches an attached editor.
 *
 * A trace spans an execution rather than a tick, so what ends it is not always the entry queued behind the traced
 * command: a client that goes away mid execution takes the execution with it, and the trace has to go too.
 */
class TraceIntegrationGameTest : AbstractDapIntegrationGameTest() {

    @GameTest(environment = "sniffer_test:trace_graph", maxTicks = MAX_TICKS)
    fun theGraphOfATracedExecutionReachesTheEditorAsItIsWalked(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("trace run function $OUTER") }
            .thenAwaitEvent("traceEnded", events.traceEnded) { ended ->
                assertEquals(ended.reason, TraceEndReason.COMPLETED, "end reason")
            }
            .thenExecute {
                val started = events.traceStarted.poll()
                assertTrue(started != null, "No trace was opened")
                assertEquals(started!!.command, "function $OUTER", "traced command")

                // The graph is walked outside in, so the callee is entered from the line of the caller it is on.
                val calls = events.traceCall.toList()
                assertEquals(calls.map { it.function }, listOf(OUTER, INNER), "called functions")
                assertTrue(calls[0].callerLine == null, "The root of a trace is called from nowhere")
                assertEquals(calls[1].callerLine, 2, "line outer calls inner from")
                assertTrue(calls.all { it.traceId == started.traceId }, "Every call belongs to the open trace")
                assertTrue(calls[0].source?.name == OUTER, "The call names a source the editor can open")

                // One return per call, innermost first, each on the last line its function ran.
                val returns = events.traceReturn.toList()
                assertEquals(returns.map { it.line }, listOf(2, 3), "returned lines")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:trace_return", maxTicks = MAX_TICKS)
    fun atraceEndsEvenWhenTheExecutionReturnsOutOfIt(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenAwaitDapReady(dap)
            // `/return` at the top level discards the queue the closing entry rides in, so what ends the
            // trace here is not the entry the modifier queued behind the command.
            .thenExecute { session.run("trace run return run function $EARLY_RETURN") }
            .thenAwaitEvent("traceEnded", events.traceEnded) { ended ->
                assertEquals(ended.reason, TraceEndReason.COMPLETED, "end reason")
            }
            .thenExecute {
                events.traceStarted.clear()
                session.clearLog()
                // The trace has to be closed, or this one is refused as a second.
                session.run("trace run function $LINEAR")
            }
            .thenAwaitEvent("traceStarted", events.traceStarted)
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b", "c") }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:trace_no_function", maxTicks = MAX_TICKS)
    fun acommandThatCannotEnterAFunctionIsRefused(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute {
                // Nothing this command does can push a scope, so there would be no graph to draw.
                session.run("trace run data modify storage $LOG refused set value 1")
                assertEquals(events.traceStarted.size, 0, "traces opened")
                // Refused before anything is queued, so the command it wrapped never ran either.
                assertThat(session).hasExecuted()

                // Reached through execute rather than at the head of the command, which still counts.
                session.run("trace run execute positioned 0 0 0 run function $LINEAR")
            }
            .thenAwaitEvent("traceEnded", events.traceEnded)
            .thenExecute {
                assertEquals(events.traceStarted.size, 1, "traces opened")
                assertThat(session).hasExecuted("a", "b", "c")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:trace_refused", maxTicks = MAX_TICKS)
    fun aSecondTraceIsRefusedWhileOneIsStillOpen(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenAwaitDapReady(dap)
            // Halts inside the traced function, so the first trace is still open when the second is asked for.
            .thenExecute { session.run("trace run function $TRIGGERS") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("evaluate", { dap.evaluate(evaluateOf("/trace run function $LINEAR", REPL)) }) { response ->
                assertTrue(response.result.isNotEmpty(), "The console should be told why the trace was refused")
            }
            .thenExecute {
                assertEquals(events.traceStarted.size, 1, "traces opened")
                // A refused trace never queues the command it was given.
                assertThat(session).hasExecuted("before_trigger")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:trace_disconnect", maxTicks = MAX_TICKS)
    fun aTraceLeftOpenByADisconnectDoesNotRefuseTheNextOne(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        var reconnected: IDebugProtocolServer? = null

        helper.startSequence()
            .thenAwaitDapReady(dap)
            // The traced function halts on its own, so the trace is still open when the client goes away,
            // and the execution carrying the entry that would close it is dropped rather than replayed.
            .thenExecute { session.run("trace run function $TRIGGERS") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenExecute { closeDapClient() }
            .thenWaitUntil { assertFalse(ConnectionState.isConnected(), "The session should be gone") }
            .thenExecute {
                reconnected = initDapClient()
                session.clearLog()
            }
            // A response proves the mod finished wiring this connection, so a trace has a client to stream to.
            .thenRequest("initialize", { reconnected!!.initialize(InitializeRequestArguments()) })
            .thenExecute { session.run("trace run function $LINEAR") }
            // A refused trace never queues the command it was given, so the markers are what says it was accepted.
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b", "c") }
            .thenSucceedAndClose()
    }

    companion object {
        private const val TRIGGERS = "sniffer_test:triggers_breakpoint"
        private const val LINEAR = "sniffer_test:linear"
        private const val OUTER = "sniffer_test:outer"
        private const val INNER = "sniffer_test:inner"
        private const val LOG = "sniffer_test:log"
        private const val EARLY_RETURN = "sniffer_test:early_return"

        const val MAX_TICKS = 100_000
    }
}
