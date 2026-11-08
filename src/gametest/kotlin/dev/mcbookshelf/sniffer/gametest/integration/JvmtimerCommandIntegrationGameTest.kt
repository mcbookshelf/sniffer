package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.chat.SnifferChat
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

    @GameTest(environment = "sniffer_test:jvmtimer/timer_measures")
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

    @GameTest(environment = "sniffer_test:jvmtimer/timer_untouched")
    fun aTimerThatNeverRanReportsNothing(helper: GameTestHelper) {
        val session = DebugSession(helper)

        val chat = session.runCapturing("jvmtimer get gt_never_run")
        val report = chat.joinToString("\n")

        assertFalse(report.hasMeasurement(), "An unused timer has nothing to report, got: $report")
        chat.assertExactly(listOf(msg("not_started", "gt_never_run")), "An untouched timer should only report 'not started'")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:jvmtimer/timer_get_before_end")
    fun getOnARunningTimerReportsNotFinished(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()

        session.run("jvmtimer start $TIMER")
        val chat = session.runCapturing("jvmtimer get $TIMER")
        chat.assertExactly(listOf(msg("not_finished")), "get on a running timer should say 'not finished'")

        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:jvmtimer/timer_mispaired_start")
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

    @GameTest(environment = "sniffer_test:jvmtimer/timer_unbalanced_end")
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

    @GameTest(environment = "sniffer_test:jvmtimer/timer_reset")
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

    @GameTest(environment = "sniffer_test:jvmtimer/timer_disable")
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

        /** A duration as the report prints it, for instance `0.042ms`. */
        val MEASUREMENT = Regex("""\d+\.\d+ms""")
    }
}
