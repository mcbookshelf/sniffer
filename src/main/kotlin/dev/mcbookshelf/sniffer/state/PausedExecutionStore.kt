package dev.mcbookshelf.sniffer.state

import dev.mcbookshelf.sniffer.accessor.ExecutionContextAccessor
import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.commands.execution.CommandQueueEntry
import net.minecraft.commands.execution.ExecutionContext
import net.minecraft.commands.execution.EntryAction
import net.minecraft.commands.execution.Frame
import net.minecraft.commands.execution.UnboundEntryAction
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

/**
 * Holds the single suspended [ExecutionContext] that exists while the debugger is paused.
 *
 * The pause path (in `UnboundDebugMixin`) drains the live execution queue and parks it here,
 * so the server tick returns immediately and only the paused datapack function is suspended.
 * The resume path replays the stashed entries on the next tick.
 * Nested `/function` calls issued while paused get their own [ExecutionContext] and are deliberately not debugged.
 *
 * Entries are only ferried between the live deque and the snapshot, never inspected,
 * so the self referential type parameter of [ExecutionContext] is erased throughout.
 *
 * @author theogiraudet
 */
@Suppress("UNCHECKED_CAST")
object PausedExecutionStore {

    private val LOGGER = LoggerFactory.getLogger("sniffer")

    @Volatile
    private var paused: PausedExecution? = null

    /**
     * Consumed by `UnboundDebugMixin` on the first line executed after a resume.
     * That line is the one we paused before, so its body must run once before any further debug check fires.
     * Server thread only, no synchronisation needed.
     */
    @JvmField
    var skipNextCheck: Boolean = false

    /** Snapshot of a suspended [ExecutionContext]. */
    private class PausedExecution(
        val context: ExecutionContext<*>,
        val drainedQueue: List<CommandQueueEntry<*>>,
        val drainedNewTop: List<CommandQueueEntry<*>>,
        val frameDepth: Int,
    )

    @JvmStatic
    fun isPaused(): Boolean = paused != null

    /**
     * Whether [context] is the stashed one.
     * The mixin uses it to skip debug checks for nested executions started while paused.
     */
    @JvmStatic
    fun isStashedContext(context: ExecutionContext<*>): Boolean {
        val p = paused ?: return false
        return p.context === context
    }

    /**
     * Drains the queues of [context] into a snapshot and stores it.
     * The in flight entry, the one whose `Unbound.execute` is about to run,
     * is prepended to the snapshot so that on resume it runs exactly once.
     *
     * Called from the mixin on the server thread when a breakpoint or step boundary is hit.
     * The mixin must cancel its inject right after, so the in flight `Unbound.execute` returns without running its body.
     *
     * @param inFlightAction the action of the entry that was polled but not executed yet
     * @param sender the source [inFlightAction] is bound to when it goes back into the queue
     * @param frame the frame the in flight entry belongs to
     */
    @JvmStatic
    @Synchronized
    fun stash(
        context: ExecutionContext<*>,
        inFlightAction: UnboundEntryAction<*>,
        sender: ExecutionCommandSource<*>,
        frame: Frame,
    ) {
        if (paused != null) {
            LOGGER.warn("Stashing a new paused execution while one is already active, dropping the previous one")
        }
        val accessor = accessorOf(context)

        // BuildContexts.Unbound implements UnboundEntryAction, not EntryAction,
        // so the in flight action has to be bound back to its sender to be queued again.
        val unbound = inFlightAction as UnboundEntryAction<Any>
        val boundAction: EntryAction<Any> = unbound.bind(sender as Any)
        val drainedQueue = ArrayList<CommandQueueEntry<*>>(accessor.commandQueue.size + 1)
        drainedQueue.add(CommandQueueEntry<Any>(frame, boundAction))
        drainedQueue.addAll(accessor.commandQueue)

        val drainedNewTop = ArrayList(accessor.newTopCommands)
        accessor.commandQueue.clear()
        accessor.newTopCommands.clear()

        accessor.isStashed = true

        paused = PausedExecution(
            context = context,
            drainedQueue = drainedQueue,
            drainedNewTop = drainedNewTop,
            frameDepth = accessor.currentFrameDepth,
        )
    }

    /**
     * Schedules a resume on the next server tick.
     * Called from the handlers on the WebSocket thread, while the replay itself runs on the server thread.
     */
    @JvmStatic
    fun scheduleResume(server: MinecraftServer) {
        server.execute { resumeNow() }
    }

    private fun resumeNow() {
        val p = synchronized(this) {
            val current = paused ?: return
            paused = null
            current
        }
        val accessor = accessorOf(p.context)
        try {
            accessor.commandQueue.clear()
            accessor.newTopCommands.clear()
            for (entry in p.drainedQueue) accessor.commandQueue.add(entry as CommandQueueEntry<Any>)
            for (entry in p.drainedNewTop) accessor.newTopCommands.add(entry as CommandQueueEntry<Any>)
            accessor.currentFrameDepth = p.frameDepth
            accessor.isStashed = false
            skipNextCheck = true
            p.context.runCommandQueue()
        } catch (e: Exception) {
            LOGGER.error("Error while resuming paused execution", e)
        } finally {
            try {
                p.context.close()
            } catch (e: Exception) {
                LOGGER.warn("Error closing resumed execution context", e)
            }
        }
    }

    /**
     * Drops the stashed execution without replaying it and closes its context.
     * Called on server shutdown and on DAP disconnect.
     */
    @JvmStatic
    @Synchronized
    fun discard() {
        val p = paused ?: return
        paused = null
        try {
            accessorOf(p.context).isStashed = false
            p.context.close()
        } catch (e: Exception) {
            LOGGER.warn("Error closing discarded paused execution", e)
        }
    }

    /** Casts through a wildcard, since the accessor demands a `T : Any` this class never needs. */
    private fun accessorOf(context: ExecutionContext<*>): ExecutionContextAccessor<Any> =
        context as ExecutionContextAccessor<Any>
}
