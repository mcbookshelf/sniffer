package dev.mcbookshelf.sniffer.state

import org.slf4j.LoggerFactory
import java.util.function.BiConsumer

/**
 * Typed event bus for debugger execution-state transitions.
 *
 * Listeners are registered by the DAP layer ([dev.mcbookshelf.sniffer.dap.DapServer])
 * and fired by handlers/state objects when the debugger stops, continues, or shuts down.
 *
 * Uses a single-listener model: each registration overwrites the previous one,
 * so reconnecting a DAP client naturally replaces stale listeners from the old connection.
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
