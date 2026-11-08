package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.chat.SnifferChat
import dev.mcbookshelf.sniffer.features.jvmtimer.JvmtimerCommand
import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertFalse
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component

/**
 * `/jvmtimer` measures the wall clock time between `start` and `end`, accumulating a total, a count and a min and max that `get` reports.
 * Pairing is the whole contract, since an unmatched call leaves the numbers describing nothing:
 * a second `start` before an `end`, or an `end` that was never started, disables the timer, which then ignores everything until a `reset` brings it back.
 *
 * A timer keeps its measurements to itself and only ever surrenders them through `get`, which reports either the durations it gathered or a line saying it has nothing to show.
 */
class JvmtimerCommandIntegrationGameTest {

    @GameTest(environment = "sniffer_test:timer_measures")
    fun aStartEndPairRecordsAMeasurement(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()

        val chat = session.measure()
        chat.assertExactly(listOf(msg("started"), msg("stopped")), "A normal start/end cycle")

        val report = session.report()
        assertTrue(report.hasMeasurement(), "A completed timer should report timings, got: $report")
        assertTrue(TIMER in report, "The report should name the timer, got: $report")
        assertTrue(label("id") in report, "The report should contain the id label, got: $report")
        assertTrue(label("total") in report, "The report should contain the total label, got: $report")
        assertTrue(label("count") in report, "The report should contain the count label, got: $report")
        assertTrue(label("average") in report, "The report should contain the average label, got: $report")
        assertTrue(label("min_max") in report, "The report should contain the min/max label, got: $report")
        assertTrue("1" in report, "The count should be 1 after a single measure, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_untouched")
    fun aTimerThatNeverRanReportsNothing(helper: GameTestHelper) {
        val session = DebugSession(helper)

        val chat = session.runCapturing("jvmtimer get gt_never_run")
        val report = chat.joinToString("\n")

        assertFalse(report.hasMeasurement(), "An unused timer has nothing to report, got: $report")
        chat.assertExactly(listOf(msg("not_started", "gt_never_run")), "An untouched timer should only report 'not started'")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_get_before_end")
    fun getOnARunningTimerReportsNotFinished(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()

        session.run("jvmtimer start $TIMER")
        val chat = session.runCapturing("jvmtimer get $TIMER")
        chat.assertExactly(listOf(msg("not_finished")), "get on a running timer should say 'not finished'")

        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_mispaired_start")
    fun aSecondStartWithoutAnEndDisablesTheTimer(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()
        session.measure()

        // A start left hanging, then another: the timer cannot trust its own numbers any more.
        session.run("jvmtimer start $TIMER")
        val doubleStart = session.runCapturing("jvmtimer start $TIMER")
        doubleStart.assertExactly(listOf(msg("already_started")), "A double start should only report the mispair")

        // A third start on a disabled timer should report the disabled state, not succeed silently.
        val thirdStart = session.runCapturing("jvmtimer start $TIMER")
        thirdStart.assertExactly(listOf(msg("disable")), "A start on a disabled timer should only say 'disabled'")

        val report = session.report()
        assertFalse(report.hasMeasurement(), "A mispaired start should disable the timer, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_unbalanced_end")
    fun endingATimerThatNeverStartedDisablesIt(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()

        val endWithoutStart = session.runCapturing("jvmtimer end $TIMER")
        endWithoutStart.assertExactly(listOf(msg("not_running")), "An end without start should only report the error")

        // The disabled timer ignores everything that follows and no success messages should leak.
        val chat = session.measure()
        chat.assertExactly(listOf(msg("disable"), msg("disable")), "Both start and end on a disabled timer should only say 'disabled'")

        val report = session.report()
        assertFalse(report.hasMeasurement(), "An unbalanced end should disable the timer, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_reset")
    fun resetDropsTheMeasurementsAndLeavesTheTimerUsable(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()
        session.measure()

        val resetChat = session.runCapturing("jvmtimer reset $TIMER")
        resetChat.assertExactly(listOf(msg("reset")), "reset should only say 'reset'")

        assertFalse(session.report().hasMeasurement(), "Reset should drop what was measured")
        // Still usable afterward, unlike a disabled timer.
        val chat = session.measure()
        chat.assertExactly(listOf(msg("started"), msg("stopped")), "A start/end cycle after reset")

        assertTrue(session.report().hasMeasurement(), "A reset timer should measure again")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_disable")
    fun aDisabledTimerStopsRecording(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()
        session.measure()

        val disableChat = session.runCapturing("jvmtimer disable $TIMER")
        disableChat.assertExactly(listOf(msg("disable")), "disable should only say 'disabled'")

        // After disable, measure should not produce success messages, only "disabled" errors.
        val chat = session.measure()
        chat.assertExactly(listOf(msg("disable"), msg("disable")), "Both start and end on a disabled timer should only say 'disabled'")

        val report = session.report()
        assertFalse(report.hasMeasurement(), "A disabled timer should record nothing, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_flag")
    fun theProfileDirectiveMarksTheFunctionAsProfiled(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_SIMPLE).reset()

        assertTrue(session.isProfiled(PROFILED_SIMPLE), "A function with #!jvmtimer profile file should be profiled")
        assertFalse(session.isProfiled(NOT_PROFILED), "A function without the directive should not be profiled")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_simple")
    fun aProfiledFunctionRecordsAMeasurementAutomatically(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_SIMPLE).reset()

        session.run("function $PROFILED_SIMPLE")

        val reportText = session.runCapturing("jvmtimer get $PROFILED_SIMPLE").joinToString("\n")
        assertTrue(reportText.hasMeasurement(), "Running a profiled function should produce a timing, got: $reportText")
        assertTrue(label("id") in reportText, "The report should contain the id label, got: $reportText")
        assertTrue(PROFILED_SIMPLE in reportText, "The report should name the timer, got: $reportText")
        assertTrue("1" in reportText, "The count should be 1 after a single call, got: $reportText")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_return")
    fun aProfiledFunctionWithReturnStillRecordsAMeasurement(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_WITH_RETURN).reset()

        session.run("function $PROFILED_WITH_RETURN")

        val report = session.runCapturing("jvmtimer get $PROFILED_WITH_RETURN").joinToString("\n")
        assertTrue(report.hasMeasurement(), "A profiled function with return should still record a timing, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_return_fn")
    fun aProfiledFunctionWithReturnRunFunctionTimesTheSubFunction(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_RETURN_FN).reset()
        JvmtimerCommand.getTimer(PROFILED_HEAVY_SUB).reset()

        session.run("function $PROFILED_RETURN_FN")

        assertTrue(session.executed().contains("sub_executed"), "The sub-function should have run")

        val parentReport = session.runCapturing("jvmtimer get $PROFILED_RETURN_FN").joinToString("\n")
        assertTrue(parentReport.hasMeasurement(), "The parent should record a timing, got: $parentReport")

        val subReport = session.runCapturing("jvmtimer get $PROFILED_HEAVY_SUB").joinToString("\n")
        assertTrue(subReport.hasMeasurement(), "The sub-function should record a timing, got: $subReport")

        val parentTotal = parentReport.extractTotalMs()
        val subTotal = subReport.extractTotalMs()
        assertTrue(parentTotal >= subTotal,
            "The parent timer ($parentTotal ms) should include the sub-function time ($subTotal ms) — " +
            "this proves timer.end() fires after the return run function completes")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_multiple_calls")
    fun multipleCallsToAProfiledFunctionAccumulateMeasurements(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_SIMPLE).reset()

        session.run("function $PROFILED_SIMPLE")
        session.run("function $PROFILED_SIMPLE")
        session.run("function $PROFILED_SIMPLE")

        val report = session.runCapturing("jvmtimer get $PROFILED_SIMPLE").joinToString("\n")
        assertTrue(report.hasMeasurement(), "Multiple calls should produce timings, got: $report")
        assertTrue(label("count") in report, "The report should contain the count label, got: $report")
        assertTrue("3" in report, "The count should be 3 after three calls, got: $report")
        assertTrue(label("min_max") in report, "The report should contain the min/max label, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_nested")
    fun nestedProfiledFunctionsBothRecordTheirOwnTimers(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_CALLER).reset()
        JvmtimerCommand.getTimer(PROFILED_SIMPLE).reset()

        session.run("function $PROFILED_CALLER")

        val callerReport = session.runCapturing("jvmtimer get $PROFILED_CALLER").joinToString("\n")
        assertTrue(callerReport.hasMeasurement(), "The outer profiled function should have a timing, got: $callerReport")

        val innerReport = session.runCapturing("jvmtimer get $PROFILED_SIMPLE").joinToString("\n")
        assertTrue(innerReport.hasMeasurement(), "The inner profiled function should have a timing, got: $innerReport")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_disable")
    fun aDisabledProfilerTimerStopsRecording(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_SIMPLE).reset()

        session.run("function $PROFILED_SIMPLE")
        val firstReport = session.runCapturing("jvmtimer get $PROFILED_SIMPLE").joinToString("\n")
        assertTrue(firstReport.hasMeasurement(), "First call should record, got: $firstReport")

        val disableChat = session.runCapturing("jvmtimer disable $PROFILED_SIMPLE")
        disableChat.assertExactly(listOf(msg("disable", PROFILED_SIMPLE)), "disable should only say 'disabled'")

        session.run("function $PROFILED_SIMPLE")

        val disabledReport = session.runCapturing("jvmtimer get $PROFILED_SIMPLE").joinToString("\n")
        assertFalse(disabledReport.hasMeasurement(), "A disabled timer should not record new measurements, got: $disabledReport")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_reset")
    fun resetAllowsAProfilerTimerToMeasureAgain(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_SIMPLE).reset()

        session.run("function $PROFILED_SIMPLE")
        assertTrue(session.runCapturing("jvmtimer get $PROFILED_SIMPLE").joinToString("\n").hasMeasurement(), "Should have a timing before reset")

        val resetChat = session.runCapturing("jvmtimer reset $PROFILED_SIMPLE")
        resetChat.assertExactly(listOf(msg("reset", PROFILED_SIMPLE)), "reset should only say 'reset'")

        val afterReset = session.runCapturing("jvmtimer get $PROFILED_SIMPLE").joinToString("\n")
        assertFalse(afterReset.hasMeasurement(), "Reset should clear the measurements")

        session.run("function $PROFILED_SIMPLE")
        val afterSecondRun = session.runCapturing("jvmtimer get $PROFILED_SIMPLE").joinToString("\n")
        assertTrue(afterSecondRun.hasMeasurement(), "The timer should measure again after reset, got: $afterSecondRun")
        assertTrue("1" in afterSecondRun, "The count should be 1 after one call post-reset, got: $afterSecondRun")
        helper.succeed()
    }
    
    @GameTest(environment = "sniffer_test:profile_recursive")
    fun recursiveProfiledFunctionsDisableOnDoubleStart(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_RECURSIVE).reset()

        // Set the recurse flag so A calls itself once.
        session.run("data modify storage sniffer_test:log recurse set value 1b")
        // Recursive function profiling is not supported
        session.run("function $PROFILED_RECURSIVE")
        val report = session.runCapturing("jvmtimer get $PROFILED_RECURSIVE").joinToString("\n")
        assertFalse(report.hasMeasurement(), "A recursive profiled function should not have valid measurements, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_macro")
    fun aProfiledMacroFunctionRecordsAMeasurement(helper: GameTestHelper) {
        val session = DebugSession(helper)
        JvmtimerCommand.getTimer(PROFILED_MACRO).reset()

        assertTrue(session.isProfiled(PROFILED_MACRO), "The macro function should be profiled")

        session.run("function $PROFILED_MACRO {who:\"steve\"}")

        assertTrue(session.executed().contains("macro_entered"), "The macro should have run")
        val report = session.runCapturing("jvmtimer get $PROFILED_MACRO").joinToString("\n")
        assertTrue(report.hasMeasurement(), "A profiled macro should record a timing, got: $report")
        assertTrue(label("id") in report, "The report should contain the id label, got: $report")
        assertTrue(PROFILED_MACRO in report, "The report should name the timer, got: $report")
        assertTrue("1" in report, "The count should be 1 after a single call, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:profile_line_mapping", maxTicks = MAX_TICKS)
    fun theProfileDirectiveDoesNotBreakTheLineMapping(helper: GameTestHelper) {
        val session = DebugSession(helper)
        // line 0 is the directive, line 1 is profiled_a, line 2 is profiled_b.
        // Pausing on line 2 means line 1 (profiled_a) has run but line 2 (profiled_b) has not.
        session.breakpointAt(PROFILED_SIMPLE, line = 2)

        helper.startSequence()
            .thenExecute { session.run("function $PROFILED_SIMPLE") }
            .thenWaitUntil {
                assertTrue(session.isPaused, "Execution should be paused on line 2")
            }
            .thenExecute {
                assertTrue(session.executed().contains("profiled_a"), "Line 1 should have run before the breakpoint")
                assertFalse(session.executed().contains("profiled_b"), "Line 2 should not have run yet")
                session.run("breakpoint continue")
            }
            .thenWaitUntil {
                assertTrue(session.executed().contains("profiled_b"), "Line 2 should have run after continuing")
            }
            .thenSucceed()
    }

    /**
     * Puts [TIMER] back to how a timer starts life, since the registry holding it is process wide and outlives any one caller.
     * Reset is the full clean slate: it zeroes the counters and re enables a timer that had disabled itself.
     */
    private fun DebugSession.withCleanTimer(): DebugSession = also { run("jvmtimer reset $TIMER") }

    private fun DebugSession.measure(): List<String> {
        val messages = mutableListOf<String>()
        messages += runCapturing("jvmtimer start $TIMER")
        messages += runCapturing("jvmtimer end $TIMER")
        return messages
    }

    private fun DebugSession.report(): String = runCapturing("jvmtimer get $TIMER").joinToString("\n")

    private fun String.hasMeasurement(): Boolean = MEASUREMENT in this

    private fun String.extractTotalMs(): Double {
        val match = MEASUREMENT.find(this) ?: error("No total found in report: $this")
        return match.value.removeSuffix("ms").toDouble()
    }

    /** Resolves the expected chat message for a jvmtimer translation key, including the [Sniffer] prefix. */
    private fun msg(keySuffix: String, timer: String = TIMER): String =
        SnifferChat.prefixed(Component.translatable("sniffer.commands.jvmtimer.$keySuffix", timer)).string

    /** Resolves a report label from the info translation keys. */
    private fun label(keySuffix: String): String =
        Component.translatable("sniffer.commands.jvmtimer.info.$keySuffix").string

    private fun List<String>.assertExactly(expected: List<String>, description: String) {
        assertTrue(this == expected, "$description: expected $expected but got $this")
    }

    private companion object {
        const val TIMER = "gt_timer"
        const val PROFILED_SIMPLE = "sniffer_test:profiled_simple"
        const val PROFILED_WITH_RETURN = "sniffer_test:profiled_with_return"
        const val PROFILED_RETURN_FN = "sniffer_test:profiled_return_fn"
        const val PROFILED_CALLER = "sniffer_test:profiled_caller"
        const val PROFILED_RECURSIVE = "sniffer_test:profiled_recursive"
        const val PROFILED_HEAVY_SUB = "sniffer_test:profiled_heavy_sub"
        const val PROFILED_MACRO = "sniffer_test:profiled_macro"
        const val NOT_PROFILED = "sniffer_test:not_profiled"
        const val MAX_TICKS = 100_000

        /** A duration as the report prints it, for instance `0.042ms`. */
        val MEASUREMENT = Regex("""\d+\.\d+ms""")

    }
}