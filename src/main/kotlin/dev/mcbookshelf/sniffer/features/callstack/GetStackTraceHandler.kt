package dev.mcbookshelf.sniffer.features.callstack

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.source.Line
import kotlin.jvm.optionals.getOrNull
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
                identity = scope.identity,
                line = if (isHead) headLine() else scope.line,
            )
        }

        return StackTraceOutput(frames = frames, totalFrames = total)
    }

    private fun headLine(): Line? =
        scopeManager.currentScope.getOrNull()?.line
}
