package net.gunivers.sniffer.mixin;

import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerFunctionManager.class)
public interface ServerFunctionManagerAccessors {

    @Accessor("library")
    ServerFunctionLibrary getLibrary();
}
