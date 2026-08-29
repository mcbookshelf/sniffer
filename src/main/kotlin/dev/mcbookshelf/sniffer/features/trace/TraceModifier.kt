package dev.mcbookshelf.sniffer.features.trace

import com.mojang.brigadier.context.ContextChain
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.execution.ChainModifiers
import net.minecraft.commands.execution.CustomModifierExecutor
import net.minecraft.commands.execution.ExecutionControl
import net.minecraft.commands.execution.tasks.BuildContexts
import dev.mcbookshelf.sniffer.chat.SnifferChat

/**
 * Opens the trace, hands the rest of the command to vanilla, and queues the entry that closes it.
 *
 * The closing entry rides the command queue behind the traced command, so a breakpoint that stashes the
 * execution stashes it too and it runs on resume, at the moment the trace is really over.
 *
 * @author theogiraudet
 */
object TraceModifier : CustomModifierExecutor.ModifierAdapter<CommandSourceStack> {

    override fun apply(
        originalSource: CommandSourceStack,
        currentSources: List<CommandSourceStack>,
        currentStep: ContextChain<CommandSourceStack>,
        modifiers: ChainModifiers,
        output: ExecutionControl<CommandSourceStack>,
    ) {
        val traced = currentStep.nextStage()
        // Everything from the traced command to the end of the line, ranges being absolute over the whole input.
        val command = currentStep.topContext.input.substring(traced.topContext.range.start)

        if (!callsAFunction(traced)) {
            SnifferChat.fail(originalSource, "sniffer.commands.trace.no_function")
            return
        }

        val refusal = when (dispatch(originalSource, command)) {
            is AlreadyTracing -> "already_tracing"
            is NoClientAttached -> "no_client"
            else -> null
        }
        if (refusal != null) {
            SnifferChat.fail(originalSource, "sniffer.commands.trace.$refusal")
            return
        }

        output.queueNext(
            BuildContexts.Continuation(
                currentStep.topContext.input, traced, modifiers, originalSource, currentSources,
            )
        )
        output.queueNext { _, _ -> dispatch(originalSource, command = null) }
    }

    /**
     * Whether the traced command can enter a function at all, which is the only thing a trace has to show.
     * The `function` literal is looked for anywhere in the chain rather than at its head, since `/execute`
     * reaches one through as many stages as it likes.
     */
    private fun callsAFunction(traced: ContextChain<CommandSourceStack>): Boolean {
        var stage: ContextChain<CommandSourceStack>? = traced
        while (stage != null) {
            if (stage.topContext.nodes.any { it.node.name == FUNCTION_LITERAL }) return true
            stage = stage.nextStage()
        }
        return false
    }

    private fun dispatch(source: CommandSourceStack, command: String?) =
        SnifferDispatcher.get().dispatch(TraceInput(command != null, command), Context(source))

    private const val FUNCTION_LITERAL = "function"
}
