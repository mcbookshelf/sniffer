package dev.mcbookshelf.sniffer.state

import dev.mcbookshelf.sniffer.network.SetDebuggingPayload
import net.minecraft.commands.CommandSourceStack


/**
 * Runtime state of the stepping engine, written by the step handlers and read by `UnboundDebugMixin`.
 *
 * The mixin reads these fields directly rather than through getters, so they are exposed with `@JvmField`,
 * which turns them into real `public static` Java fields supporting `++` and plain assignment.
 *
 * @author theogiraudet
 */
object SteppingState {

    /**
     * Whether execution is currently paused on a breakpoint or a step.
     * Writes should go through [setDebugging] so the HUD bug icon stays in sync.
     */
    @JvmField
    var isDebugging: Boolean = false

    /** Updates [isDebugging] and broadcasts the new value to every online player. */
    @JvmStatic
    fun setDebugging(value: Boolean) {
        if (isDebugging == value) return
        isDebugging = value
        ConnectionState.broadcast(SetDebuggingPayload(value))
    }

    /**
     * Remaining lines to execute before pausing again.
     * `UnboundDebugMixin` decrements it on every line matching the depth policy of [stepType].
     */
    @JvmField
    var stepsRemaining: Int = 0

    /** Active stepping policy. */
    @JvmField
    var stepType: StepType = StepType.STEP_IN

    /**
     * Frame depth at which the current step was initiated, `-1` when no step is in progress.
     * `STEP_OVER` and `STEP_OUT` compare against it to decide when to pause again.
     */
    @JvmField
    var stepDepth: Int = -1

    /**
     * Whether a pause has been asked for and no line has honoured it yet.
     * Written from the DAP thread, read and cleared by `UnboundDebugMixin` on the server thread.
     */
    @JvmField
    @Volatile
    var pauseRequested: Boolean = false

    /** The command source that was active when the breakpoint triggered. */
    @JvmStatic
    var currSource: CommandSourceStack? = null


    @JvmStatic
    fun isStepIn(): Boolean = stepType == StepType.STEP_IN

    @JvmStatic
    fun isStepOver(): Boolean = stepType == StepType.STEP_OVER

    @JvmStatic
    fun isStepOut(): Boolean = stepType == StepType.STEP_OUT

    /** Clears every stepping field. Called on server start and on DAP disconnect. */
    @JvmStatic
    fun reset() {
        setDebugging(false)
        stepsRemaining = 0
        stepType = StepType.STEP_IN
        stepDepth = -1
        pauseRequested = false
        currSource = null
    }

    /** Clears the stepping fields and drops any paused execution. */
    @JvmStatic
    fun resetAll() {
        reset()
        PausedExecutionStore.discard()
    }
}
