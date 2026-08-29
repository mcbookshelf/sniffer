package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.ExecutionContextAccessor;
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;
import java.util.List;

/**
 * Mixin on {@link ExecutionContext} rerouting {@code queueInitialFunctionCall} through {@link CallFunction},
 * where vanilla queues the entries directly.
 *
 * <p>A top level call, from {@code /function} or from a tick tag,
 * then goes through {@link CallFunctionMixin} like any other call and gets its debug scope.
 *
 * @author Alumopper
 * @author theogiraudet
 */
@Mixin(ExecutionContext.class)
public abstract class InitialFunctionCallMixin<T> implements ExecutionContextAccessor<T> {

    @Shadow @Final
    private Deque<CommandQueueEntry<T>> commandQueue;

    @Shadow @Final
    private List<CommandQueueEntry<T>> newTopCommands;

    @Shadow
    private int currentFrameDepth;

    @Shadow
    private int commandQuota;

    @Unique
    private boolean sniffer$stashed = false;

    @Override
    public Deque<CommandQueueEntry<T>> getCommandQueue() {
        return commandQueue;
    }

    @Override
    public List<CommandQueueEntry<T>> getNewTopCommands() {
        return newTopCommands;
    }

    @Override
    public int getCurrentFrameDepth() {
        return currentFrameDepth;
    }

    @Override
    public void setCurrentFrameDepth(int value) {
        this.currentFrameDepth = value;
    }

    @Override
    public int getCommandQuota() {
        return commandQuota;
    }

    @Override
    public void setCommandQuota(int value) {
        this.commandQuota = value;
    }

    @Override
    public boolean isStashed() {
        return sniffer$stashed;
    }

    @Override
    public void setStashed(boolean value) {
        this.sniffer$stashed = value;
    }

    @Shadow
    private static <T extends ExecutionCommandSource<T>> Frame createTopFrame(
            ExecutionContext<T> context, CommandResultCallback returnValueConsumer
    ) {
        throw new AssertionError();
    }

    /**
     * Keeps the context alive while it is stashed, since a pause spans several server ticks.
     * The resume path closes it once its queue has drained.
     *
     * <p>A close that is not skipped is this execution genuinely ending, however it ended: drained to the last
     * entry, dropped, or cut short by a top level {@code /return} discarding the queue.
     * It is the only signal that covers all three, so it is what the observers of the control flow are told on.
     */
    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void sniffer$skipCloseWhileStashed(CallbackInfo ci) {
        if (sniffer$stashed) {
            ci.cancel();
            return;
        }
        ScopeManager.Companion.get().executionComplete((ExecutionContext<?>) (Object) this);
    }

    @Inject(method = "queueInitialFunctionCall", at = @At("HEAD"), cancellable = true)
    private static <T extends ExecutionCommandSource<T>> void redirectThroughCallFunction(
            ExecutionContext<T> context, InstantiatedFunction<T> procedure, T source,
            CommandResultCallback returnValueConsumer, CallbackInfo ci
    ) {
        Frame frame = createTopFrame(context, returnValueConsumer);
        context.queueNext(
            new CommandQueueEntry<>(frame, new CallFunction<>(procedure, source.callback(), false).bind(source))
        );
        ci.cancel();
    }
}
