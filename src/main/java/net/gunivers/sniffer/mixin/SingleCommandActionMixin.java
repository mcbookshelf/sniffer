package net.gunivers.sniffer.mixin;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.gunivers.sniffer.command.BreakPointCommand;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.*;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.ContinuationTask;
import net.minecraft.commands.execution.tasks.ExecuteCommand;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collection;
import java.util.List;

import static net.gunivers.sniffer.command.StepType.isStepOut;
import static net.minecraft.commands.execution.tasks.BuildContexts.ERROR_FORK_LIMIT_REACHED;

@Mixin(BuildContexts.class)
public class SingleCommandActionMixin<T extends ExecutionCommandSource<T>> {

    @Shadow private final String commandInput = null;
    @Shadow private final ContextChain<T> command = null;

    /**
     * @author
     * @reason
     */
    @SuppressWarnings({"JavadocDeclaration", "ConstantValue", "DataFlowIssue", "unchecked"})
    @Overwrite
    protected void execute(T executionCommandSource, List<T> list, ExecutionContext<T> executionContext, Frame frame, ChainModifiers chainModifiers) {
        ContextChain<T> contextChain = this.command;
        ChainModifiers chainModifiers2 = chainModifiers;
        List<T> list2 = list;
        if (contextChain.getStage() != ContextChain.Stage.EXECUTE) {
            executionContext.profiler().push(() -> "prepare " + this.commandInput);

            try {
                for (int i = executionContext.forkLimit(); contextChain.getStage() != ContextChain.Stage.EXECUTE; contextChain = contextChain.nextStage()) {
                    CommandContext<T> commandContext = contextChain.getTopContext();
                    if (commandContext.isForked()) {
                        chainModifiers2 = chainModifiers2.setForked();
                    }

                    RedirectModifier<T> redirectModifier = commandContext.getRedirectModifier();
                    if (redirectModifier instanceof CustomModifierExecutor<?>) {
                        var customModifierExecutor = (CustomModifierExecutor<T>) redirectModifier;
                        customModifierExecutor.apply(executionCommandSource, list2, contextChain, chainModifiers2, ExecutionControl.create(executionContext, frame));
                        return;
                    }

                    if (redirectModifier != null) {
                        executionContext.incrementCost();
                        boolean bl = chainModifiers2.isForked();
                        List<T> list3 = new ObjectArrayList<>();

                        for (T executionCommandSource2 : list2) {
                            try {
                                Collection<T> collection = ContextChain.runModifier(commandContext, executionCommandSource2, (commandContextx, blx, ix) -> {}, bl);
                                if (list3.size() + collection.size() >= i) {
                                    executionCommandSource.handleError(ERROR_FORK_LIMIT_REACHED.create(i), bl, executionContext.tracer());
                                    return;
                                }

                                list3.addAll(collection);
                            } catch (CommandSyntaxException var20) {
                                executionCommandSource2.handleError(var20, bl, executionContext.tracer());
                                if (!bl) {
                                    return;
                                }
                            }
                        }

                        list2 = list3;
                    }
                }
            } finally {
                executionContext.profiler().pop();
            }
        }

        if (list2.isEmpty()) {
            if (chainModifiers2.isReturn()) {
                executionContext.queueNext(new CommandQueueEntry<T>(frame, FallthroughTask.instance()));
                if(BreakPointCommand.isDebugging && BreakPointCommand.moveSteps > 0 && !isStepOut()) {
                    BreakPointCommand.moveSteps --;
                }
            }
        } else {
            CommandContext<T> commandContext2 = contextChain.getTopContext();
            if (commandContext2.getCommand() instanceof CustomCommandExecutor<?> ) {
                var customCommandExecutor = (CustomCommandExecutor<T>) commandContext2.getCommand();
                ExecutionControl<T> executionControl = ExecutionControl.create(executionContext, frame);

                for (T executionCommandSource3 : list2) {
                    customCommandExecutor.run(executionCommandSource3, contextChain, chainModifiers2, executionControl);
                }
            } else {
                if (chainModifiers2.isReturn()) {
                    T executionCommandSource4 = list2.getFirst();
                    executionCommandSource4 = executionCommandSource4.withCallback(
                            CommandResultCallback.chain(executionCommandSource4.callback(), frame.returnValueConsumer())
                    );
                    list2 = List.of(executionCommandSource4);
                }

                ExecuteCommand<T> executeCommand = new ExecuteCommand<>(this.commandInput, chainModifiers2, commandContext2);
                ContinuationTask.schedule(
                        executionContext, frame, list2, (framex, executionCommandSourcex) -> new CommandQueueEntry<>(framex, executeCommand.bind(executionCommandSourcex))
                );
            }
        }
    }
}
