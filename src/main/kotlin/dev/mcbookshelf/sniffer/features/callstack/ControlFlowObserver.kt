package dev.mcbookshelf.sniffer.features.callstack

import net.minecraft.commands.execution.ExecutionContext

/**
 * Follows the control flow of the debugged executions, as [ScopeManager] sees it.
 *
 * An observer is told of every call and every return, of any execution, for as long as it stays registered
 * through [ScopeManager.observe]. Nothing filters by execution, so an observer interested in one of them
 * has to tell it apart itself, which is what [onExecutionComplete] hands it the context for.
 *
 * The calls all land on the server thread, in the middle of the execution they describe, so an observer
 * must do little and must not block: waiting on an editor here holds the game.
 *
 * @author theogiraudet
 */
interface ControlFlowObserver {

    /** A function has been entered, and [scope] is the frame it pushed. */
    fun onNewScope(scope: DebugScope)

    /** The function of [scope] has returned, normally or through `/return`, and its frame is being popped. */
    fun onUnscope(scope: DebugScope)

    /**
     * The whole call hierarchy has been thrown away, so nothing that was being followed still stands.
     * It is what a disconnect and a server stop do, and no return is reported for the frames it drops.
     */
    fun onClear()

    /**
     * [context] is over, whether it drained to its last entry, was dropped, or was cut short by a top level
     * `/return` throwing its queue away.
     * It fires when the context is closed for good, which is the only signal an empty scope stack cannot give,
     * since a fork empties it once per branch.
     * The context is named because several run in a tick, and an observer following one of them has no other
     * way to tell that the one it cares about has ended.
     */
    fun onExecutionComplete(context: ExecutionContext<*>)
}
