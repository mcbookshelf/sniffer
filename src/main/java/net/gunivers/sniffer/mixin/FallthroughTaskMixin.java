package net.gunivers.sniffer.mixin;

import net.gunivers.sniffer.command.BreakPointCommand;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.gunivers.sniffer.command.StepType.isStepOut;


@Mixin(FallthroughTask.class)
public class FallthroughTaskMixin<T> {

    @Inject(method = "execute", at = @At("HEAD"))
    private void onExecute(ExecutionContext<T> executionContext, Frame frame, CallbackInfo ci){
        if(BreakPointCommand.isDebugging && BreakPointCommand.moveSteps > 0 && !isStepOut()) {
            BreakPointCommand.moveSteps --;
        }
    }
}
