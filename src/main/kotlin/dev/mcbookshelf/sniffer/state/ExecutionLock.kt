package dev.mcbookshelf.sniffer.state

import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Thread-blocking mechanism for pausing command execution at breakpoints
 * and step boundaries.
 *
 * Blocks the server thread in-place using a [ReentrantLock] +
 * [java.util.concurrent.locks.Condition]. The execution loop pauses
 * naturally; on continue/step, the DAP handler signals the condition
 * and the loop resumes where it left off.
 *
 * The periodic timeout in [await] guards against server shutdown while
 * blocked — Minecraft's shutdown sequence does not interrupt the server
 * thread, so we poll a [shutdown] flag.
 */
object ExecutionLock {

    private val LOGGER = LoggerFactory.getLogger("sniffer")
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    @Volatile
    private var paused = false

    @Volatile
    private var shutdown = false

    /**
     * Blocks the server thread until [resume] is called.
     *
     * Must be called **after** the caller has already triggered the
     * breakpoint notification (e.g. via [BreakpointTrigger.trigger]).
     * This method only manages the lock — it does not mutate debugger or
     * stepping state.
     */
    @JvmStatic
    fun pauseExecution() {
        lock.lock()
        try {
            paused = true

            while (paused && !shutdown) {
                // Periodic wake-up so the thread can check the shutdown flag.
                condition.await(500, TimeUnit.MILLISECONDS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            LOGGER.warn("Execution lock interrupted", e)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Called from DAP handlers (continue, step-in, step-over, step-out)
     * on the WebSocket thread. Wakes the blocked server thread so command
     * execution can resume.
     */
    @JvmStatic
    fun resume() {
        lock.lock()
        try {
            paused = false
            condition.signal()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Called on server shutdown to unblock the server thread if it is
     * currently paused. Called during server shutdown.
     */
    @JvmStatic
    fun forceRelease() {
        lock.lock()
        try {
            shutdown = true
            paused = false
            condition.signal()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Resets the lock state for a fresh server lifecycle.
     * Called on `SERVER_STARTED`.
     */
    @JvmStatic
    fun reset() {
        lock.lock()
        try {
            shutdown = false
            paused = false
        } finally {
            lock.unlock()
        }
    }

    /** Whether the server thread is currently blocked. */
    @JvmStatic
    fun isPaused(): Boolean = paused
}
