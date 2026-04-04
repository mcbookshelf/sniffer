package net.gunivers.sniffer.mixin;

import net.minecraft.commands.execution.tasks.BuildContexts;

public interface UnboundUniqueAccessor {
    String getSourceFunction();
    void setSourceFunction(String sourceFunction);
    int getSourceLine();
    void setSourceLine(int sourceLine);

    static UnboundUniqueAccessor of(BuildContexts.Unbound<?> action) {
        return (UnboundUniqueAccessor) action;
    }
}
