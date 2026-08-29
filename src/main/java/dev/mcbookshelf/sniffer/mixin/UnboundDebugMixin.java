package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.UnboundUniqueAccessor;
import dev.mcbookshelf.sniffer.features.breakpoints.BreakpointManager;
import dev.mcbookshelf.sniffer.features.breakpoints.BreakpointTrigger;
import dev.mcbookshelf.sniffer.features.stepping.PausedExecutionStore;
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager;
import dev.mcbookshelf.sniffer.features.stepping.StepType;
import dev.mcbookshelf.sniffer.features.stepping.SteppingState;
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
 * Mixin on {@link BuildContexts.Unbound}, the action run for every function line.
 * It carries the source location attached at parse time, checks breakpoints and stepping,
 * and on a pause drains the live {@link ExecutionContext} into {@link PausedExecutionStore}.
 *
 * <p>The server tick therefore returns immediately and the world keeps running while paused.
 * Executions started during that time are deliberately not debugged.
 *
 * @author Alumopper
 * @author theogiraudet
 */
@Mixin(BuildContexts.Unbound.class)
public class UnboundDebugMixin implements UnboundUniqueAccessor {

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

    /** Fires for every function line and suspends the execution when a breakpoint or a step boundary is reached. */
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void onExecute(ExecutionCommandSource<?> sender, ExecutionContext<?> context, Frame frame, CallbackInfo ci) {
        // Lines run by a breakpoint condition are not part of the debugged execution and must not be checked.
        if (BreakpointManager.INSTANCE.evaluatingCondition) return;

        if (frame.depth() <= 0) return;
        if (sourceFunction == null) return;
        if (sourceLine < 0) return;

        // The first line of a resumed session is the one we paused before, so it must not trigger again.
        if (PausedExecutionStore.skipNextCheck) {
            PausedExecutionStore.skipNextCheck = false;
            return;
        }

        // Commands issued by the user during a pause run in their own context and are left alone.
        if (PausedExecutionStore.isPaused() && !PausedExecutionStore.isStashedContext(context)) {
            return;
        }

        // Why we are stopping, or null to keep running.
        // The DAP client is told which of the two it was, so the cause is carried rather than collapsed into a boolean.
        String stopReason = null;

        // Must run before the scope line is updated, since mustStop compares against it to avoid triggering twice.
        // The sender is passed along so a conditional breakpoint runs its command with the executing command source.
        CommandSourceStack conditionSource = sender instanceof CommandSourceStack senderCss ? senderCss : null;
        if (BreakpointManager.INSTANCE.mustStop(sourceFunction, sourceLine, conditionSource)) {
            stopReason = BreakpointTrigger.BREAKPOINT_REASON;
        }

        // A pause request is honoured by the first line that gets here, and consumed even when a breakpoint won the race.
        if (SteppingState.pauseRequested) {
            SteppingState.pauseRequested = false;
            if (stopReason == null) {
                stopReason = BreakpointTrigger.PAUSE_REASON;
            }
        }

        // shouldStepPause decrements the step counter, so it only runs when no breakpoint has already fired.
        if (stopReason == null && SteppingState.isDebugging && shouldStepPause(frame.depth())) {
            stopReason = BreakpointTrigger.STEP_REASON;
        }

        ScopeManager.Companion.get().getCurrentScope().ifPresent(scope -> scope.recordLine(sourceLine));

        if (stopReason != null && sender instanceof CommandSourceStack css) {
            // Drop memoized variable subtrees so the client sees fresh entity state on the upcoming pause.
            ScopeManager.Companion.get().refreshForPause();
            BreakpointTrigger.trigger(css, stopReason);
            // Cancelling keeps the body of this line from running now.
            // It runs when the stashed entry is replayed, with skipNextCheck holding back the same trigger.
            PausedExecutionStore.stash(context, (UnboundEntryAction<?>) (Object) this, sender, frame);
            ci.cancel();
        }
    }

    /**
     * Applies the stepping policy to the current frame depth.
     * STEP_IN pauses on the next line at any depth, STEP_OVER when the depth is no deeper than where stepping started,
     * and STEP_OUT when it is strictly shallower.
     * A step spanning several lines only pauses once its counter reaches zero.
     *
     * @param currentDepth depth of the frame the line about to run belongs to
     * @return whether that line must be paused on
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

        if (SteppingState.stepsRemaining > 0) {
            SteppingState.stepsRemaining--;
        }
        return SteppingState.stepsRemaining == 0;
    }
}
