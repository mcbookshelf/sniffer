package net.gunivers.sniffer.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.gunivers.sniffer.util.ReflectUtil;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin for the Macro class to enhance debugging capabilities for macros.
 * This mixin captures and preserves macro arguments during expansion,
 * allowing the debugger to display macro parameter values during debugging.
 *
 * @param <T> The type of command source being used
 *
 * @author Alumopper
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(MacroFunction.class)
public abstract class MacroFunctionMixin<T extends ExecutionCommandSource<T>> implements MacroFunctionUniqueAccessor {

    @Shadow public abstract ResourceLocation id();

    /** Stores the NBT compound containing macro arguments */
    @Unique private CompoundTag arguments;

    @Override
    public CompoundTag getArguments() {
        return arguments;
    }

    @Override
    public void setArguments(CompoundTag arguments) {
        this.arguments = arguments;
    }

    @Unique
    private ArrayList<String> debugTags = new ArrayList<>();

    @Override
    public ArrayList<String> getDebugTags() {
        return debugTags;
    }

    @Override
    public void setDebugTags(ArrayList<String> debugTags) {
        this.debugTags = debugTags;
    }

    /**
     * Captures the macro arguments when a macro is being expanded.
     * This method is injected at the start of the withMacroReplaced method
     * to store the arguments for later use.
     *
     * @param arguments The NBT compound containing macro arguments
     * @param dispatcher The command dispatcher
     * @param cir The callback info returnable
     */
    @Inject(method = "instantiate",
            at = @At("HEAD"))
    private void onInstantiate(CompoundTag arguments, CommandDispatcher<T> dispatcher, CallbackInfoReturnable<InstantiatedFunction<T>> cir){
        this.arguments = arguments;
    }

/**
     * Injects the captured arguments into the expanded macro.
     * This method is injected at the end of the withMacroReplaced method
     * to ensure the expanded macro has access to the original arguments.
     *
     * @param varNames The list of variable names
     * @param arguments The list of argument values
     * @param dispatcher The command dispatcher
     * @param cir The callback info returnable
     */
    @Inject(method = "substituteAndParse",
            at = @At("RETURN"), cancellable = true)
    private void onSubstituteAndParse(List<String> varNames, List<String> arguments, CommandDispatcher<T> dispatcher, CallbackInfoReturnable<InstantiatedFunction<T>> cir){
        PlainTextFunction<T> function = (PlainTextFunction<T>) cir.getReturnValue();
        ReflectUtil.set(function, "arguments", CompoundTag.class, this.arguments);
        ReflectUtil.set(function, "functionIdentifier", ResourceLocation.class, this.id());
        ReflectUtil.set(function, "originalMacro", MacroFunction.class, this);
        cir.setReturnValue(function);
    }

}

