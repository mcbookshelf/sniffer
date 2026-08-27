package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertEquals
import dev.mcbookshelf.sniffer.gametest.support.fail
import dev.mcbookshelf.sniffer.gametest.support.thenExpand
import dev.mcbookshelf.sniffer.gametest.support.thenPausedVariables
import dev.mcbookshelf.sniffer.gametest.support.variablesOf
import dev.mcbookshelf.sniffer.gametest.support.assertFalse
import dev.mcbookshelf.sniffer.gametest.support.assertThat
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import dev.mcbookshelf.sniffer.gametest.support.thenAwaitDapReady
import dev.mcbookshelf.sniffer.gametest.support.thenAwaitEvent
import dev.mcbookshelf.sniffer.gametest.support.thenRequest
import dev.mcbookshelf.sniffer.state.FunctionPathRegistry
import java.nio.file.Files
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestSequence
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.storage.LevelResource
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.ContinueArguments
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.NextArguments
import org.eclipse.lsp4j.debug.PauseArguments
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceArguments
import org.eclipse.lsp4j.debug.SourceBreakpoint
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.StepInArguments
import org.eclipse.lsp4j.debug.StepOutArguments
import org.slf4j.LoggerFactory

/**
 * The Debug Adapter Protocol as an editor speaks it to the mod, over a real WebSocket.
 *
 * An attached client sets breakpoints by file path and line, is told which of them verified, and is pushed a `stopped` event when one is hit.
 * From there it reads the pause site as a tree, frames to scopes to variables, evaluates expressions against it, asks for the text of any function it cannot open itself, and drives execution on with a step or a continue.
 * The adapter answers on the server thread, so every one of those requests lands on execution the mixin layer really suspended rather than on a snapshot of it.
 */
class DapProtocolIntegrationGameTest : AbstractDapIntegrationGameTest() {

    // ── Session lifecycle ───────────────────────────────────────────

    @GameTest(environment = "sniffer_test:dap_handshake", maxTicks = MAX_TICKS)
    fun theHandshakeAnnouncesCapabilitiesAndSignalsInitialized(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenRequest("initialize", { dap.initialize(InitializeRequestArguments()) }) { capabilities ->
                assertTrue(
                    capabilities.supportsConfigurationDoneRequest,
                    "The adapter should announce support for configurationDone",
                )
            }
            .thenAwaitEvent("initialized", events.initialized)
            .thenRequest("attach", { dap.attach(emptyMap()) })
            .thenRequest("configurationDone", { dap.configurationDone(ConfigurationDoneArguments()) })
            .thenSucceedAndClose()
    }

