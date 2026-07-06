package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.execution.Frame
import net.minecraft.commands.functions.InstantiatedFunction

interface FrameUniqueAccessor {
    var function: InstantiatedFunction<*>?

    /**
     * Pops the debug scope pushed for this frame, at most once.
     *
     * Called both by the cleanup entry queued in `CallFunctionMixin` (normal completion) and by `Frame.discard` (early return).
     * Vanilla can discard the same frame twice (`/return run function` + fallthrough), so the pop must be idempotent,
     * and frames that never pushed a scope (no [function]) must not pop at all.
     */
    fun popScopeOnce()

    companion object {
        @JvmStatic
        fun of(frame: Frame): FrameUniqueAccessor = frame as FrameUniqueAccessor
    }
}
