package dev.mcbookshelf.sniffer.mixin;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
public abstract class InitialFunctionCallMixin<T> {

    @Shadow
    private static <T extends ExecutionCommandSource<T>> Frame createTopFrame(
            ExecutionContext<T> context, CommandResultCallback returnValueConsumer
    ) {
        throw new AssertionError();
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
