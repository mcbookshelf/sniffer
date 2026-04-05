package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.GetStackTraceInput
import dev.mcbookshelf.sniffer.output.StackFrameData
import dev.mcbookshelf.sniffer.output.StackTraceOutput
import kotlin.math.min

/**
 * Returns a paginated slice of the debug call stack as domain objects.
 *
 * The head frame's line and name are taken from [ScopeManager.getCurrentScope]
 * because the mixin updates the current scope's line on pause, while
 * deeper frames retain the line at which they called the next function.
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
