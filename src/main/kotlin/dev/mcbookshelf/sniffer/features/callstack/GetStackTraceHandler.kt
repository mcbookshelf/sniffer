package dev.mcbookshelf.sniffer.features.callstack

import dev.mcbookshelf.sniffer.features.callstack.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.callstack.GetStackTraceInput
import dev.mcbookshelf.sniffer.features.callstack.StackTraceOutput
import kotlin.math.min

/**
 * Returns a slice of the debug call stack.
 *
 * The top frame is read from the current scope, the one the mixin keeps up to date,
 * while every frame below it stays on the line from which it called the next one.
 *
 * @author theogiraudet
 */
class GetStackTraceHandler(
    private val scopeManager: ScopeManager,
) : Handler<GetStackTraceInput> {

    override val inputType = GetStackTraceInput::class

    override fun handle(input: GetStackTraceInput, ctx: Context): Output {
        val allScopes = scopeManager.debugScopes
        val total = allScopes.size
        val start = input.startFrame.coerceIn(0, total)
        val end = min(start + input.maxLevels, total)

        val frames = allScopes.subList(start, end).mapIndexed { index, scope ->
            val isHead = start + index == 0
            StackFrameData(
                id = scope.id,
                functionName = if (isHead) headFunctionName() else scope.function,
                line = if (isHead) headLine() else scope.line,
                path = scope.path,
            )
        }

        return StackTraceOutput(frames = frames, totalFrames = total)
    }

    private fun headLine(): Int =
        scopeManager.currentScope.map { it.line }.orElse(0)

    private fun headFunctionName(): String =
        scopeManager.currentScope.map { it.function }.orElse("")
}
