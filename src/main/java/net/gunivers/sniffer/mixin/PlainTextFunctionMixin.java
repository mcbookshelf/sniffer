package net.gunivers.sniffer.mixin;

import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.gunivers.sniffer.command.FunctionInAction;
import net.gunivers.sniffer.command.FunctionOutAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin for the ExpandedMacro class to add debugging capabilities to macros.
 * This mixin injects function entry and exit actions into macro execution,
 * allowing the debugger to track macro execution in the same way as functions.
 *
 * @param <T> The type of command source being used
 *
 * @author Alumopper
 * @author Wenz-jam
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(PlainTextFunction.class)
public class PlainTextFunctionMixin<T extends ExecutionCommandSource<T>> implements PlainTextFunctionUniqueAccessor<T> {


    /** The list of command actions in this expanded macro */
    @Shadow @Final private List<UnboundEntryAction<T>> entries;

    /** Stores the NBT compound containing macro arguments */
    @Unique
    private Tag arguments;

    @Override
    public Tag getArguments() {
        return arguments;
    }

    @Override
    public void setArguments(Tag arguments) {
        this.arguments = arguments;
    }

    @Unique
    private ResourceLocation functionIdentifier;

    @Override
    public ResourceLocation getFunctionIdentifier() {
        return functionIdentifier;
    }

    @Override
    public void setFunctionIdentifier(ResourceLocation functionIdentifier) {
        this.functionIdentifier = functionIdentifier;
    }

    /** The original macro this function is resolved from */
    @Unique
    private MacroFunction<T> originalMacro;

    @Override
    public MacroFunction<T> getOriginalMacro() {
        return originalMacro;
    }

    @Override
    public void setOriginalMacro(MacroFunction<T> originalMacro) {
        this.originalMacro = originalMacro;
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
     * Injects function entry and exit actions into the macro's command list.
     * This allows the debugger to track when a macro is entered and exited,
     * similar to how it tracks function calls.
     *
     * @param ci Callback info for the injection
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        //noinspection unchecked
        final var THIS = (PlainTextFunction<T>) (Object) this;
        entries.addFirst(new FunctionInAction<>(THIS));
        entries.add(new FunctionOutAction<>(THIS));
        this.functionIdentifier = THIS.id();
    }

}

