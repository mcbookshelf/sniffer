package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.UnboundUniqueAccessor;
import dev.mcbookshelf.sniffer.state.BreakpointManager;
import dev.mcbookshelf.sniffer.state.BreakpointTrigger;
import dev.mcbookshelf.sniffer.state.PausedExecutionStore;
import dev.mcbookshelf.sniffer.state.ScopeManager;
import dev.mcbookshelf.sniffer.state.StepType;
import dev.mcbookshelf.sniffer.state.SteppingState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.execution.tasks.BuildContexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Combined mixin on {@link BuildContexts.Unbound} that:
 * <ul>
 *   <li>Adds source-location fields ({@code sourceFunction}, {@code sourceLine})</li>
 *   <li>Intercepts every function-line execution to check breakpoints and stepping</li>
 *   <li>On a pause, drains the live {@link ExecutionContext} into
 *       {@link PausedExecutionStore} so the server tick can return immediately</li>
 * </ul>
 *
 * <p>Unlike a thread-blocking pause, this implementation lets the world keep
 * running while the debugger is paused. Players can issue commands and other
 * datapack functions can run on subsequent ticks. Nested executions started
 * while paused are intentionally <em>not</em> debugged — see
 * {@link PausedExecutionStore#isStashedContext}.
 */
@Mixin(BuildContexts.Unbound.class)
public class UnboundDebugMixin implements UnboundUniqueAccessor {

    // ── Source-location fields (set during function parsing) ─────────

    @Unique
    private String sourceFunction = null;

    @Unique
    private int sourceLine = -1;

    @Override
    public String getSourceFunction() {
        return sourceFunction;
    }

    @Override
    public void setSourceFunction(String sourceFunction) {
        this.sourceFunction = sourceFunction;
    }

    @Override
    public int getSourceLine() {
        return sourceLine;
    }

    @Override
    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
    }

    // ── Debug hook ──────────────────────────────────────────────────

    /**
     * Injected at the head of {@code Unbound.execute(T, ExecutionContext, Frame)}.
     * Fires for every function line.
     *
     * <p>Order of checks:
     * <ol>
     *   <li>Skip if depth == 0 (top-level, not inside a function)</li>
     *   <li>Skip if a paused session exists and this isn't the same context —
     *       commands run by the user during a pause must not re-trigger the
     *       debugger</li>
     *   <li>Check breakpoints and stepping</li>
     *   <li>If a pause is required, drain the context into
     *       {@link PausedExecutionStore} and return — the queue is empty so
     *       {@code runCommandQueue} exits cleanly</li>
     * </ol>
     */
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void onExecute(ExecutionCommandSource<?> sender, ExecutionContext<?> context, Frame frame, CallbackInfo ci) {
        if (frame.depth() <= 0) return;
        if (sourceFunction == null) return;
        if (sourceLine < 0) return;

        // First entry of a resumed session: skip checks exactly once so the
        // line we paused *before* runs without re-triggering the same
        // breakpoint/step.
        if (PausedExecutionStore.skipNextCheck) {
            PausedExecutionStore.skipNextCheck = false;
            return;
        }

        // Nested execution while paused: skip all debug checks so user-issued
        // commands run normally without interfering with the suspended session.
        if (PausedExecutionStore.isPaused() && !PausedExecutionStore.isStashedContext(context)) {
            return;
        }

        boolean shouldPause = false;

        // 1. Breakpoint check (must happen BEFORE updating the scope line,
        //    because mustStop uses isAtCurrentPosition to avoid re-triggering)
        if (BreakpointManager.INSTANCE.mustStop(sourceFunction, sourceLine)) {
            shouldPause = true;
        }

        // 2. Step-pause check (only when already debugging)
        if (!shouldPause && SteppingState.isDebugging) {
            shouldPause = shouldStepPause(frame.depth());
        }

        // Update the current scope's line so DAP clients see the right position
        ScopeManager.Companion.get().getCurrentScope().ifPresent(scope -> scope.setLine(sourceLine));

        if (shouldPause && sender instanceof CommandSourceStack css) {
            // Drop memoized variable subtrees so the DAP client sees fresh
            // entity state (position, NBT, …) on the upcoming pause.
            ScopeManager.Companion.get().refreshForPause();
            BreakpointTrigger.INSTANCE.trigger(css);
            // Drain the queue, re-queue *this* entry as the first thing to
            // replay on resume, and cancel — so the body of this Unbound.execute
            // does NOT run now. The line will run when the entry is replayed
            // (with skipNextCheck preventing the same trigger from re-firing).
            // BuildContexts.Unbound implements UnboundEntryAction (not EntryAction);
            // PausedExecutionStore.stash binds it to the sender to produce a real
            // EntryAction for the replay queue.
            PausedExecutionStore.stash(context, (UnboundEntryAction<?>) (Object) this, sender, frame);
            ci.cancel();
        }
    }

    /**
     * Determines whether execution should pause based on the active stepping
     * policy and frame depth.
     *
     * <ul>
     *   <li>STEP_IN: pause on the next line at any depth</li>
     *   <li>STEP_OVER: pause when depth &le; the depth where stepping started</li>
     *   <li>STEP_OUT: pause when depth &lt; the depth where stepping started</li>
     * </ul>
     *
     * <p>For multi-line steps ({@code stepsRemaining > 1}), the counter is
     * decremented and pause only happens when it reaches 0.
     */
    @Unique
    private boolean shouldStepPause(int currentDepth) {
        StepType stepType = SteppingState.stepType;
        int stepDepth = SteppingState.stepDepth;

        boolean depthMatch = switch (stepType) {
            case STEP_IN -> true;
            case STEP_OVER -> currentDepth <= stepDepth;
            case STEP_OUT -> currentDepth < stepDepth;
        };

        if (!depthMatch) return false;

        // Decrement multi-step counter
        if (SteppingState.stepsRemaining > 0) {
            SteppingState.stepsRemaining--;
        }
        return SteppingState.stepsRemaining == 0;
    }
}
