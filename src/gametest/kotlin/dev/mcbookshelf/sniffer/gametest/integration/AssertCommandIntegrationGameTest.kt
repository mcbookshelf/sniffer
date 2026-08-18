package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertEquals
import dev.mcbookshelf.sniffer.gametest.support.assertFalse
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import dev.mcbookshelf.sniffer.gametest.support.chatSentTo
import dev.mcbookshelf.sniffer.gametest.support.placePlayer
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.permissions.LevelBasedPermissionSet

/**
 * `/assert` evaluates a `{ ... }` expression and passes only when it yields a true byte, the shape a comparison produces.
 * Everything else fails, and fails the same way whatever the result turned out to be: a number, a piece of text, an operand naming something that does not exist, an expression that throws while evaluating.
 * What a failure is worth is the call stack it prints, which says which line of which function made the claim that did not hold.
 *
 * A failure reaches both of a command's channels, the result and the success vanilla derives from whether it returned at all, so that `execute if` and `execute store success` see it as the failure it is.
 * The message itself is broadcast to every player rather than returned to the caller, because an assertion that fails deep inside a function would otherwise report where nobody is looking.
 */
class AssertCommandIntegrationGameTest {

    @GameTest(environment = "sniffer_test:assert_passes")
    fun aTrueExpressionPasses(helper: GameTestHelper) {
        val session = DebugSession(helper)

        session.assertThat("{1 == 1}", into = "passed")

        assertEquals(session.stored("passed") ?: MISSING, 1, "assert on a true expression")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_fails")
    fun aFalseExpressionFails(helper: GameTestHelper) {
        val session = DebugSession(helper)

        session.assertThat("{1 == 2}", into = "failed")

        assertEquals(session.stored("failed") ?: MISSING, 0, "assert on a false expression")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_not_a_byte")
    fun aResultThatIsNotABooleanFails(helper: GameTestHelper) {
        val session = DebugSession(helper)

        // Arithmetic yields an int; only a comparison yields the byte an assertion needs.
        session.assertThat("{1 + 1}", into = "not_a_byte")

        assertEquals(session.stored("not_a_byte") ?: MISSING, 0, "assert on a non boolean result")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_unevaluable")
    fun anExpressionThatThrowsWhileEvaluatingFails(helper: GameTestHelper) {
        val session = DebugSession(helper)

        // The objective does not exist, so the operand throws rather than returning a value.
        session.assertThat("{(score gt_holder gt_missing) == 1}", into = "unevaluable")

        assertEquals(session.stored("unevaluable") ?: MISSING, 0, "assert on an unevaluable expression")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_score")
    fun anExpressionCanReadAScoreboardValue(helper: GameTestHelper) {
        val session = DebugSession(helper)
        session.run("scoreboard objectives add gt_obj dummy")
        session.run("scoreboard players set gt_holder gt_obj 5")

        session.assertThat("{(score gt_holder gt_obj) == 5}", into = "score")

        assertEquals(session.stored("score") ?: MISSING, 1, "assert on a score")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_name")
    fun anExpressionCanResolveAName(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val (player, _) = placePlayer(helper, NAMED, op = true)

        // Comparing is what makes a name usable: `==` has an arm for text against a string, and it yields the byte an assertion needs.
        session.assertThat("""{(name Steve) == "Steve"}""", into = "name_literal")
        // A selector is the point of the operand, and the only form that has to reach the world to be answered.
        session.run(
            """execute as ${player.name.string} store result storage sniffer_test:log name_selector int 1 """ +
                """run assert {(name @s) == "$NAMED"}"""
        )

        assertEquals(session.stored("name_literal") ?: MISSING, 1, "a name compared to its own text")
        assertEquals(session.stored("name_selector") ?: MISSING, 1, "a selector resolved to a display name")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_component_result")
    fun aResultThatIsTextFails(helper: GameTestHelper) {
        val session = DebugSession(helper)

        // The failure message renders the offending value by type, and `(name ...)` is the operand that yields text:
        // it resolves through vanilla's chat message argument, which returns a component so that a selector can expand into several display names.
        session.assertThat("{(name Steve)}", into = "component")

        assertEquals(session.stored("component") ?: MISSING, 0, "assert on a component result")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_stack")
    fun aFailureReportsWhereItHappened(helper: GameTestHelper) {
        val session = DebugSession(helper)
        // A failure is broadcast, so someone has to be online to receive it.
        val (_, channel) = placePlayer(helper, WITNESS, op = true)

        session.run("function $CALLS_FAILING_ASSERT")

        val report = chatSentTo(channel).joinToString("\n")
        // Frames are printed innermost first, one indexed, so the assertion is pinned to the line it is written on.
        assertTrue("$FAILING_ASSERT:1" in report, "The failure should name the failing line, got: $report")
        assertTrue(
            report.indexOf(FAILING_ASSERT) < report.indexOf(CALLS_FAILING_ASSERT),
            "The failure should report the caller below the callee, got: $report",
        )
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_success_channel")
    fun aFailureFailsOnTheSuccessChannelToo(helper: GameTestHelper) {
        val session = DebugSession(helper)

        session.assertSuccessOf("{1 == 1}", into = "succeeded")
        session.assertSuccessOf("{1 == 2}", into = "did_not_succeed")
        // A result of 0 is not enough on its own: `execute if` and `store success` read the success channel, not the result,
        // and vanilla counts any command that returns at all as a success.
        session.assertSuccessOf("{1 + 1}", into = "unusable_did_not_succeed")

        assertEquals(session.stored("succeeded") ?: MISSING, 1, "success of a passing assertion")
        assertEquals(session.stored("did_not_succeed") ?: MISSING, 0, "success of a failing assertion")
        assertEquals(session.stored("unusable_did_not_succeed") ?: MISSING, 0, "success of an unreadable result")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_continues")
    fun aFailureDoesNotStopTheFunctionItIsWrittenIn(helper: GameTestHelper) {
        val session = DebugSession(helper)

        session.run("function $FAILING_ASSERT")

        // An assertion marks a claim that did not hold; it is not a reason to abandon the rest of the run.
        assertEquals(session.stored("after_failed_assert") ?: MISSING, 1, "the line after a failed assertion")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:assert_permission")
    fun aSourceWithoutGamemasterPermissionCannotReachTheCommand(helper: GameTestHelper) {
        val session = DebugSession(helper)

        // Permission level 0: the `requires` check fails, so brigadier never finds the node and reports an unknown command.
        val feedback = session.runCapturing("assert {1 == 1}", LevelBasedPermissionSet.NO_PERMISSIONS)

        assertTrue(feedback.isNotEmpty(), "An unreachable command should be refused")
        // The refusal echoes the command back, so what says it never ran is the absence of its own output.
        assertFalse(
            feedback.any { PASSED in it },
            "The command must not have run, got: $feedback",
        )
        helper.succeed()
    }

    /** Runs `assert [expr]` and keeps its return value under [into] in the test log storage. */
    private fun DebugSession.assertThat(expression: String, into: String) =
        run("execute store result storage sniffer_test:log $into int 1 run assert $expression")

    /** Runs `assert [expr]` and keeps whether it succeeded under [into] in the test log storage. */
    private fun DebugSession.assertSuccessOf(expression: String, into: String) =
        run("execute store success storage sniffer_test:log $into int 1 run assert $expression")

    private companion object {
        const val FAILING_ASSERT = "sniffer_test:failing_assert"
        const val CALLS_FAILING_ASSERT = "sniffer_test:calls_failing_assert"
        const val WITNESS = "sniffer_assert"
        const val NAMED = "sniffer_name"

        /** What a passing assertion reports back to its caller. */
        const val PASSED = "Assert passed"

        /** Stands in for a key the command never wrote, so a missing result fails rather than reading as 0. */
        const val MISSING = -1
    }
}