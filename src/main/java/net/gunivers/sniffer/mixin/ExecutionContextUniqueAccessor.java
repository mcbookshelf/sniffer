package net.gunivers.sniffer.mixin;

import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.UnboundEntryAction;

public interface ExecutionContextUniqueAccessor {

    UnboundEntryAction<?> getNextCommand();
    void setNextCommand(UnboundEntryAction<?> nextCommand);

    boolean getIsLastCommand();
    void setIsLastCommand(boolean lastCommand);

    static ExecutionContextUniqueAccessor of(ExecutionContext<?> context) {
        return (ExecutionContextUniqueAccessor) context;
    }

}
