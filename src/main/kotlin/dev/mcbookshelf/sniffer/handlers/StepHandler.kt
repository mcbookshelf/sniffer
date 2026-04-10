package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.state.StepType
import dev.mcbookshelf.sniffer.state.ExecutionLock
import dev.mcbookshelf.sniffer.output.Ack
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dispatch.StepInput
import dev.mcbookshelf.sniffer.state.SteppingState
import net.minecraft.network.chat.Component

/**
 * Base handler for all step actions (step-in, step-over, step-out).
 *
 * Each concrete subclass declares its [inputType] and stepping [policy].
 * The handler sets [SteppingState] fields and signals [ExecutionLock] to
 * wake the blocked server thread. The mixin ([UnboundDebugMixin]) reads
 * these fields to decide when to re-pause.
 *
 * No reflection, no queue draining — just set state and signal.
 *
 * @param I the concrete [StepInput] subtype this handler routes.
 * @property policy the [StepType] applied when this handler runs.
 */
abstract class StepHandler<I : StepInput>(
    private val policy: StepType,
) : Handler<I> {

    override fun handle(input: I, ctx: Context): Output {
        if (!SteppingState.isDebugging) {
            ctx.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.step.fail"))
            return Ack
        }

        // Configure stepping state for the mixin to read
        SteppingState.stepType = policy
        SteppingState.stepsRemaining = input.lines
        // Record the current scope depth so the mixin can compare
        SteppingState.stepDepth = ScopeManager.get().count()

        // Unfreeze tick rate and wake the blocked server thread
        ctx.source.server.tickRateManager().setFrozen(false)
        ExecutionLock.resume()

        return Ack
    }
}
