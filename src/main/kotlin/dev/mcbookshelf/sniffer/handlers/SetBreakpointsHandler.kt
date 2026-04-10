package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.SetBreakpointsInput
import dev.mcbookshelf.sniffer.output.BreakpointResult
import dev.mcbookshelf.sniffer.output.SetBreakpointsOutput
import dev.mcbookshelf.sniffer.state.BreakpointManager

/**
 * Replaces all breakpoints for a file with the requested set.
 *
 * Clears existing breakpoints for the file, then registers each requested
 * line. Returns verification results so the entrypoint can report which
 * breakpoints were successfully resolved to a Minecraft function path.
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
