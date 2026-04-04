package net.gunivers.sniffer.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionLibrary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ServerFunctionLibrary.class)
public interface ServerFunctionLibraryAccessors {
    @Accessor("functions")
    Map<ResourceLocation, CommandFunction<CommandSourceStack>> getFunctions();

    @Accessor("functions")
    void setFunctions(Map<ResourceLocation, CommandFunction<CommandSourceStack>> functions);

    @Accessor("dispatcher")
    CommandDispatcher<CommandSourceStack> getDispatcher();

    @Accessor("functionCompilationLevel")
    int getFunctionCompilationLevel();
}
