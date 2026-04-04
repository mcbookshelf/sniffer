// TODO(Ravel): Failed to fully resolve file: null
// TODO(Ravel): Failed to fully resolve file: null
// TODO(Ravel): Failed to fully resolve file: null
package net.gunivers.sniffer.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.ExecuteCommand;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.gunivers.sniffer.command.BreakPointCommand;

import static net.gunivers.sniffer.util.Utils.addSnifferPrefix;
import static net.gunivers.sniffer.command.StepType.isStepOut;

/**
 * Mixin for the FixedCommandAction class to enhance command execution during debugging.
 * This mixin adds debugging capabilities to individual command execution,
 * allowing the debugger to track and control command execution flow.
 *
 * @param <T> The type of command source being used
 *
 * @author Alumopper
 */
@Mixin(ExecuteCommand.class)
public class ExecuteCommandMixin<T extends ExecutionCommandSource<T>> {

    /** The command string being executed */
    @Shadow @Final private String commandInput;

    /**
     * Injects code at the start of command execution to handle debugging.
     * This method manages step execution in debug mode and notifies players
     * about the currently executing command for better debugging visibility.
     *
     * @param executionCommandSource The command source executing the command
     * @param executionContext The command execution context
     * @param frame The execution frame
     * @param ci The callback info
     */
    @Inject(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V",
            at = @At("HEAD"))
    private void execute(T executionCommandSource, ExecutionContext<T> executionContext, Frame frame, CallbackInfo ci) {
        if(frame.depth() == 0)
            return;
        // If we are in debug mode, we execute as many commands as determined by the moveSteps variable, except if we found a breakpoint before
        if(BreakPointCommand.isDebugging) {
            if(BreakPointCommand.moveSteps > 0 && !isStepOut()) BreakPointCommand.moveSteps --;
            if(this.commandInput.startsWith("breakpoint")) return;
            if(executionCommandSource instanceof CommandSourceStack serverCommandSource) {
                // We send to each player the executing command
                var players = serverCommandSource.getServer().getPlayerList().getPlayers();
                for(var player : players) {
                    player.sendSystemMessage(addSnifferPrefix(Component.translatable("sniffer.commands.breakpoint.run", this.commandInput).withStyle(ChatFormatting.WHITE)));
                }
            }
        }
    }

}
