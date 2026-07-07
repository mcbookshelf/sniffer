package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.TriggerBreakpointInput
import dev.mcbookshelf.sniffer.output.TriggerBreakpointOutput
import dev.mcbookshelf.sniffer.state.BreakpointManager
import dev.mcbookshelf.sniffer.state.BreakpointTrigger

/**
 * Triggers a breakpoint at the current execution position, which is what `/breakpoint` does.
 *
 * An optional condition command gates the halt, read on its success channel exactly as a DAP breakpoint condition is.
 * Unlike a registered breakpoint, a condition that cannot be run declines the halt rather than forcing it:
 * nothing validated this one when it was written, so the entrypoint is told what was wrong with it instead.
 *
 * @author theogiraudet
 */
class TriggerBreakpointHandler(
    private val breakpointManager: BreakpointManager,
) : Handler<TriggerBreakpointInput> {

    override val inputType = TriggerBreakpointInput::class

    override fun handle(input: TriggerBreakpointInput, ctx: Context): Output {
        if (input.condition != null) {
            val holds = try {
                breakpointManager.runCondition(input.condition, ctx.source)
            } catch (e: Exception) {
                return TriggerBreakpointOutput(triggered = false, error = e.message ?: e.toString())
            }
            if (!holds) return TriggerBreakpointOutput(triggered = false)
        }
        BreakpointTrigger.trigger(ctx.source)
        return TriggerBreakpointOutput(triggered = true)
    }
}
