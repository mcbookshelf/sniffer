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
 * Holds a single suspended [ExecutionContext] while the debugger is paused.
 *
 * The pause path (in `UnboundDebugMixin`) drains the live execution
 * queue and parks it here so the server tick can return immediately —
 * the world keeps running, players can issue commands, and only the
 * paused datapack function is suspended.
 *
 * The resume path replays the stashed entries on the next tick by
 * re-seeding `newTopCommands` and re-entering [ExecutionContext.runCommandQueue].
 *
 * Only one paused session exists at a time. Nested `/function` calls
 * issued by the user while paused get fresh `ExecutionContext`s and are
 * intentionally not debugged (see `UnboundDebugMixin.onExecute`).
 *
 * Note on generics: [ExecutionContext] is parameterized by an F-bounded
 * `ExecutionCommandSource<T>`. We never need to inspect the entries here —
 * we only ferry them between the live deque and our snapshot — so we
 * intentionally erase the type and rely on the fact that every operation
 * is invariant in `T`.
 */
@Suppress("UNCHECKED_CAST")
object PausedExecutionStore {

    private val LOGGER = LoggerFactory.getLogger("sniffer")

    @Volatile
    private var paused: PausedExecution? = null

    /**
     * One-shot flag set just before [ExecutionContext.runCommandQueue] runs
     * during a resume. The first time `UnboundDebugMixin.onExecute` fires
     * after a resume, it consumes this flag and skips its checks — that
     * entry is the line we paused *before*, and its body must run exactly
     * once before any further breakpoint/stepping logic kicks in.
     *
     * Server-thread only; no synchronisation needed.
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

    /** Whether a paused session currently exists. */
    @JvmStatic
    fun isPaused(): Boolean = paused != null

    /**
     * `true` iff [context] is the currently-stashed context. Used by the
     * mixin to skip debug checks for nested executions started while paused.
     */
    @JvmStatic
    fun isStashedContext(context: ExecutionContext<*>): Boolean {
        val p = paused ?: return false
        return p.context === context
    }

    /**
     * Drain [context]'s queues into a new [PausedExecution] and store it.
     * The currently in-flight entry — the one whose `Unbound.execute` is
     * about to run — is **prepended** to the snapshot so that on resume it
     * runs exactly once, picking up the line that was being paused.
     *
     * Called from the mixin on the server thread when a breakpoint or step
     * boundary is hit. The mixin must cancel its inject (`ci.cancel()`)
     * after calling this so the in-flight `Unbound.execute` returns
     * without running its body — the body will run when the entry is
     * replayed on resume.
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
            LOGGER.warn("Stashing a new paused execution while one is already active — dropping the previous one")
        }
        val accessor = accessorOf(context)

        // Snapshot live queues first (the in-flight entry has already been
        // polled, so it's not in either of these). Re-bind the unbound action
        // to the original sender so it can be re-queued as an EntryAction —
        // BuildContexts.Unbound implements UnboundEntryAction, not EntryAction.
        val unbound = inFlightAction as UnboundEntryAction<Any>
        val boundAction: EntryAction<Any> = unbound.bind(sender as Any)
        val drainedQueue = ArrayList<CommandQueueEntry<*>>(accessor.commandQueue.size + 1)
        // The re-queued in-flight entry must be the very first thing replayed.
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
     * Schedule a resume on the next server tick. Pops the stashed
     * execution, re-seeds its queues, re-enters
     * [ExecutionContext.runCommandQueue], and closes the context once the
     * queue drains.
     *
     * Called from [dev.mcbookshelf.sniffer.handlers.ContinueHandler] and
     * [dev.mcbookshelf.sniffer.handlers.StepHandler] on the WebSocket
     * thread; the actual replay runs on the server thread.
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
            // The first entry of drainedQueue is the line we paused before;
            // its body must run exactly once before any further debug check
            // is allowed to fire.
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
     * Drop the stashed execution without replaying it. Closes the
     * underlying context. Called on server shutdown / DAP disconnect.
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

    /**
     * The Kotlin accessor's [of] requires `T : Any` to satisfy
     * [ExecutionContext]'s F-bound. We don't care about `T` at all here,
     * so we cast through a wildcard.
     */
    private fun accessorOf(context: ExecutionContext<*>): ExecutionContextAccessor<Any> =
        context as ExecutionContextAccessor<Any>
}
