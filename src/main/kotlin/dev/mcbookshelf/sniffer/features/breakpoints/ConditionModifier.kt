package dev.mcbookshelf.sniffer.features.breakpoints

import com.mojang.brigadier.context.ContextChain
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.execution.ChainModifiers
import net.minecraft.commands.execution.CustomModifierExecutor
import net.minecraft.commands.execution.ExecutionControl
import net.minecraft.commands.execution.tasks.BuildContexts
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the rest of `/breakpoint if` as a command, with the output of the caller suppressed,
 * and halts only if it reported success.
 *
 * @author theogiraudet
 */
object ConditionModifier : CustomModifierExecutor.ModifierAdapter<CommandSourceStack> {

    override fun apply(
        originalSource: CommandSourceStack,
        currentSources: List<CommandSourceStack>,
        currentStep: ContextChain<CommandSourceStack>,
        modifiers: ChainModifiers,
        output: ExecutionControl<CommandSourceStack>,
    ) {
        val succeeded = AtomicBoolean(false)
        val conditionSources = currentSources.map { source ->
            source.withSuppressedOutput()
                .withCallback { success, _ -> if (success) succeeded.set(true) }
        }

        output.queueNext(
            BuildContexts.Continuation(
                currentStep.topContext.input, currentStep.nextStage(), modifiers, originalSource, conditionSources,
            )
        )
        output.queueNext { _, _ -> if (succeeded.get()) BreakPointCommand.triggerBreakpoint(originalSource) }
    }
}
