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
 * Mixin on {@link CallFunction} that handles debugger bookkeeping when a
 * function is called:
 * <ul>
 *   <li>Stores the function reference on the new {@link Frame} for call-stack tracking</li>
 *   <li>Pushes a debug scope to {@link ScopeManager} (with macro args if present)</li>
 *   <li>Queues a cleanup entry that pops the scope on normal function completion</li>
 * </ul>
 *
 * The cleanup entry is removed by {@link Frame#discard} when {@code /return}
 * is called, so {@link FrameMixin} handles scope popping in that case.
 * The two mechanisms are mutually exclusive: exactly one pop per push.
 */
@Mixin(CallFunction.class)
public class CallFunctionMixin<T extends ExecutionCommandSource<T>> {

    @Shadow @Final
    private InstantiatedFunction<T> function;

    /**
     * Injected at TAIL of {@code CallFunction.execute()} — after
     * {@code ContinuationTask.schedule()} has queued the function entries.
     * This ensures the cleanup entry is queued AFTER the function's commands,
     * so it fires when all commands have completed.
     */
    @Inject(
        method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V",
        at = @At("TAIL")
    )
    private void afterExecute(
            T sender, ExecutionContext<T> context, Frame frame, CallbackInfo ci,
            @Local(ordinal = 1) Frame newFrame
    ) {
        // 1. Store function reference on the new frame (via accessor, no reflection)
        FrameUniqueAccessor.of(newFrame).setFunction(function);

        // 2. Push debug scope
        String functionId = getFunctionId();
        CompoundTag macroArgs = MacroArgsStore.get(function);
        if (macroArgs != null && sender instanceof CommandSourceStack) {
            ScopeManager.Companion.get().newScope(functionId, sender, macroArgs);
        } else if (sender instanceof CommandSourceStack) {
            ScopeManager.Companion.get().newScope(functionId, sender);
        }

        // 3. Queue cleanup entry — pops scope on normal function completion.
        //    If /return calls frame.discard(), this entry is removed (same depth),
        //    and FrameMixin.beforeDiscard() handles the scope pop instead.
        context.queueNext(new CommandQueueEntry<>(newFrame, (s, c) -> ScopeManager.Companion.get().unscope()));
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
        // Fallback — should not happen in practice since all InstantiatedFunctions
        // are PlainTextFunction in vanilla Minecraft.
        return "unknown";
    }
}
