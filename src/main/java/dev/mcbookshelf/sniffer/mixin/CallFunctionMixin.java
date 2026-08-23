package dev.mcbookshelf.sniffer.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.mcbookshelf.sniffer.accessor.FrameUniqueAccessor;
import dev.mcbookshelf.sniffer.accessor.UnboundUniqueAccessor;
import dev.mcbookshelf.sniffer.state.MacroArgsStore;
import dev.mcbookshelf.sniffer.state.ScopeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on {@link CallFunction} doing the debugger bookkeeping of a function call.
 * It records the called function on the new {@link Frame}, pushes a debug scope,
 * and queues the entry that pops that scope once the function completes.
 *
 * {@code /return} discards the frame and with it that queued entry,
 * so {@link FrameMixin} pops the scope instead, which keeps it to exactly one pop per push.
 *
 * @author theogiraudet
 */
@Mixin(CallFunction.class)
public class CallFunctionMixin<T extends ExecutionCommandSource<T>> {

    @Shadow @Final
    private InstantiatedFunction<T> function;

    /**
     * Injected at the tail, once {@code ContinuationTask.schedule()} has queued the entries of the function.
     * The cleanup entry then lands behind them and fires only when every command has completed.
     */
    @Inject(
        method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V",
        at = @At("TAIL")
    )
    private void afterExecute(
            T sender, ExecutionContext<T> context, Frame frame, CallbackInfo ci,
            @Local(ordinal = 1) Frame newFrame
    ) {
        FrameUniqueAccessor.of(newFrame).setFunction(function);

        String functionId = getFunctionId();
        CompoundTag macroArgs = MacroArgsStore.get(function);
        if (macroArgs != null && sender instanceof CommandSourceStack) {
            ScopeManager.Companion.get().newScope(functionId, sender, macroArgs);
        } else if (sender instanceof CommandSourceStack) {
            ScopeManager.Companion.get().newScope(functionId, sender);
        }

        // popScopeOnce keeps this entry and FrameMixin exclusive, even when vanilla discards the same frame twice.
        context.queueNext(new CommandQueueEntry<>(newFrame, (s, c) -> FrameUniqueAccessor.of(newFrame).popScopeOnce()));
    }

    @Unique
    private String getFunctionId() {
        if (function instanceof PlainTextFunction<?> plainText) {
            var entries = plainText.entries();
            if (!entries.isEmpty()) {
                var first = entries.getFirst();
                if (first instanceof BuildContexts.Unbound<?> unbound) {
                    String sourceFunction = UnboundUniqueAccessor.of(unbound).getSourceFunction();
                    if (sourceFunction != null) return sourceFunction;
                }
            }
            return plainText.id().toString();
        }
        // Unreachable in vanilla, where every InstantiatedFunction is a PlainTextFunction.
        return "unknown";
    }
}
