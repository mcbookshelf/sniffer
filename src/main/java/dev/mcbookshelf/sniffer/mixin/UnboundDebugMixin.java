package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.UnboundUniqueAccessor;
import dev.mcbookshelf.sniffer.state.BreakpointManager;
import dev.mcbookshelf.sniffer.state.BreakpointTrigger;
import dev.mcbookshelf.sniffer.state.ExecutionLock;
import dev.mcbookshelf.sniffer.state.ScopeManager;
import dev.mcbookshelf.sniffer.state.StepType;
import dev.mcbookshelf.sniffer.state.SteppingState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
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
 *   <li>Blocks the server thread via {@link ExecutionLock} when a pause is needed</li>
 * </ul>
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
     * This fires for every function line, regardless of whether it was queued
     * via {@code ContinuationTask} or directly (for 1–2 line functions).
     *
     * <p>Checks (in order):
     * <ol>
     *   <li>Skip if depth == 0 (top-level, not inside a function)</li>
     *   <li>Skip if debug mode is globally off</li>
     *   <li>Update current scope line number</li>
     *   <li>Check for breakpoint at current location</li>
     *   <li>Check step-pause condition (depth-based)</li>
     *   <li>If either triggers, block via {@link ExecutionLock#pauseExecution}</li>
     * </ol>
     */
    @Inject(method = "execute", at = @At("HEAD"))
    private void onExecute(ExecutionCommandSource<?> sender, ExecutionContext<?> context, Frame frame, CallbackInfo ci) {
        if (frame.depth() <= 0) return;
        if (sourceFunction == null) return;
        if (sourceLine < 0) return;

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
            ExecutionLock.INSTANCE.pauseExecution();
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
