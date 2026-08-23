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
 * Mixin on {@link Frame} recording which {@link InstantiatedFunction} the frame runs, for the call stack.
 * It also pops the debug scope when {@code /return} discards the frame,
 * the case the cleanup entry queued by {@link CallFunctionMixin} cannot cover.
 *
 * @author Alumopper
 * @author theogiraudet
 */
@Mixin(Frame.class)
public class FrameMixin implements FrameUniqueAccessor {

    @Unique
    private InstantiatedFunction<?> function = null;

    /** Whether this frame's debug scope has already been popped. */
    @Unique
    private boolean scopePopped = false;

    @Override
    public InstantiatedFunction<?> getFunction() {
        return function;
    }

    @Override
    public void setFunction(InstantiatedFunction<?> function) {
        this.function = function;
    }

    /**
     * Pops the debug scope pushed for this frame, at most once.
     *
     * <p>Only frames created by {@code CallFunction} carry a function and had a scope pushed for them.
     * Any other frame, such as the one of a top level {@code /return run}, must leave the stack alone.
     * The {@code scopePopped} guard is there because vanilla can discard the same frame twice.
     */
    @Override
    public void popScopeOnce() {
        if (function == null || scopePopped) return;
        scopePopped = true;
        ScopeManager.Companion.get().unscope();
    }

    /** Covers the early return, where discarding the frame also drops the cleanup entry that would have popped. */
    @Inject(method = "discard", at = @At("HEAD"))
    private void beforeDiscard(CallbackInfo ci) {
        popScopeOnce();
    }
}
