package dev.mcbookshelf.sniffer.accessor

import net.minecraft.commands.execution.CommandQueueEntry
import net.minecraft.commands.execution.ExecutionContext
import java.util.Deque

/**
 * Exposes the internals of [ExecutionContext] the pause and resume paths need,
 * to drain its queues into a snapshot and to seed them back later.
 *
 * @author Alumopper
 * @author theogiraudet
 */
interface ExecutionContextAccessor<T : Any> {
    val commandQueue: Deque<CommandQueueEntry<T>>
    val newTopCommands: MutableList<CommandQueueEntry<T>>
    var currentFrameDepth: Int
    var commandQuota: Int

    /** Whether the context is stashed, in which case closing it does nothing. */
    var isStashed: Boolean

    companion object {
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T : Any> of(context: ExecutionContext<T>): ExecutionContextAccessor<T> =
            context as ExecutionContextAccessor<T>
    }
}