    /**
     * Attaching mirrors the game log to the client, which shows it in its debug console.
     *
     * The line is written through the logging backend the game itself writes to rather than through the mod,
     * since what has to be proven is that any line reaches the client, not just the ones Sniffer produces.
     * It travels on a thread of the forwarder's own, so the wait is for it to turn up rather than for it to have already turned up.
     */
    @GameTest(environment = "sniffer_test:dap_log_forwarding", maxTicks = MAX_TICKS)
    fun theGameLogIsMirroredToAnAttachedClient(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()
        val marker = "log forwarding marker ${System.nanoTime()}"

        helper.startSequence()
            .thenRequest("initialize", { dap.initialize(InitializeRequestArguments()) })
            .thenRequest("attach", { dap.attach(emptyMap()) })
            .thenExecute { LoggerFactory.getLogger("sniffer_test").info(marker) }
            .thenWaitUntil {
                assertTrue(
                    events.output.any { marker in (it.output ?: "") },
                    "The logged line has not reached the client yet",
                )
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_threads", maxTicks = MAX_TICKS)
    fun threadsReportsTheSingleThreadFunctionsRunOn(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // Functions only ever run on the server thread, so the client is offered exactly one thread to stop and step in.
            .thenRequest("threads", { dap.threads() }) { response ->
                assertEquals(response.threads.size, 1, "thread count")
                assertEquals(response.threads[0].name, "Main Thread", "thread name")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_disconnect_running", maxTicks = MAX_TICKS)
    fun disconnectingWithNothingPausedResumesNothing(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // Disconnecting releases a suspended function, so a client leaving while nothing is suspended must not announce a resume that never happened.
            .thenRequest("disconnect", { dap.disconnect(DisconnectArguments()) })
            .thenExecute {
                assertTrue(events.continued.isEmpty(), "Nothing was paused, so nothing continued")
                assertThat(session).isNotPaused()
            }
            .thenSucceedAndClose()
    }

    // ── Breakpoints ─────────────────────────────────────────────────

    @GameTest(environment = "sniffer_test:dap_breakpoints", maxTicks = MAX_TICKS)
    fun breakpointsSetOverTheWireAreVerifiedAndHaltExecution(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        var breakpointId = -1

        helper.startSequence()
            // DAP lines are 1 based, so line 2 is the second line of the file.
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(session.filePath(LINEAR), 2)) }) { response ->
                assertEquals(response.breakpoints.size, 1, "breakpoint count")
                val breakpoint = response.breakpoints[0]
                assertTrue(breakpoint.isVerified, "The breakpoint should be verified")
                assertEquals(breakpoint.line, 2, "breakpoint line")
                assertTrue(breakpoint.id != null, "A verified breakpoint should carry an id")
                breakpointId = breakpoint.id
            }
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped) { event ->
                assertEquals(event.reason, "breakpoint", "stop reason")
                assertEquals(event.threadId, 1, "stopped thread")
                assertEquals(event.hitBreakpointIds.toList(), listOf(breakpointId), "hit breakpoints")
            }
            .thenExecute {
                assertThat(session).isPaused("Execution should be paused")
                // Paused before the breakpointed line, so only the one above it ran.
                assertThat(session).hasExecuted("a")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_unverified_breakpoint", maxTicks = MAX_TICKS)
    fun aBreakpointOutsideADatapackIsReportedUnverified(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // A path that is not a datapack function cannot be resolved to one, and the client is told so rather than left believing the breakpoint is live.
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt("nowhere.txt", 1)) }) { response ->
                assertEquals(response.breakpoints.size, 1, "breakpoint count")
                val breakpoint = response.breakpoints[0]
                assertFalse(breakpoint.isVerified, "A breakpoint outside a datapack cannot verify")
                assertTrue(breakpoint.id == null, "An unverified breakpoint should carry no id")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_conditional_breakpoint", maxTicks = MAX_TICKS)
    fun aConditionalBreakpointOnlyHaltsWhenItsCommandSucceeds(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        // The test world outlives the run, so the flag the condition reads has to start from a known state.
        session.run("data remove storage $CONDITION_STORAGE flag")

        helper.startSequence()
            // The condition is a command, read on its success channel: it halts on success and lets the line through on failure.
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(session.filePath(LINEAR), 2, condition = CONDITION)) }) { response ->
                assertTrue(response.breakpoints.single().isVerified, "A breakpoint with a valid condition should verify")
            }
            .thenExecute { session.run("function $LINEAR") }
            .thenWaitUntil { assertThat(session).hasExecuted(LINEAR_A, LINEAR_B, LINEAR_C) }
            .thenExecute {
                assertTrue(events.stopped.isEmpty(), "The flag is unset, so the condition fails and the function runs through")
                assertThat(session).isNotPaused()
                session.clearLog()
                // Flipping the flag is all that changes, so the halt below can only come from the condition now succeeding.
                session.run("data modify storage $CONDITION_STORAGE flag set value 1b")
            }
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenExecute { assertThat(session).hasExecuted(LINEAR_A) }
            .thenRequest("continue", { dap.continue_(ContinueArguments()) })
            .thenWaitUntil { assertThat(session).hasExecuted(LINEAR_A, LINEAR_B, LINEAR_C) }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_condition_calls_function", maxTicks = MAX_TICKS)
    fun breakpointsInsideAFunctionAConditionCallsAreNotHit(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // A live breakpoint on the first line of the function the condition below is about to call.
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(session.filePath(PROBE), 1)) }) { response ->
                assertTrue(response.breakpoints.single().isVerified, "The breakpoint in the called function should verify")
            }
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(session.filePath(LINEAR), 2, condition = PROBE_CONDITION)) }) { response ->
                assertTrue(response.breakpoints.single().isVerified, "The conditional breakpoint should verify")
            }
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenExecute {
                // The condition ran the whole function rather than halting on its way through it.
                assertTrue(session.stored(PROBE_MARKER) != null, "The condition should have run the function it calls")
                // A condition is not part of the debugged execution, so the breakpoint it runs over must not fire:
                // the halt just awaited is the conditional one, and the call stack says it is not the one in the called function.
                assertTrue(events.stopped.isEmpty(), "Only the conditional breakpoint should have halted")
                assertThat(session).hasCallStack(LINEAR)
            }
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                assertEquals(response.stackFrames.map { it.name }, listOf(LINEAR), "frame names")
                assertEquals(response.stackFrames[0].line, 2, "the line execution halted on")
            }
            .thenRequest("continue", { dap.continue_(ContinueArguments()) })
            .thenWaitUntil { assertThat(session).hasExecuted(LINEAR_A, LINEAR_B, LINEAR_C, PROBE_MARKER) }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_invalid_condition", maxTicks = MAX_TICKS)
    fun aBreakpointWhoseConditionIsNotACommandIsReportedUnverified(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // Conditions are validated when they are set, so a typo comes back as an unverified breakpoint rather than as a surprise at runtime.
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(session.filePath(LINEAR), 2, condition = "not_a_command")) }) { response ->
                val breakpoint = response.breakpoints.single()
                assertFalse(breakpoint.isVerified, "A breakpoint with an unparseable condition cannot verify")
                assertTrue(breakpoint.message != null, "The client should be told why the breakpoint did not verify")
            }
            .thenExecute { session.run("function $LINEAR") }
            .thenWaitUntil { assertThat(session).hasExecuted(LINEAR_A, LINEAR_B, LINEAR_C) }
            // The breakpoint was never registered, so nothing halts.
            .thenExecute { assertTrue(events.stopped.isEmpty(), "An unverified breakpoint must not halt execution") }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_breakpoint_replace", maxTicks = MAX_TICKS)
    fun resendingAFilesBreakpointsReplacesTheOnesItHeld(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        // A function in a subdirectory, so the path the editor sends has to be resolved back through the whole `data/<namespace>/function/` tail.
        val nested = session.filePath(NESTED)

        helper.startSequence()
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(nested, 2)) }) { response ->
                assertTrue(response.breakpoints.single().isVerified, "A breakpoint in a nested function should verify")
            }
            .thenExecute { session.run("function $NESTED") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenExecute { assertThat(session).hasExecuted(NESTED_FIRST) }
            .thenRequest("continue", { dap.continue_(ContinueArguments()) })
            .thenWaitUntil { assertThat(session).hasExecuted(NESTED_FIRST, NESTED_SECOND, NESTED_THIRD) }
            .thenExecute {
                // Resuming re-runs the line it was parked on, which must not count as hitting the breakpoint again.
                assertTrue(events.stopped.isEmpty(), "Resuming should not halt again on the same line")
                session.clearLog()
            }
            // The editor moves the breakpoint down a line and re-sends what the file now holds, which is the only way it ever says one was removed.
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(nested, 3)) }) { response ->
                assertTrue(response.breakpoints.single().isVerified, "The moved breakpoint should verify")
            }
            .thenExecute { session.run("function $NESTED") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenExecute {
                // The line the old breakpoint sat on ran this time, so it really is gone rather than merely shadowed.
                assertThat(session).hasExecuted(NESTED_FIRST, NESTED_SECOND)
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_breakpoint_clear", maxTicks = MAX_TICKS)
    fun clearingAFilesBreakpointsLeavesTheOtherFilesAlone(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        val nested = session.filePath(NESTED)

        helper.startSequence()
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(nested, 2)) })
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(session.filePath(LINEAR), 2)) })
            // The user removed the last breakpoint of the nested file, which reaches the adapter as a request carrying none.
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(nested)) }) { response ->
                assertTrue(
                    response.breakpoints == null || response.breakpoints.isEmpty(),
                    "An empty request should leave the file with no breakpoints",
                )
            }
            .thenExecute { session.run("function $NESTED") }
            .thenWaitUntil { assertThat(session).hasExecuted(NESTED_FIRST, NESTED_SECOND, NESTED_THIRD) }
            .thenExecute {
                assertThat(session).isNotPaused("The cleared function should run straight through")
                assertTrue(events.stopped.isEmpty(), "No breakpoint is left in that file to stop on")
                // The request named one file, so the breakpoints of every other file have to have survived it.
                session.run("function $LINEAR")
            }
            .thenAwaitEvent("stopped", events.stopped) { event ->
                assertEquals(event.reason, "breakpoint", "stop reason")
            }
            .thenExecute {
                assertThat(session).isPaused("The other file's breakpoint should still halt execution")
            }
            .thenSucceedAndClose()
    }

    // ── Inspection while paused ─────────────────────────────────────

    @GameTest(environment = "sniffer_test:dap_inspection", maxTicks = MAX_TICKS)
    fun stackTraceScopesAndVariablesDescribeTheRealPauseSite(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(INNER, line = 0)
        // Spelt out rather than walked with `thenPausedVariables`, since the walk itself is what this test is about.
        var frameId = -1
        var scopeReference = 0

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $OUTER") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                assertEquals(response.totalFrames, 2, "frame count")
                val frames = response.stackFrames
                assertEquals(frames.map { it.name }, listOf(INNER, OUTER), "frame names")
                // The innermost frame sits on the first line of the callee and its caller on the second line of the caller, both reported 1 based.
                assertEquals(frames.map { it.line }, listOf(1, 2), "frame lines")
                assertEquals(frames[0].source.name, INNER, "innermost source name")
                frameId = frames[0].id
            }
            .thenRequest("scopes", { dap.scopes(ScopesArguments().apply { this.frameId = frameId }) }) { response ->
                assertEquals(response.scopes.size, 1, "scope count")
                val scope = response.scopes[0]
                assertEquals(scope.name, "Function", "scope name")
                assertTrue(scope.variablesReference != 0, "The scope should be expandable")
                scopeReference = scope.variablesReference
            }
            .thenExpand(dap, { scopeReference }) { variables ->
                // No macro arguments here, so the scope holds the two source variables.
                assertEquals(variables.map { it.name }, listOf("executor", "location"), "variable names")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_macro_variables", maxTicks = MAX_TICKS)
    fun macroArgumentsAreReachableThroughTheVariablesTree(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(MACRO, line = 1)
        var macroReference = 0
        var nestedReference = 0

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $MACRO {who:\"steve\",nested:{count:3}}") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenExecute {
                assertThat(session).hasExecuted("macro_entered")
            }
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                assertEquals(response.stackFrames[0].line, 2, "the line the macro halted on")
            }
            .thenPausedVariables(dap) { variables ->
                val macro = variables.firstOrNull { it.name == "macro" }
                assertTrue(macro != null, "The paused macro should expose its arguments")
                assertTrue(macro!!.variablesReference != 0, "Macro arguments should be expandable")
                macroReference = macro.variablesReference
            }
            .thenExpand(dap, { macroReference }) { variables ->
                val who = variables.firstOrNull { it.name == "who" }
                assertTrue(who != null, "The 'who' macro argument should be listed")
                assertTrue(
                    who!!.value.contains("steve"),
                    "The 'who' argument should hold the value the function was called with",
                )
                // Arguments are NBT, so a compound among them is a subtree rather than a printed value.
                val nested = variables.firstOrNull { it.name == "nested" }
                assertTrue(nested != null, "A compound argument should be listed")
                assertTrue(nested!!.variablesReference != 0, "A compound argument should be expandable")
                nestedReference = nested.variablesReference
            }
            .thenExpand(dap, { nestedReference }) { variables ->
                assertEquals(variables.map { it.name }, listOf("count"), "what the compound holds")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_entity_executor", maxTicks = MAX_TICKS)
    fun anEntityExecutorDescribesTheEntityTheFunctionRunsAs(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        val entity = markerNamed(helper, "Steve")
        session.breakpointAt(LINEAR, line = 1)
        var executorReference = 0
        var nbtReference = 0

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("execute as ${entity.stringUUID} run function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenPausedVariables(dap) { variables ->
                val executor = variables.first { it.name == "executor" }
                // Run from the console the executor is the plain word "server", so a name here is what says the entity was picked up at all.
                assertEquals(executor.value, "Steve", "executor name")
                assertTrue(executor.variablesReference != 0, "An entity executor should be expandable")
                executorReference = executor.variablesReference
            }
            .thenExpand(dap, { executorReference }) { variables ->
                assertEquals(
                    variables.map { it.name },
                    listOf("type", "name", "uuid", "position", "rotation", "world", "nbt"),
                    "executor children",
                )
                val byName = variables.associateBy { it.name }
                assertEquals(byName["type"]!!.value, "marker", "entity type")
                assertEquals(byName["name"]!!.value, "Steve", "entity name")
                assertEquals(byName["uuid"]!!.value, entity.stringUUID, "entity uuid")
                assertEquals(byName["world"]!!.value, "overworld", "entity world")
                nbtReference = byName["nbt"]!!.variablesReference
                assertTrue(nbtReference != 0, "An entity's nbt should be expandable")
            }
            .thenExpand(dap, { nbtReference }) { variables ->
                // Which keys an entity saves is the game's business and moves between versions,
                // so what is checked is that the subtree really is its saved data.
                assertTrue(variables.isNotEmpty(), "An entity's nbt should hold its saved data")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_entity_position", maxTicks = MAX_TICKS)
    fun anEntityExecutorReportsItsOwnPositionRatherThanTheCommandSources(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        val entity = markerNamed(helper, "Steve")
        session.breakpointAt(LINEAR, line = 1)
        var executorReference = 0
        var locationReference = 0
        var executorPosition = 0
        var locationPosition = 0
        var entityCoordinates = emptyList<String>()

        helper.startSequence()
            .thenAwaitDapReady(dap)
            // `as` and not `at`: the entity becomes the executor while the position the command runs from stays the source's,
            // which is what keeps the two nodes distinguishable.
            .thenExecute { session.run("execute as ${entity.stringUUID} run function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenPausedVariables(dap) { variables ->
                executorReference = variables.first { it.name == "executor" }.variablesReference
                locationReference = variables.first { it.name == "location" }.variablesReference
            }
            .thenExpand(dap, { executorReference }) { variables ->
                executorPosition = variables.first { it.name == "position" }.variablesReference
            }
            .thenExpand(dap, { locationReference }) { variables ->
                locationPosition = variables.first { it.name == "position" }.variablesReference
            }
            .thenExpand(dap, { executorPosition }) { variables ->
                val position = entity.position()
                entityCoordinates = variables.map { it.value }
                assertEquals(variables.map { it.name }, listOf("x", "y", "z"), "coordinate names")
                assertEquals(
                    entityCoordinates,
                    listOf(position.x.toString(), position.y.toString(), position.z.toString()),
                    "executor coordinates",
                )
            }
            .thenExpand(dap, { locationPosition }) { variables ->
                // Named first, so the comparison below cannot pass by comparing against nothing.
                assertEquals(variables.map { it.name }, listOf("x", "y", "z"), "location coordinate names")
                assertTrue(
                    variables.map { it.value } != entityCoordinates,
                    "The location should stay where the command ran from rather than follow the executor",
                )
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_zipped_breakpoint", maxTicks = MAX_TICKS)
    fun aBreakpointInsideAZippedDatapackVerifiesAndHalts(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        ensureZippedPack(session.server)

        helper.startSequence()
            .thenLoadZippedPack(session)
            // The path the adapter reports for a zipped function is composite (the archive, then the entry inside it).
            // Sending it straight back is what an editor does, and it must still resolve to a function.
            .thenRequest("setBreakpoints", { dap.setBreakpoints(breakpointsAt(zippedPath(), 2)) }) { response ->
                assertTrue(response.breakpoints[0].isVerified, "A breakpoint inside a zipped pack should verify")
            }
            .thenExecute { session.run("function $ZIPPED") }
            .thenAwaitEvent("stopped", events.stopped) { event ->
                assertEquals(event.reason, "breakpoint", "stop reason")
            }
            .thenExecute {
                assertThat(session).isPaused("Execution should be paused")
                // Paused before the second line, so only the first one ran.
                assertThat(session).hasExecuted(ZIP_FIRST)
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_zipped_source", maxTicks = MAX_TICKS)
    fun aFunctionInsideAZippedDatapackIsServedBySourceReference(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        ensureZippedPack(session.server)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenLoadZippedPack(session)
            // The pause is only a vantage point here: a source reference is reported on the frames of a stack trace, so there has to be a stack.
            .thenExecute {
                session.breakpointAtFile(zippedPath(), 1)
                session.run("function $ZIPPED")
            }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                val source = response.stackFrames[0].source
                assertEquals(source.name, ZIPPED, "source name")
                // Nothing on disk for the editor to open, so the adapter hands out a reference and undertakes to serve the text itself.
                assertTrue(
                    source.sourceReference != null && source.sourceReference != 0,
                    "A zipped function should be given a source reference",
                )
            }
            .thenRequest("source", { dap.source(sourceOf(ZIPPED)) }) { response ->
                assertTrue(
                    response.content.contains(ZIP_FIRST) && response.content.contains(ZIP_SECOND),
                    "The adapter should serve the text held in the archive",
                )
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_evaluate", maxTicks = MAX_TICKS)
    fun evaluateResolvesAnExpressionAgainstThePausedScope(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(LINEAR, line = 1)
        var compoundReference = 0

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute {
                // Staged before the pause, so what the expression reads is ordinary game state rather than something written while suspended.
                session.run("data modify storage sniffer_test:log nested set value {x:1,y:2}")
                session.run("function $LINEAR")
            }
            .thenAwaitEvent("stopped", events.stopped)
            // `evaluate` takes a single operand of the expression mini language (`data`, `score` or `name`)
            .thenRequest("evaluate", { dap.evaluate(evaluateOf("data storage sniffer_test:log a")) }) { response ->
                // The breakpointed function wrote this on the line above the pause.
                assertEquals(response.result, "1", "evaluated value")
                assertEquals(response.variablesReference, 0, "A scalar result is not expandable")
            }
            .thenRequest("evaluate", { dap.evaluate(evaluateOf("data storage sniffer_test:log nested")) }) { response ->
                assertTrue(response.result.contains("x:1"), "The compound should be rendered in the result")
                assertTrue(response.variablesReference != 0, "A compound result should be expandable")
                compoundReference = response.variablesReference
            }
            // The subtree an evaluation registers is reachable through the same `variables` request as any scope variable.
            .thenExpand(dap, { compoundReference }) { variables ->
                assertEquals(variables.map { it.name }.sorted(), listOf("x", "y"), "variable names")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_source", maxTicks = MAX_TICKS)
    fun theSourceRequestReturnsTheFunctionText(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenRequest("source", { dap.source(sourceOf(LINEAR)) }) { response ->
                assertEquals(response.content.trim().lines().size, 3, "source line count")
                assertTrue(
                    response.content.contains("sniffer_test:log a set value 1"),
                    "The source should be the text of the function itself",
                )
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_unknown_source", maxTicks = MAX_TICKS)
    fun aFunctionThatIsNotLoadedHasNoSource(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // A stale stack frame can name a function that no longer exists, and the client is handed an empty file rather than an error.
            .thenRequest("source", { dap.source(sourceOf("sniffer_test:ghost")) }) { response ->
                assertEquals(response.content, "", "source of an unknown function")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_malformed_source", maxTicks = MAX_TICKS)
    fun aMalformedFunctionIdHasNoSource(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // Not even a well-formed identifier, so it is answered the same way rather than throwing out of the adapter.
            .thenRequest("source", { dap.source(sourceOf("NOT AN ID")) }) { response ->
                assertEquals(response.content, "", "source of a malformed id")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_evaluate_no_scope", maxTicks = MAX_TICKS)
    fun evaluatingWithNothingPausedReportsTheMissingScope(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // An expression is resolved against the executor of the paused scope, so with nothing paused there is nothing to resolve it against.
            .thenRequest("evaluate", { dap.evaluate(evaluateOf("name @s")) }) { response ->
                assertEquals(response.result, "Scope is null", "evaluated value")
                assertEquals(response.variablesReference, 0, "An unevaluated expression is not expandable")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_evaluate_unknown", maxTicks = MAX_TICKS)
    fun anExpressionMatchingNoKnownFormEvaluatesToNothing(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(LINEAR, line = 1)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            // Whatever an editor's watch box holds is sent as typed, so a word matching none of the expression forms has to come back as a value rather than as a failed request.
            .thenRequest("evaluate", { dap.evaluate(evaluateOf("bogus")) }) { response ->
                assertEquals(response.result, "", "evaluated value")
                assertEquals(response.variablesReference, 0, "An empty result is not expandable")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_evaluate_error", maxTicks = MAX_TICKS)
    fun anExpressionThatFailsAgainstTheGameReportsTheErrorInsteadOfThrowing(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(LINEAR, line = 1)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            // The storage is real but holds no such path, and what the game throws over it belongs in the watch box, not on the wire as a request failure.
            .thenRequest("evaluate", { dap.evaluate(evaluateOf("data storage sniffer_test:log missing")) }) { response ->
                assertTrue(response.result.isNotEmpty(), "The failure should be reported as the value")
                assertEquals(response.variablesReference, 0, "A failed evaluation is not expandable")
            }
            .thenSucceedAndClose()
    }

    // ── Execution control ───────────────────────────────────────────

    @GameTest(environment = "sniffer_test:dap_step_in", maxTicks = MAX_TICKS)
    fun steppingInOverTheWireEntersTheCallee(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        // The call line, so that stepping in enters the callee.
        session.breakpointAt(OUTER, line = 1)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $OUTER") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("stepIn", { dap.stepIn(StepInArguments()) })
            .thenAwaitEvent("stopped", events.stopped) { event ->
                // A step lands where no breakpoint sits, so none is reported hit.
                assertTrue(event.hitBreakpointIds == null, "A step should not report a breakpoint hit")
                assertEquals(event.reason, "step", "The reason of the stop should be 'step'")
            }
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                assertEquals(response.stackFrames.map { it.name }, listOf(INNER, OUTER), "frame names")
                // Paused before the first line of the callee, so it has not run yet.
                assertThat(session).hasExecuted("outer_before")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_step_over", maxTicks = MAX_TICKS)
    fun steppingOverACallOverTheWireRunsItWithoutEnteringIt(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        // The call line, so the step has a callee to skip over.
        session.breakpointAt(OUTER, line = 1)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $OUTER") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("next", { dap.next(NextArguments()) })
            .thenAwaitEvent("stopped", events.stopped) { event ->
                assertEquals(event.reason, "step", "stop reason")
            }
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                // Back in the caller on the line below the call, never inside the callee.
                assertEquals(response.stackFrames.map { it.name }, listOf(OUTER), "frame names")
                assertEquals(response.stackFrames.map { it.line }, listOf(3), "frame lines")
                // The callee still ran in full, it was only never stopped in.
                assertThat(session).hasExecuted("outer_before", "inner_first", "inner_second")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_step_out", maxTicks = MAX_TICKS)
    fun steppingOutOverTheWireFinishesTheCalleeAndReturnsToTheCaller(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(INNER, line = 0)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $OUTER") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("stepOut", { dap.stepOut(StepOutArguments()) })
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                assertEquals(response.stackFrames.map { it.name }, listOf(OUTER), "frame names")
                assertThat(session).hasExecuted("outer_before", "inner_first", "inner_second")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_continue", maxTicks = MAX_TICKS)
    fun continuingOverTheWireEmitsContinuedAndFinishesTheFunction(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(LINEAR, line = 1)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("continue", { dap.continue_(ContinueArguments()) })
            .thenAwaitEvent("continued", events.continued) { event ->
                assertEquals(event.threadId, 1, "continued thread")
            }
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b", "c") }
            .thenExecute {
                assertThat(session).isNotPaused("Execution should have resumed")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_disconnect", maxTicks = MAX_TICKS)
    fun disconnectingWhilePausedReleasesTheSuspendedFunction(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(LINEAR, line = 1)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            // A client that goes away must not leave the function suspended forever.
            .thenRequest("disconnect", { dap.disconnect(DisconnectArguments()) })
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b", "c") }
            .thenExecute {
                assertThat(session).isNotPaused("Execution should have resumed")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_step_refused", maxTicks = MAX_TICKS)
    fun steppingWithNothingPausedIsRefusedAndLeavesTheStateUntouched(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // A step only means anything from a pause site, and a refused one must not arm a step that fires on whatever runs next.
            .thenRequest("stepIn", { dap.stepIn(StepInArguments()) })
            .thenExecute {
                assertThat(session).hasNoPendingStep()
                assertThat(session).isNotPaused()
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_continue_refused", maxTicks = MAX_TICKS)
    fun continuingWithNothingPausedIsRefusedAndEmitsNoEvent(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenRequest("continue", { dap.continue_(ContinueArguments()) })
            .thenExecute {
                assertTrue(events.continued.isEmpty(), "Nothing was paused, so nothing continued")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_pause", maxTicks = MAX_TICKS)
    fun pauseHaltsTheNextFunctionThatRuns(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // Functions run in bursts rather than continuously, so the editor's pause button usually finds nothing executing.
            // The request arms a stop instead of performing one, and the next line to run anywhere honours it.
            .thenRequest("pause", { dap.pause(PauseArguments()) })
            .thenExecute {
                assertThat(session).isNotPaused()
                assertTrue(events.stopped.isEmpty(), "Nothing was running, so nothing has stopped yet")
            }
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped) { event ->
                assertEquals(event.reason, "pause", "stop reason")
                assertTrue(event.hitBreakpointIds == null, "No breakpoint was hit, so none should be named")
            }
            .thenExecute {
                assertThat(session).isPaused("The armed pause should have halted the function")
                // Halted on the first line, so none of the function has run.
                assertThat(session).hasExecuted()
            }
            .thenSucceedAndClose()
    }

    /**
     * Reloads so the copied archive is discovered, and waits for its function to load.
     *
     * The reload runs off the server thread and reports nothing when it lands, so this waits for the function itself rather than for a tick count.
     */
    private fun GameTestSequence.thenLoadZippedPack(session: DebugSession): GameTestSequence =
        thenExecute { session.run("reload") }
            .thenWaitUntil {
                assertTrue(
                    session.server.functions.get(Identifier.parse(ZIPPED)).isPresent,
                    "The zipped pack should have been discovered and loaded",
                )
            }

    /**
     * Writes a datapack holding [DIR_FUNCTION] into the world's datapack directory, as a plain directory rather than an archive, and returns the function's path on disk.
     *
     * A function only has a path the debugger can report when it came from a pack unpacked in the world, so building the pack here is what gives it one.
     */
    private fun ensureDirectoryPack(server: MinecraftServer): String {
        val pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DIR_PACK)
        val file = pack.resolve("data").resolve(DIR_NAMESPACE).resolve("function").resolve("$DIR_NAME.mcfunction")
        Files.createDirectories(file.parent)
        Files.writeString(
            pack.resolve("pack.mcmeta"),
            """{"pack":{"description":"sniffer directory pack","pack_format":15,""" +
                """"supported_formats":[15,81],"min_format":15,"max_format":2147483647}}""",
        )
        Files.writeString(
            file,
            "data modify storage sniffer_test:log $DIR_FIRST set value 1\n" +
                "data modify storage sniffer_test:log $DIR_SECOND set value 1\n",
        )
        return file.toAbsolutePath().toString()
    }

    /**
     * Copies the zipped datapack holding [ZIPPED] into the world's datapack directory.
     *
     * Minecraft only reads packs from that directory, so the checked in archive has to be placed there before a reload can find it.
     * Its `pack.mcmeta` declares every format from 15 upwards, so it stays loadable across Minecraft versions without being rebuilt.
     *
     * The copy is left behind afterwards, since Minecraft holds the archive open for as long as the pack is loaded and deleting it would fail on Windows.
     * Finding it already there is therefore the ordinary case, and skipping the copy leaves the pack no less loaded.
     */
    private fun ensureZippedPack(server: MinecraftServer) {
        val zip = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(ZIP_PACK)
        if (Files.exists(zip)) return
        Files.createDirectories(zip.parent)
        javaClass.classLoader.getResourceAsStream(ZIP_PACK).use { source ->
            Files.copy(
                source ?: fail("No such resource: $ZIP_PACK"),
                zip,
            )
        }
    }

    /** The path the debugger resolved for [ZIPPED], as the adapter reports it to a client. */
    private fun zippedPath(): String =
        FunctionPathRegistry.getPath(ZIPPED).orElseThrow { fail("No path was resolved for $ZIPPED") }

    /**
     * Spawns a marker inside the test structure, named so the debugger has something of the entity's own to report.
     *
     * A marker holds still: it has no physics and an empty tick, so the position read while execution is suspended is the one it was spawned at, whatever the structure stands on.
     */
    private fun markerNamed(helper: GameTestHelper, name: String): Entity =
        helper.spawn(EntityTypes.MARKER, BlockPos(1, 2, 1)).apply {
            customName = Component.literal(name)
        }

    private fun breakpointsAt(path: String, vararg lines: Int, condition: String? = null) = SetBreakpointsArguments().apply {
        source = Source().apply { this.path = path }
        breakpoints = lines.map { line ->
            SourceBreakpoint().apply {
                this.line = line
                this.condition = condition
            }
        }.toTypedArray()
    }

    private fun sourceOf(function: String) = SourceArguments().apply {
        source = Source().apply { name = function }
    }

    // ── Stack and variable trees ────────────────────────────────────

    @GameTest(environment = "sniffer_test:dap_stack_pagination", maxTicks = MAX_TICKS)
    fun theStackCanBePaginatedWhileTotalFramesStillDescribesItAll(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(INNER, line = 0)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $OUTER") }
            .thenAwaitEvent("stopped", events.stopped)
            // One frame, starting past the innermost: an editor asking for a window onto a deep stack.
            .thenRequest("stackTrace", { dap.stackTrace(stackTraceOf(startFrame = 1, levels = 1)) }) { response ->
                assertEquals(response.stackFrames.map { it.name }, listOf(OUTER), "the requested window")
                assertEquals(response.totalFrames, 2, "totalFrames counts the whole stack, not the window")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_empty_stack", maxTicks = MAX_TICKS)
    fun anEmptyStackYieldsNoFrames(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            // Nothing is paused, so a client asking anyway gets an answer rather than an error.
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                assertEquals(response.stackFrames.size, 0, "frame count")
                assertEquals(response.totalFrames, 0, "totalFrames")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_directory_source", maxTicks = MAX_TICKS)
    fun aFunctionUnpackedInTheWorldIsServedByPath(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        val file = ensureDirectoryPack(session.server)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("reload") }
            .thenWaitUntil {
                assertTrue(
                    session.server.functions.get(Identifier.parse(DIR_FUNCTION)).isPresent,
                    "The directory pack should have been discovered and loaded",
                )
            }
            .thenExecute {
                session.breakpointAtFile(file, 1)
                session.run("function $DIR_FUNCTION")
            }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                val source = response.stackFrames[0].source
                // This pack is unpacked in the world, so there is a real file behind the function: the editor is pointed at it and opens it itself.
                assertEquals(source.path, file, "source path")
                assertTrue(
                    source.sourceReference == null || source.sourceReference == 0,
                    "A function the client can open itself needs no source reference, got: ${source.sourceReference}",
                )
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_pathless_source", maxTicks = MAX_TICKS)
    fun aFunctionWithNoFileOnDiskIsNamedButNotPathed(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(LINEAR, line = 0)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
                val source = response.stackFrames[0].source
                assertEquals(source.name, LINEAR, "source name")
                // This pack is bundled in a mod rather than unpacked in the world, so nothing recorded a file for it.
                // The frame still has to name the function, or the client would have nothing to show at all.
                assertTrue(source.path == null, "A function with no file on disk should report no path, got: ${source.path}")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_unknown_frame", maxTicks = MAX_TICKS)
    fun anUnknownFrameYieldsAnEmptyScope(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(INNER, line = 0)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $OUTER") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenRequest("scopes", { dap.scopes(ScopesArguments().apply { frameId = UNKNOWN_REFERENCE }) }) { response ->
                val scope = response.scopes.single()
                assertEquals(scope.variablesReference, 0, "an unknown frame expands to nothing")
                assertEquals(scope.namedVariables, 0, "an unknown frame holds nothing")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_location_tree", maxTicks = MAX_TICKS)
    fun theExecutorIsALeafWhileTheLocationExpands(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(INNER, line = 0)
        var locationReference = 0

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $OUTER") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenPausedVariables(dap) { variables ->
                val executor = variables.first { it.name == "executor" }
                val location = variables.first { it.name == "location" }
                // The command source here is the server itself, which has nothing to unfold; where it runs does.
                assertEquals(executor.value, "server", "executor")
                assertEquals(executor.variablesReference, 0, "the executor is a leaf")
                assertTrue(location.variablesReference != 0, "The location should be expandable")
                locationReference = location.variablesReference
            }
            .thenExpand(dap, { locationReference }) { variables ->
                assertEquals(
                    variables.map { it.name },
                    listOf("position", "rotation", "world"),
                    "what a location is made of",
                )
                assertEquals(variables.first { it.name == "world" }.value, "overworld", "world")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_variables_pagination", maxTicks = MAX_TICKS)
    fun variablesCanBePaginated(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(INNER, line = 0)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $OUTER") }
            .thenAwaitEvent("stopped", events.stopped)
            // The second of the two, one at a time.
            .thenPausedVariables(dap, start = 1, count = 1) { variables ->
                assertEquals(variables.map { it.name }, listOf("location"), "the requested page")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:dap_unknown_variables", maxTicks = MAX_TICKS)
    fun anUnknownVariablesReferenceYieldsNoVariables(helper: GameTestHelper) {
        DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenRequest("variables", { dap.variables(variablesOf(UNKNOWN_REFERENCE)) }) { response ->
                assertEquals(response.variables.size, 0, "variables under an unknown reference")
            }
            .thenSucceedAndClose()
    }

    private fun evaluateOf(expression: String) = EvaluateArguments().apply {
        this.expression = expression
    }

    private fun stackTraceOf(startFrame: Int? = null, levels: Int? = null) = StackTraceArguments().apply {
        this.threadId = 1
        this.startFrame = startFrame
        this.levels = levels
    }

    private companion object {
        const val LINEAR = "sniffer_test:linear"
        const val OUTER = "sniffer_test:outer"
        const val INNER = "sniffer_test:inner"
        const val MACRO = "sniffer_test:macro"
        const val NESTED = "sniffer_test:nested/target"
        const val NESTED_FIRST = "nested_first"
        const val NESTED_SECOND = "nested_second"
        const val NESTED_THIRD = "nested_third"
        const val LINEAR_A = "a"
        const val LINEAR_B = "b"
        const val LINEAR_C = "c"

        /** A function called from a breakpoint condition, returning a nonzero result so the condition reads as a success. */
        const val PROBE = "sniffer_test:probe"
        const val PROBE_MARKER = "probe"
        const val PROBE_CONDITION = "function $PROBE"

        /** Storage the conditional breakpoint test flips, kept apart from the log the fixtures write their markers into. */
        const val CONDITION_STORAGE = "sniffer_test:cond"
        const val CONDITION = "execute if data storage $CONDITION_STORAGE {flag:1b}"

        /** A frame or variables id the adapter never handed out, which a client can still ask about after a stale stack. */
        const val UNKNOWN_REFERENCE = 9999

        const val DIR_PACK = "sniffer_directory"
        const val DIR_NAMESPACE = "sniffer_dir"
        const val DIR_NAME = "plain"
        const val DIR_FUNCTION = "$DIR_NAMESPACE:$DIR_NAME"
        const val DIR_FIRST = "dir_first"
        const val DIR_SECOND = "dir_second"

        const val ZIP_PACK = "sniffer_zipped.zip"
        const val ZIPPED = "sniffer_zip:zipped"
        const val ZIP_FIRST = "zip_first"
        const val ZIP_SECOND = "zip_second"

        /**
         * Generous, because every wait here is on wall clock work (a WebSocket round trip, a resume scheduled for the next tick) while a game test server ticks as fast as it can.
         */
        const val MAX_TICKS = 100_000
    }
}