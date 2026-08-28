package dev.mcbookshelf.sniffer.mixin;

import com.mojang.brigadier.CommandDispatcher;
import dev.mcbookshelf.sniffer.accessor.MacroFunctionUniqueAccessor;
import dev.mcbookshelf.sniffer.accessor.UnboundUniqueAccessor;
import dev.mcbookshelf.sniffer.features.variables.MacroArgsStore;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Captures the arguments a {@link MacroFunction} is instantiated with into {@link MacroArgsStore},
 * keyed by the resulting {@link InstantiatedFunction}.
 *
 * <p>It also attaches the source location to every entry of that function,
 * which is what makes breakpoints and stepping work inside a macro.
 * The line mapping it needs was computed during parsing and left on the macro.
 *
 * @author Alumopper
 * @author theogiraudet
 */
@Mixin(MacroFunction.class)
public class MacroInstantiationMixin<T extends ExecutionCommandSource<T>> implements MacroFunctionUniqueAccessor {

    @Shadow @Final
    private Identifier id;

    @Unique
    private List<Integer> lineMapping = null;

    @Override
    public List<Integer> getLineMapping() {
        return lineMapping;
    }

    @Override
    public void setLineMapping(List<Integer> lineMapping) {
        this.lineMapping = lineMapping;
    }

    @Inject(method = "instantiate", at = @At("RETURN"))
    private void captureArgs(
            CompoundTag arguments, CommandDispatcher<T> dispatcher,
            CallbackInfoReturnable<InstantiatedFunction<T>> cir
    ) {
        InstantiatedFunction<T> returnValue = cir.getReturnValue();
        if (returnValue == null || arguments == null) return;

        MacroArgsStore.put(returnValue, arguments);

        if (lineMapping == null) return;
        var entries = returnValue.entries();
        String functionId = id.toString();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry instanceof BuildContexts.Unbound<?> unbound) {
                UnboundUniqueAccessor accessor = UnboundUniqueAccessor.of(unbound);
                accessor.setSourceFunction(functionId);
                accessor.setSourceLine(i < lineMapping.size() ? lineMapping.get(i) : -1);
            }
        }
    }
}
