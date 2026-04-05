package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.output.Ack
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.ContinueInput
import dev.mcbookshelf.sniffer.state.DebugEventBus
import dev.mcbookshelf.sniffer.state.ExecutionLock
import dev.mcbookshelf.sniffer.state.SteppingState
import net.minecraft.network.chat.Component

/**
 * Resumes execution from the current pause point until the next breakpoint.
 *
 * Unfreezes the tick-rate manager, notifies DAP clients via [DebugEventBus],
 * and signals [ExecutionLock] to wake the blocked server thread. The thread
 * resumes the execution loop naturally — no queue draining needed.
 */
class ContinueHandler : Handler<ContinueInput> {

    override val inputType = ContinueInput::class

    override fun handle(input: ContinueInput, ctx: Context): Output {
        if (!SteppingState.isDebugging) {
            ctx.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.move.not_debugging"))
            return Ack
        }

        // Clear debugging state — the mixin will not pause until the next breakpoint
        SteppingState.isDebugging = false
        SteppingState.stepsRemaining = 0

        // Unfreeze and notify DAP
        ctx.source.server.tickRateManager().setFrozen(false)
        DebugEventBus.fireContinue()

        // Wake the blocked server thread
        ExecutionLock.resume()

        return Ack
    }
}
