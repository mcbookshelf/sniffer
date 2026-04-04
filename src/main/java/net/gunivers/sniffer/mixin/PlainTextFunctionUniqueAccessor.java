package net.gunivers.sniffer.mixin;

import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public interface PlainTextFunctionUniqueAccessor<T extends ExecutionCommandSource<T>> extends CommandFunctionUniqueAccessors {
    Tag getArguments();
    void setArguments(Tag arguments);
    ResourceLocation getFunctionIdentifier();
    void setFunctionIdentifier(ResourceLocation functionIdentifier);
    MacroFunction<T> getOriginalMacro();
    void setOriginalMacro(MacroFunction<T> originalMacro);

    @SuppressWarnings("unchecked")
    static <T extends ExecutionCommandSource<T>> PlainTextFunctionUniqueAccessor<T> of(PlainTextFunction<T> function) {
        return (PlainTextFunctionUniqueAccessor<T>) (Object) function;
    }
}
