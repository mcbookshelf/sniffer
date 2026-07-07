package dev.mcbookshelf.sniffer.handlers

import com.mojang.brigadier.exceptions.CommandSyntaxException
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.SetBreakpointsInput
import dev.mcbookshelf.sniffer.output.BreakpointResult
import dev.mcbookshelf.sniffer.output.SetBreakpointsOutput
import dev.mcbookshelf.sniffer.state.BreakpointManager

/**
 * Replaces the breakpoints of a file with the requested ones.
 * The result says which lines could be mapped to a function, so the client can mark the others as unverified.
 *
 * Conditions are commands, validated once here rather than at the moment the breakpoint is hit.
 * One that does not parse leaves its breakpoint unverified with an explanatory message instead of being registered.
 *
 * @author theogiraudet
 */
class SetBreakpointsHandler(
    private val breakpointManager: BreakpointManager,
) : Handler<SetBreakpointsInput> {

    override val inputType = SetBreakpointsInput::class

    override fun handle(input: SetBreakpointsInput, ctx: Context): Output {
        breakpointManager.clearBreakpoints(input.filePath)

        val results = input.breakpoints.map { spec ->
            val condition = try {
                spec.condition?.takeIf { it.isNotBlank() }?.let { breakpointManager.parseCondition(it, ctx.source) }
            } catch (e: CommandSyntaxException) {
                return@map BreakpointResult(
                    line = spec.line,
                    id = null,
                    verified = false,
                    message = "Invalid condition: ${e.message}",
                )
            }
            val idOpt = breakpointManager.addBreakpoint(input.filePath, spec.line, condition)
            BreakpointResult(
                line = spec.line,
                id = idOpt.orElse(null),
                verified = idOpt.isPresent,
            )
        }

        return SetBreakpointsOutput(results)
    }
}
