package net.gunivers.sniffer.mixin;

import net.minecraft.commands.functions.CommandFunction;

import java.util.ArrayList;

public interface CommandFunctionUniqueAccessors {
    ArrayList<String> getDebugTags();
    void setDebugTags(ArrayList<String> debugTags);

    static CommandFunctionUniqueAccessors of(CommandFunction<?> function){
        return (CommandFunctionUniqueAccessors) function;
    }
}
