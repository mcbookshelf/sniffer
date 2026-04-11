package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.execution.CommandQueueEntry
import net.minecraft.commands.execution.ExecutionContext
import java.util.Deque

/**
 * Exposes private internals of [ExecutionContext] needed by the
 * pause/resume drain-and-stash flow. The pause path snapshots and
 * clears the queues; the resume path re-seeds them and re-enters
 * [ExecutionContext.runCommandQueue].
 */
interface ExecutionContextAccessor<T : Any> {
    val commandQueue: Deque<CommandQueueEntry<T>>
    val newTopCommands: MutableList<CommandQueueEntry<T>>
    var currentFrameDepth: Int
    var commandQuota: Int

    /** Marks the context as stashed; while true the close() mixin no-ops. */
    var isStashed: Boolean

    companion object {
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T : Any> of(context: ExecutionContext<T>): ExecutionContextAccessor<T> =
            context as ExecutionContextAccessor<T>
    }
}
