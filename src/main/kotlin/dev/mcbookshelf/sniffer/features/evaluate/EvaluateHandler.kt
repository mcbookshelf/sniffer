package dev.mcbookshelf.sniffer.features.evaluate

import dev.mcbookshelf.sniffer.features.variables.NbtVariableBuilder
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.features.variables.VariableManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import net.minecraft.nbt.CompoundTag

/**
 * Evaluates a debug expression against the source a command would run as.
 *
 * A [CompoundTag] result is registered as a variable subtree, so the client can expand it afterwards.
 * The [EvaluationSession] remembers that subtree, and evaluating the same expression again drops it first.
 *
 * @author theogiraudet
 */
class EvaluateHandler(
    private val scopeManager: ScopeManager,
    private val evaluationSession: EvaluationSession,
) : Handler<EvaluateInput> {

    override val inputType = EvaluateInput::class

    override fun handle(input: EvaluateInput, ctx: Context): Output {
        evaluationSession.clearPrevious(input.expression)

        val parseResult = VariableManager.evaluate(input.expression)
        val debugData = parseResult.getOrElse { ex ->
            return EvaluateOutput(result = ex.message ?: "Expression is invalid", variablesReference = 0)
        }

        // The same source a command would run as, so an expression reads what a command would see, whether the
        // debugger is stopped in a scope or only attached to a player.
        val source = scopeManager.commandSource(ctx.source)

        return try {
            val value = debugData.get(source)
            if (value is CompoundTag) {
                val node = NbtVariableBuilder.build("debug", value, isRoot = true, registry = scopeManager.registry)
                evaluationSession.store(input.expression, node)
                EvaluateOutput(result = value.toString(), variablesReference = node.id)
            } else {
                EvaluateOutput(result = value.toString(), variablesReference = 0)
            }
        } catch (e: Exception) {
            EvaluateOutput(result = e.message ?: "Evaluation error", variablesReference = 0)
        }
    }
}
