package dev.mcbookshelf.sniffer.mixin;

import dev.mcbookshelf.sniffer.accessor.ExecutionContextAccessor;
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
 * Mixin on {@link ExecutionContext} that intercepts
 * {@code queueInitialFunctionCall} to route through {@link CallFunction}
 * instead of directly queuing via {@code ContinuationTask.schedule()}.
 *
 * <p>This ensures {@link CallFunctionMixin} fires for top-level function
 * calls (e.g. from {@code /function} or tick/load tags), which is necessary
 * for proper scope pushing.
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
     * Suppress AutoCloseable.close() while the context is stashed by the
     * debugger pause flow. The pause path keeps the context alive across
     * server ticks; the resume path closes it once the queue drains.
     */
    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void sniffer$skipCloseWhileStashed(CallbackInfo ci) {
        if (sniffer$stashed) {
            ci.cancel();
        }
    }

    @Inject(method = "queueInitialFunctionCall", at = @At("HEAD"), cancellable = true)
    private static <T extends ExecutionCommandSource<T>> void redirectThroughCallFunction(
            ExecutionContext<T> context, InstantiatedFunction<T> procedure, T source,
            CommandResultCallback returnValueConsumer, CallbackInfo ci
    ) {
        Frame frame = createTopFrame(context, returnValueConsumer);
        // Queue a CallFunction action instead of directly scheduling entries.
        // This way, CallFunctionMixin fires and handles scope push + cleanup.
        context.queueNext(
            new CommandQueueEntry<>(frame, new CallFunction<>(procedure, source.callback(), false).bind(source))
        );
        ci.cancel();
    }
}
