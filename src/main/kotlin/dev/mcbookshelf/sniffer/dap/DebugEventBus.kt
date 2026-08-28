package dev.mcbookshelf.sniffer.dap

import org.slf4j.LoggerFactory
import java.util.function.BiConsumer

/**
 * Carries the execution state transitions of the debugger to the DAP layer, which is what registers the listeners.
 *
 * Each event holds a single listener and a new registration overwrites the previous one,
 * so a reconnecting client replaces the listeners of the connection it lost.
 *
 * @author theogiraudet
 */
object DebugEventBus {

    private val LOGGER = LoggerFactory.getLogger("sniffer")

    private var stopConsumer: BiConsumer<Int, String>? = null
    private var continueListener: Runnable? = null
    private var shutdownListener: Runnable? = null

    @JvmStatic
    fun onStop(consumer: BiConsumer<Int, String>) {
        stopConsumer = consumer
    }

    @JvmStatic
    fun onContinue(listener: Runnable) {
        continueListener = listener
    }

    @JvmStatic
    fun onShutdown(listener: Runnable) {
        shutdownListener = listener
    }

    @JvmStatic
    fun fireStop(breakpointId: Int, reason: String) {
        try {
            stopConsumer?.accept(breakpointId, reason)
        } catch (e: Exception) {
            LOGGER.warn("Error in stop consumer", e)
        }
    }

    @JvmStatic
    fun fireContinue() {
        try {
            continueListener?.run()
        } catch (e: Exception) {
            LOGGER.warn("Error in continue listener", e)
        }
        LOGGER.debug("Execution continued")
    }

    @JvmStatic
    fun fireShutdown() {
        try {
            shutdownListener?.run()
        } catch (e: Exception) {
            LOGGER.warn("Error in shutdown listener", e)
        }
    }

    @JvmStatic
    fun clear() {
        stopConsumer = null
        continueListener = null
        shutdownListener = null
    }
}
