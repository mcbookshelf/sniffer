package net.gunivers.sniffer.mixin;

import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.nbt.CompoundTag;

public interface MacroFunctionUniqueAccessor extends CommandFunctionUniqueAccessors {
    CompoundTag getArguments();
    void setArguments(CompoundTag arguments);

    static MacroFunctionUniqueAccessor of(MacroFunction<?> function) {
        return (MacroFunctionUniqueAccessor) function;
    }
}
