package dev.mcbookshelf.sniffer.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the execution context vanilla keeps for the thread currently running commands.
 *
 * {@link Commands#executeCommandInContext} only creates a fresh context when that thread local is empty,
 * and otherwise queues the command into the running one, where it would be executed long after the caller returned.
 * A breakpoint condition needs its answer immediately, so it clears the thread local for the duration of the run.
 *
 * @author theogiraudet
 */
@Mixin(Commands.class)
public interface CommandsAccessor {

    @Accessor("CURRENT_EXECUTION_CONTEXT")
    static ThreadLocal<ExecutionContext<CommandSourceStack>> getCurrentExecutionContext() {
        throw new AssertionError();
    }
}
