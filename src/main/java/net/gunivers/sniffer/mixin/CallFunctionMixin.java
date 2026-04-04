package net.gunivers.sniffer.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.logging.LogUtils;
import net.gunivers.sniffer.util.ReflectUtil;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for the CommandFunctionAction class to enhance function execution tracking.
 * This mixin associates the function being executed with its execution frame,
 * which is necessary for proper call stack tracking during debugging.
 *
 * @param <T> The type of command source being used
 *
 * @author Alumopper
 */
@Mixin(CallFunction.class)
public class CallFunctionMixin<T extends ExecutionCommandSource<T>> {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The function being executed by this action */
    @Shadow @Final private InstantiatedFunction<T> function;

    /**
     * Injects code to associate the function with its execution frame.
     * This method is called when a function is executed and sets the function
     * reference in the newly created frame, allowing the debugger to track
     * which function is associated with each frame in the call stack.
     *
     * @param executionCommandSource The command source executing the function
     * @param executionContext The command execution context
     * @param frame The parent execution frame
     * @param ci The callback info
     * @param frame2 The newly created frame for the function execution
     */
    @Inject(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/tasks/ContinuationTask;schedule(Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;Ljava/util/List;Lnet/minecraft/commands/execution/tasks/ContinuationTask$TaskProvider;)V"
            )
    )
    public void onExecute(T executionCommandSource, ExecutionContext<T> executionContext, Frame frame, CallbackInfo ci, @Local(ordinal = 1) Frame frame2){
        ReflectUtil.set(frame2, "function", InstantiatedFunction.class, function).onFailure(LOGGER::error);
    }

}