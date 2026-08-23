package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.execution.Frame
import net.minecraft.commands.functions.InstantiatedFunction

/**
 * Exposes the function a frame runs, and the scope pop that goes with it.
 *
 * @author Alumopper
 * @author theogiraudet
 */
interface FrameUniqueAccessor {
    var function: InstantiatedFunction<*>?

    /**
     * Pops the debug scope pushed for this frame, at most once.
     *
     * Both the cleanup entry of a completed function and the discard of an early return call it,
     * and vanilla can discard the same frame twice, so it has to be idempotent.
     * A frame that never pushed a scope, and therefore has no [function], must not pop at all.
     */
    fun popScopeOnce()

    companion object {
        @JvmStatic
        fun of(frame: Frame): FrameUniqueAccessor = frame as FrameUniqueAccessor
    }
}
