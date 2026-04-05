package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.FrameUniqueAccessor;
import dev.mcbookshelf.sniffer.state.ScopeManager;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.functions.InstantiatedFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on {@link Frame} to:
 * <ul>
 *   <li>Store which {@link InstantiatedFunction} this frame is executing
 *       (needed for call-stack tracking and source-location lookups)</li>
 *   <li>Pop the debug scope when the frame is discarded by {@code /return}
 *       (the normal-completion scope pop is handled by the cleanup entry
 *       queued in {@link CallFunctionMixin})</li>
 * </ul>
 */
@Mixin(Frame.class)
public class FrameMixin implements FrameUniqueAccessor {

    @Unique
    private InstantiatedFunction<?> function = null;

    @Override
    public InstantiatedFunction<?> getFunction() {
        return function;
    }

    @Override
    public void setFunction(InstantiatedFunction<?> function) {
        this.function = function;
    }

    /**
     * Pops the debug scope when the frame is discarded.
     * Handles the {@code /return} case — when a function returns early,
     * {@code frame.discard()} removes remaining queue entries (including the
     * cleanup entry from {@link CallFunctionMixin}), so we pop scope here.
     */
    @Inject(method = "discard", at = @At("HEAD"))
    private void beforeDiscard(CallbackInfo ci) {
        ScopeManager.Companion.get().unscope();
    }
}
