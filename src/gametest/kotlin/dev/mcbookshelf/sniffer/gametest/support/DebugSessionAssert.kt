package dev.mcbookshelf.sniffer.gametest.support

import dev.mcbookshelf.sniffer.features.stepping.StepType
import dev.mcbookshelf.sniffer.features.stepping.SteppingState

/** Entry point for the assertions on a [DebugSession]. */
fun assertThat(session: DebugSession) = DebugSessionAssert(session)

/**
 * Assertions over the debugger's state, chained off [assertThat].
 *
 * Each carries a label describing what was compared, since that label is the whole of what a failing run reports.
 */
class DebugSessionAssert(private val session: DebugSession) {

    /** Asserts that execution is suspended in the debugger. */
    fun isPaused(reason: String = "Execution should be paused") = apply {
        assertTrue(session.isPaused, reason)
    }

    /** Asserts that nothing is suspended in the debugger. */
    fun isNotPaused(reason: String = "Nothing should be paused") = apply {
        assertFalse(session.isPaused, reason)
    }

    /**
     * Asserts that exactly [markers] have been written to the test log storage.
     *
     * The functions under test write one marker per line, so this is "which lines have run so far".
     * Order is not meaningful: a marker is written once whatever happens afterwards, so the set is what carries the information.
     */
    fun hasExecuted(vararg markers: String) = apply {
        assertEquals(session.executed(), markers.toSet(), "executed lines")
    }

    /** Asserts the paused call stack, innermost frame first. */
    fun hasCallStack(vararg functions: String) = apply {
        assertEquals(session.callStack(), functions.toList(), "call stack")
    }

    /** Asserts which kind of step the last resume requested. */
    fun hasStepType(expected: StepType) = apply {
        assertEquals(SteppingState.stepType, expected, "step type")
    }

    /** Asserts that no step is pending, which is what a reset or a refused step leaves behind. */
    fun hasNoPendingStep() = apply {
        assertEquals(SteppingState.stepsRemaining, 0, "steps remaining")
        assertEquals(SteppingState.stepDepth, -1, "step depth")
    }
}
