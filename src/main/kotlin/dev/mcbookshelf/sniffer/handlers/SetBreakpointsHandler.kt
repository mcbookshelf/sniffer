package dev.mcbookshelf.sniffer.handlers

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
 * @author theogiraudet
 */
class SetBreakpointsHandler(
    private val breakpointManager: BreakpointManager,
) : Handler<SetBreakpointsInput> {

    override val inputType = SetBreakpointsInput::class

    override fun handle(input: SetBreakpointsInput, ctx: Context): Output {
        breakpointManager.clearBreakpoints(input.filePath)

        val results = input.lines.map { line ->
            val idOpt = breakpointManager.addBreakpoint(input.filePath, line)
            BreakpointResult(
                line = line,
                id = idOpt.orElse(null),
                verified = idOpt.isPresent,
            )
        }

        return SetBreakpointsOutput(results)
    }
}
