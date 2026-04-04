package net.gunivers.sniffer.command;

import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.gunivers.sniffer.dap.ScopeManager;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.functions.CommandFunction;

import static net.gunivers.sniffer.command.StepType.isStepOut;

/**
 * Action handler for when a function is exited during debugging.
 * This class manages the function call stack by popping the current function
 * and its source from the stack when a function returns.
 *
 * @param <T> The type of command source being used
 *
 * @author Alumopper
 * @author theogiraudet
 */
public class FunctionOutAction<T extends ExecutionCommandSource<T>> implements UnboundEntryAction<T> {

    /** 
     * The function being exited.
     * This reference is kept to identify which Minecraft function is being terminated
     * and is used for debug information and scope management.
     */
    CommandFunction<T> function;

    /**
     * Creates a new function exit action handler.
     * @param function The function that is being exited
     */
    public FunctionOutAction(CommandFunction<T> function){
        this.function = function;
    }

    /**
     * Executes the function exit action.
     * Pops the current function and its source from the stack for debugging purposes.
     *
     * @param source The command source executing the function
     * @param context The command execution context
     * @param frame The current execution frame
     */
    @Override
    public void execute(T source, ExecutionContext<T> context, Frame frame) {
        ScopeManager.get().unscope();
        // BreakPointCommand.stepDepth - 1 because we only want to decrement if we go higher than the stepDepth
        if(BreakPointCommand.isDebugging && BreakPointCommand.moveSteps > 0 && isStepOut() && frame.depth() - 1 <= BreakPointCommand.stepDepth - 1) BreakPointCommand.moveSteps --;
    }
}
