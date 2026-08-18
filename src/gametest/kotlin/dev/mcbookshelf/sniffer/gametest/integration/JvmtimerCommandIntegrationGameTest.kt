package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertFalse
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

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

        session.measure()

        val report = session.report()
        assertTrue(report.hasMeasurement(), "A completed timer should report timings, got: $report")
        assertTrue(TIMER in report, "The report should name the timer, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_untouched")
    fun aTimerThatNeverRanReportsNothing(helper: GameTestHelper) {
        val session = DebugSession(helper)

        val report = session.runCapturing("jvmtimer get gt_never_run").joinToString("\n")

        assertFalse(report.hasMeasurement(), "An unused timer has nothing to report, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_mispaired_start")
    fun aSecondStartWithoutAnEndDisablesTheTimer(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()
        session.measure()

        // A start left hanging, then another: the timer cannot trust its own numbers any more.
        session.run("jvmtimer start $TIMER")
        session.run("jvmtimer start $TIMER")

        val report = session.report()
        assertFalse(report.hasMeasurement(), "A mispaired start should disable the timer, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_unbalanced_end")
    fun endingATimerThatNeverStartedDisablesIt(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()

        session.run("jvmtimer end $TIMER")
        // The disabled timer ignores everything that follows.
        session.measure()

        val report = session.report()
        assertFalse(report.hasMeasurement(), "An unbalanced end should disable the timer, got: $report")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_reset")
    fun resetDropsTheMeasurementsAndLeavesTheTimerUsable(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()
        session.measure()

        session.run("jvmtimer reset $TIMER")

        assertFalse(session.report().hasMeasurement(), "Reset should drop what was measured")
        // Still usable afterward, unlike a disabled timer.
        session.measure()
        assertTrue(session.report().hasMeasurement(), "A reset timer should measure again")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:timer_disable")
    fun aDisabledTimerStopsRecording(helper: GameTestHelper) {
        val session = DebugSession(helper).withCleanTimer()
        session.measure()

        session.run("jvmtimer disable $TIMER")
        session.measure()

        val report = session.report()
        assertFalse(report.hasMeasurement(), "A disabled timer should record nothing, got: $report")
        helper.succeed()
    }

    /**
     * Puts [TIMER] back to how a timer starts life, since the registry holding it is process wide and outlives any one caller.
     * Reset is the full clean slate: it zeroes the counters and re enables a timer that had disabled itself.
     */
    private fun DebugSession.withCleanTimer(): DebugSession = also { run("jvmtimer reset $TIMER") }

    private fun DebugSession.measure() {
        run("jvmtimer start $TIMER")
        run("jvmtimer end $TIMER")
    }

    private fun DebugSession.report(): String = runCapturing("jvmtimer get $TIMER").joinToString("\n")

    private fun String.hasMeasurement(): Boolean = MEASUREMENT in this

    private companion object {
        const val TIMER = "gt_timer"

        /** A duration as the report prints it, for instance `0.042ms`. */
        val MEASUREMENT = Regex("""\d+\.\d+ms""")
    }
}