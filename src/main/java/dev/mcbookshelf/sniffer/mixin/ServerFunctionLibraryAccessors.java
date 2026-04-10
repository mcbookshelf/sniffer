package dev.mcbookshelf.sniffer.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.permissions.PermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ServerFunctionLibrary.class)
public interface ServerFunctionLibraryAccessors {

    @Accessor("functions")
    Map<Identifier, CommandFunction<CommandSourceStack>> getFunctions();

    @Accessor("functions")
    void setFunctions(Map<Identifier, CommandFunction<CommandSourceStack>> functions);

    @Accessor("dispatcher")
    CommandDispatcher<CommandSourceStack> getDispatcher();

    @Accessor("functionCompilationPermissions")
    PermissionSet getFunctionCompilationPermissions();
}
