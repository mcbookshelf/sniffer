package dev.mcbookshelf.sniffer.mixin;

import com.mojang.brigadier.CommandDispatcher;
import dev.mcbookshelf.sniffer.accessor.MacroFunctionUniqueAccessor;
import dev.mcbookshelf.sniffer.accessor.UnboundUniqueAccessor;
import dev.mcbookshelf.sniffer.state.MacroArgsStore;
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
 * Captures macro arguments when a {@link MacroFunction} is instantiated and
 * stores them in {@link MacroArgsStore} — an external map keyed by the
 * resulting {@link InstantiatedFunction} instance.
 *
 * <p>Also sets source-location info ({@code sourceFunction}, {@code sourceLine})
 * on each entry of the instantiated function, enabling breakpoints and stepping
 * inside macro functions. The line mapping is stored during function parsing
 * (via {@link MacroFunctionUniqueAccessor}) and applied here at instantiation time.
 *
 * <p>The {@link MacroArgsStore} uses a {@code WeakHashMap}, so entries are
 * garbage-collected when the function instance is evicted from the macro LRU cache.
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

    /**
     * After {@code MacroFunction.instantiate()} returns an
     * {@link InstantiatedFunction}, associate the original macro
     * arguments with it in the external store and set source info
     * on each entry for debugger support.
     */
    @Inject(method = "instantiate", at = @At("RETURN"))
    private void captureArgs(
            CompoundTag arguments, CommandDispatcher<T> dispatcher,
            CallbackInfoReturnable<InstantiatedFunction<T>> cir
    ) {
        InstantiatedFunction<T> returnValue = cir.getReturnValue();
        if (returnValue == null || arguments == null) return;

        MacroArgsStore.put(returnValue, arguments);

        // Set source info on instantiated entries so breakpoints and
        // stepping work inside macro functions
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
