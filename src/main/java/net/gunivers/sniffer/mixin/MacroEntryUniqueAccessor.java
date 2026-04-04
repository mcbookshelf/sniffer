package net.gunivers.sniffer.mixin;

import net.minecraft.commands.functions.MacroFunction;

public interface MacroEntryUniqueAccessor {
    int getLine();
    void setLine(int line);

    static MacroEntryUniqueAccessor of(MacroFunction.Entry<?> entry) {
        return (MacroEntryUniqueAccessor) entry;
    }
}
