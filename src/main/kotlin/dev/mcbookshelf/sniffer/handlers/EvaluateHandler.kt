package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.EvaluationVariableStore
import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.state.VariableManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.EvaluateInput
import dev.mcbookshelf.sniffer.output.EvaluateOutput
import net.minecraft.commands.CommandSourceStack
import net.minecraft.nbt.CompoundTag

/**
 * Evaluates a debug expression in the current scope.
 *
 * Parses the expression via [VariableManager.evaluate], resolves it against
 * the current scope's executor, and — if the result is a [CompoundTag] —
 * stores the expanded variables in [EvaluationVariableStore] so that
 * subsequent [ResolveVariablesHandler] calls can expand them.
 */
class EvaluateHandler(
    private val scopeManager: ScopeManager,
    private val evaluationStore: EvaluationVariableStore,
) : Handler<EvaluateInput> {

    override val inputType = EvaluateInput::class

    override fun handle(input: EvaluateInput, ctx: Context): Output {
        evaluationStore.clearPrevious(input.expression)

        val parseResult = VariableManager.evaluate(input.expression)
        val debugData = parseResult.getOrElse { ex ->
            return EvaluateOutput(result = ex.message ?: "Expression is invalid", variablesReference = 0)
        }

        val scope = scopeManager.currentScope.orElse(null)
            ?: return EvaluateOutput(result = "Scope is null", variablesReference = 0)

        val source = scope.executor
        if (source !is CommandSourceStack) {
            return EvaluateOutput(result = "Source is not a server command source", variablesReference = 0)
        }

        return try {
            val value = debugData.get(source)
            if (value is CompoundTag) {
                val ref = evaluationStore.peekNextRef()
                val vars = VariableManager.convertNbtCompound("debug", value, ref, true)
                evaluationStore.store(input.expression, vars)
                EvaluateOutput(result = value.toString(), variablesReference = ref)
            } else {
                EvaluateOutput(result = value.toString(), variablesReference = 0)
            }
        } catch (e: Exception) {
            EvaluateOutput(result = e.message ?: "Evaluation error", variablesReference = 0)
        }
    }
}
