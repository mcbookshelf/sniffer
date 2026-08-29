package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertEquals
import dev.mcbookshelf.sniffer.gametest.support.assertFalse
import dev.mcbookshelf.sniffer.gametest.support.assertThat
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import dev.mcbookshelf.sniffer.gametest.support.thenAwaitDapReady
import dev.mcbookshelf.sniffer.gametest.support.thenAwaitEvent
import dev.mcbookshelf.sniffer.features.stepping.StepType
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.world.entity.EntityTypes

/**
 * `/breakpoint`, the way to drive the debugger from inside the game rather than from an editor.
 *
 * Its subcommands step, continue, clear and print the call stack, and they dispatch through the same actions a DAP request does.
 * Sharing the dispatcher is what makes the two entrypoints one debugger: a player halting or resuming execution moves the state an attached client is watching, and the client is told about it.
 */
class BreakpointCommandIntegrationGameTest : AbstractDapIntegrationGameTest() {

    // ── Stepping ────────────────────────────────────────────────────

    @GameTest(environment = "sniffer_test:cmd_step", maxTicks = MAX_TICKS)
    fun stepTakesALineCountAndDefaultsToOne(helper: GameTestHelper) {
        val session = DebugSession(helper)
        session.breakpointAt(LINEAR, line = 0)

        helper.startSequence()
            .thenExecute { session.run("function $LINEAR") }
            .thenExecute { session.run("breakpoint step 2") }
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b") }
            .thenExecute {
                assertThat(session).hasStepType(StepType.STEP_IN)
                assertThat(session).isPaused("Execution should be paused again")
            }
            // No count given, so exactly one more line.
            .thenExecute { session.run("breakpoint step") }
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b", "c") }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:cmd_step_over", maxTicks = MAX_TICKS)
    fun stepOverRunsACallWithoutEnteringIt(helper: GameTestHelper) {
        val session = DebugSession(helper)
        // The call line, so the step has a callee to skip over.
        session.breakpointAt(OUTER, line = 1)

        helper.startSequence()
            .thenExecute { session.run("function $OUTER") }
            .thenExecute { session.run("breakpoint step_over") }
            .thenWaitUntil { assertThat(session).hasExecuted("outer_before", "inner_first", "inner_second") }
            .thenExecute {
                assertThat(session).hasStepType(StepType.STEP_OVER)
                assertThat(session).hasCallStack(OUTER)
            }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:cmd_step_out", maxTicks = MAX_TICKS)
    fun stepOutFinishesTheCalleeAndReturnsToTheCaller(helper: GameTestHelper) {
        val session = DebugSession(helper)
        session.breakpointAt(INNER, line = 0)

        helper.startSequence()
            .thenExecute { session.run("function $OUTER") }
            .thenExecute { session.run("breakpoint step_out") }
            .thenWaitUntil { assertThat(session).hasExecuted("outer_before", "inner_first", "inner_second") }
            .thenExecute {
                assertThat(session).hasStepType(StepType.STEP_OUT)
                assertThat(session).hasCallStack(OUTER)
            }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:cmd_step_refused", maxTicks = MAX_TICKS)
    fun steppingIsRefusedWhileNothingIsPaused(helper: GameTestHelper) {
        val session = DebugSession(helper)

        val feedback = session.runCapturing("breakpoint step 3")

        assertTrue(feedback.isNotEmpty(), "A refused step should say so")
        assertThat(session).hasNoPendingStep()
        assertThat(session).isNotPaused("Nothing should be paused")

        session.run("function $LINEAR")

        assertThat(session).hasExecuted("a", "b", "c")
        assertThat(session).isNotPaused("A refused step must not halt whatever runs next")
        helper.succeed()
    }

    // ── Cross entrypoint behaviour ──────────────────────────────────

    @GameTest(environment = "sniffer_test:cmd_trigger", maxTicks = MAX_TICKS)
    fun triggeringABreakpointInGameStopsTheAttachedDapClient(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()

        helper.startSequence()
            .thenAwaitDapReady(dap)
            // No breakpoint is registered: the function calls `breakpoint` itself, which is how a datapack author halts on a line from inside the pack.
            .thenExecute { session.run("function $TRIGGERS") }
            .thenAwaitEvent("stopped", events.stopped) { event ->
                assertEquals(event.reason, "breakpoint", "stop reason")
                // Nothing was registered, so no breakpoint id can be reported.
                assertTrue(event.hitBreakpointIds == null, "No breakpoint should be named")
            }
            .thenExecute {
                assertThat(session).isPaused("Execution should be paused")
                // Halted on the line below the trigger, so the last line has not run.
                assertThat(session).hasExecuted("before_trigger")
                closeDapClient()
            }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:cmd_conditional_trigger", maxTicks = MAX_TICKS)
    fun aTriggerWithAConditionOnlyHaltsWhenItsCommandSucceeds(helper: GameTestHelper) {
        val session = DebugSession(helper)
        // The test world outlives the run, so the flag the condition reads has to start from a known state.
        session.run("data remove storage $COND_STORAGE flag")

        helper.startSequence()
            // `breakpoint if` reads a real command on its success channel, exactly like a DAP breakpoint condition.
            .thenExecute { session.run("function $CONDITIONAL_TRIGGER") }
            .thenWaitUntil { assertThat(session).hasExecuted("before_trigger", "after_trigger") }
            .thenExecute {
                assertThat(session).isNotPaused("A failing condition should let the line through")
                session.clearLog()
                // Flipping the flag is all that changes, so the halt below can only come from the condition now succeeding.
                session.run("data modify storage $COND_STORAGE flag set value 1b")
            }
            .thenExecute { session.run("function $CONDITIONAL_TRIGGER") }
            .thenWaitUntil { assertThat(session).isPaused("A succeeding condition should halt execution") }
            .thenExecute { assertThat(session).hasExecuted("before_trigger") }
            .thenExecute { session.run("breakpoint continue") }
            .thenWaitUntil { assertThat(session).hasExecuted("before_trigger", "after_trigger") }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:cmd_invalid_condition", maxTicks = MAX_TICKS)
    fun aTriggerWhoseConditionIsNotACommandSaysSoAndDoesNotHalt(helper: GameTestHelper) {
        val session = DebugSession(helper)

        // The condition is parsed as a command, so the typo is a plain parse error of the whole line.
        val feedback = session.runCapturing("breakpoint if not_a_command")

        assertTrue(feedback.isNotEmpty(), "An unparseable condition should be reported")
        assertThat(session).isNotPaused("An unparseable condition should not halt execution")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:cmd_condition_suggestions", maxTicks = MAX_TICKS)
    fun theConditionIsCompletedAsACommandWouldBe(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dispatcher = session.server.commands.dispatcher
        val source = session.server.createCommandSourceStack()

        val partial = "breakpoint if exec"
        val suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(partial, source)).join()
        assertTrue(suggestions.list.any { it.text == "execute" }, "The condition should be completed from the command tree")
        // Only the half typed command word may be replaced, or accepting a suggestion would eat the `/breakpoint` in front of it.
        assertEquals(suggestions.range.start, partial.indexOf("exec"), "suggestion start")

        // Completion follows the command into its own arguments, which is the whole point of borrowing the real tree.
        val nested = "breakpoint if execute if "
        val nestedSuggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(nested, source)).join()
        assertTrue(nestedSuggestions.list.any { it.text == "score" }, "The condition's own arguments should be completed too")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:cmd_pause", maxTicks = MAX_TICKS)
    fun pauseInGameHaltsTheNextFunctionThatRuns(helper: GameTestHelper) {
        val session = DebugSession(helper)

        helper.startSequence()
            // Unlike the other subcommands, this one is issued while nothing is suspended, so it arms a stop instead of performing one.
            .thenExecute { session.run("breakpoint pause") }
            .thenExecute { assertThat(session).isNotPaused("There is nothing running to halt yet") }
            .thenExecute { session.run("function $LINEAR") }
            .thenWaitUntil { assertThat(session).isPaused("The armed pause should have halted the function") }
            // Halted on the first line, so none of the function has run.
            .thenExecute { assertThat(session).hasExecuted() }
            // A pause suspends execution the same way a breakpoint does, so the usual resume has to pick it up again.
            .thenExecute { session.run("breakpoint continue") }
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b", "c") }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:cmd_continue", maxTicks = MAX_TICKS)
    fun continueInGameResumesTheAttachedDapClient(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val dap = initDapClient()
        session.breakpointAt(LINEAR, line = 1)

        helper.startSequence()
            .thenAwaitDapReady(dap)
            .thenExecute { session.run("function $LINEAR") }
            .thenAwaitEvent("stopped", events.stopped)
            .thenExecute { session.run("breakpoint continue") }
            .thenAwaitEvent("continued", events.continued) { event ->
                assertEquals(event.threadId, 1, "continued thread")
            }
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b", "c") }
            .thenExecute {
                assertThat(session).isNotPaused("Execution should have resumed")
                closeDapClient()
            }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:cmd_clear", maxTicks = MAX_TICKS)
    fun clearDropsTheSteppingStateAndTheSuspendedExecution(helper: GameTestHelper) {
        val session = DebugSession(helper)
        session.breakpointAt(LINEAR, line = 1)

        helper.startSequence()
            .thenExecute { session.run("function $LINEAR") }
            // One line, so the function is still suspended when the clear arrives.
            // A count that ran past the last line would leave nothing paused whatever the clear did, and the assertions below would hold on their own.
            .thenExecute { session.run("breakpoint step") }
            .thenWaitUntil { assertThat(session).hasExecuted("a", "b") }
            .thenExecute {
                assertThat(session).isPaused("There should be a suspended execution to drop")
                session.run("breakpoint clear")
            }
            .thenWaitUntil {
                assertThat(session).isNotPaused("Clearing should leave nothing paused")
                assertThat(session).hasNoPendingStep()
            }
            .thenSucceed()
    }

    // ── Reporting ───────────────────────────────────────────────────

    @GameTest(environment = "sniffer_test:cmd_stack", maxTicks = MAX_TICKS)
    fun stackPrintsTheCallHierarchyInnermostFrameFirst(helper: GameTestHelper) {
        val session = DebugSession(helper)
        session.breakpointAt(INNER, line = 0)

        helper.startSequence()
            .thenExecute { session.run("function $OUTER") }
            .thenExecute {
                val text = session.runCapturing("breakpoint stack").joinToString("\n")
                // Lines are printed 1 based: the callee is halted on its first.
                assertTrue("$INNER:1" in text, "Expected the paused frame, got: $text")
                assertTrue(
                    text.indexOf(INNER) < text.indexOf(OUTER),
                    "Frames should run innermost first, got: $text",
                )
            }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:cmd_stack_empty", maxTicks = MAX_TICKS)
    fun stackOnAnEmptyCallStackPrintsJustTheHeader(helper: GameTestHelper) {
        val session = DebugSession(helper)

        assertEquals(session.runCapturing("breakpoint stack"), listOf("\nCall stack:\n"), "stack output")
        helper.succeed()
    }

    // ── Reading the paused frame ────────────────────────────────────

    @GameTest(environment = "sniffer_test:cmd_get_macro", maxTicks = MAX_TICKS)
    fun getReportsTheMacroArgumentsOfThePausedFrame(helper: GameTestHelper) {
        val session = DebugSession(helper)
        session.breakpointAt(MACRO, line = 1)

        session.run("function $MACRO {who:\"steve\",nested:{count:3}}")

        assertThat(session).isPaused("Execution should be paused inside the macro")
        val named = session.runCapturing("breakpoint get who").joinToString("\n")
        assertTrue("steve" in named, "Expected the value of the named argument, got: $named")
        assertFalse("count" in named, "Only the argument asked for should be reported, got: $named")
        // Unnamed, so everything the macro was called with.
        val all = session.runCapturing("breakpoint get").joinToString("\n")
        assertTrue("who" in all && "steve" in all, "Expected every argument, got: $all")
        assertTrue("count" in all, "A compound argument should be printed with the rest, got: $all")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:cmd_get_not_macro", maxTicks = MAX_TICKS)
    fun getSaysSoWhenThePausedFrameIsNotAMacro(helper: GameTestHelper) {
        val session = DebugSession(helper)
        session.breakpointAt(LINEAR, line = 1)

        session.run("function $LINEAR")

        assertThat(session).isPaused("Execution should be paused")
        // An ordinary function was never called with arguments, so there is nothing to print and the caller is told why rather than shown an empty compound.
        val feedback = session.runCapturing("breakpoint get").joinToString("\n")
        assertTrue("not a macro" in feedback, "Expected a refusal naming the reason, got: $feedback")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:cmd_run_as_executor", maxTicks = MAX_TICKS)
    fun runExecutesACommandAsThePausedExecutor(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val marker = helper.spawn(EntityTypes.MARKER, BlockPos(1, 2, 1))
        session.breakpointAt(LINEAR, line = 1)

        session.run("execute as ${marker.stringUUID} run function $LINEAR")

        assertThat(session).isPaused("Execution should be paused")
        // Run in the same context as where the function is paused.
        session.run("breakpoint run tag @s add $RAN_AS")

        assertTrue(
            marker.entityTags().contains(RAN_AS),
            "The command should have run as the executor of the paused frame, tags: ${marker.entityTags()}",
        )
        helper.succeed()
    }

    // ── Permissions ─────────────────────────────────────────────────

    @GameTest(environment = "sniffer_test:cmd_permission", maxTicks = MAX_TICKS)
    fun aSourceWithoutGamemasterPermissionCannotReachTheCommand(helper: GameTestHelper) {
        val session = DebugSession(helper)

        // Permission level 0: the command tree's `requires` check fails, so brigadier never finds the node and reports an unknown command.
        val feedback = session.runCapturing("breakpoint stack", LevelBasedPermissionSet.NO_PERMISSIONS)

        assertTrue(feedback.isNotEmpty(), "An unreachable command should be refused")
        assertFalse(
            feedback.any { it.contains("Call stack") },
            "The command must not have run, got: $feedback",
        )
        helper.succeed()
    }

    private companion object {
        const val LINEAR = "sniffer_test:linear"
        const val OUTER = "sniffer_test:outer"
        const val INNER = "sniffer_test:inner"
        const val TRIGGERS = "sniffer_test:triggers_breakpoint"
        const val CONDITIONAL_TRIGGER = "sniffer_test:conditional_trigger"

        /** Storage the conditional trigger reads, kept apart from the log the fixtures write their markers into. */
        const val COND_STORAGE = "sniffer_test:cmd_cond"
        const val MACRO = "sniffer_test:macro"

        const val RAN_AS = "sniffer_ran_as_executor"

        const val MAX_TICKS = 100_000
    }
}
