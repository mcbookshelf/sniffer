package dev.mcbookshelf.sniffer.util

import dev.mcbookshelf.sniffer.mixin.CommandsAccessor

/**
 * Runs commands outside the execution context vanilla keeps for the thread currently running commands.
 *
 * `Commands.executeCommandInContext` only builds a fresh context when that thread local is empty,
 * and otherwise queues the command into the running one, where it would run long after the caller returned.
 * Clearing it for the duration of the call is what makes the run synchronous and isolated from the debugged execution.
 *
 * @author theogiraudet
 */
object IsolatedExecution {

    /**
     * Runs [block] with the thread local execution context cleared, and restores it afterwards.
     * It is restored rather than cleared, since the caller may itself be running inside one.
     */
    @JvmStatic
    fun <T> outsideCurrentContext(block: () -> T): T {
        val holder = CommandsAccessor.getCurrentExecutionContext()
        val outer = holder.get()
        holder.set(null)
        return try {
            block()
        } finally {
            holder.set(outer)
        }
    }
}
