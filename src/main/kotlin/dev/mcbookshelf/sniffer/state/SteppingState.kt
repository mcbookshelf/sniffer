package dev.mcbookshelf.sniffer.state

import dev.mcbookshelf.sniffer.network.SetDebuggingPayload
import net.minecraft.commands.CommandSourceStack


/**
 * Single source of truth for the stepping-runtime state of the debugger.
 *
 * Fields here are read and written from:
 *  - [dev.mcbookshelf.sniffer.handlers.StepHandler] (via the dispatcher, on user input)
 *  - `UnboundDebugMixin` (the single mixin that checks breakpoints and stepping)
 *  - [isStepIn] / [isStepOver] / [isStepOut] (queried by `UnboundDebugMixin`)
 *
 * Because the mixin accesses these fields directly (not via getters/setters),
 * they are exposed with `@JvmField` — making them real `public static` Java
 * fields that support `++` / `--` / direct assignment.
 */
object SteppingState {

    /**
     * Whether execution is currently paused on a breakpoint/step.
     *
     * Read directly by `UnboundDebugMixin` (hence `@JvmField`); writes should
     * go through [setDebugging] so every change is mirrored to clients via
     * [SetDebuggingPayload] for the HUD bug icon.
     */
    @JvmField
    var isDebugging: Boolean = false

    /**
     * Update [isDebugging] and broadcast the new value to every online
     * player so the HUD bug overlay can appear/disappear in sync with
     * paused execution.
     */
    @JvmStatic
    fun setDebugging(value: Boolean) {
        if (isDebugging == value) return
        isDebugging = value
        ConnectionState.broadcast(SetDebuggingPayload(value))
    }

    /**
     * Remaining lines to execute before re-pausing. Decremented by
     * [UnboundDebugMixin] each time a function line matches the active
     * [stepType] depth policy. When this reaches 0, execution pauses.
     */
    @JvmField
    var stepsRemaining: Int = 0

    /** Active stepping policy (`STEP_IN`/`STEP_OVER`/`STEP_OUT`). */
    @JvmField
    var stepType: StepType = StepType.STEP_IN

    /**
     * Frame depth at which the current step was initiated; `-1` when no
     * step is in progress. Used by STEP_OVER/STEP_OUT in `UnboundDebugMixin`
     * to decide when to re-pause. Written by [StepHandler].
     */
    @JvmField
    var stepDepth: Int = -1

    /** The command source that was active when the breakpoint triggered. */
    @JvmStatic
    var currSource: CommandSourceStack? = null


    @JvmStatic
    fun isStepIn(): Boolean = stepType == StepType.STEP_IN

    @JvmStatic
    fun isStepOver(): Boolean = stepType == StepType.STEP_OVER

    @JvmStatic
    fun isStepOut(): Boolean = stepType == StepType.STEP_OUT

    /** Clear all stepping state — called on server start and on DAP disconnect. */
    @JvmStatic
    fun reset() {
        setDebugging(false)
        stepsRemaining = 0
        stepType = StepType.STEP_IN
        stepDepth = -1
        currSource = null
    }

    /**
     * Full stepping reset: clears stepping counters and drops any
     * paused execution. Called during lifecycle transitions.
     */
    @JvmStatic
    fun resetAll() {
        reset()
        PausedExecutionStore.discard()
    }
}
