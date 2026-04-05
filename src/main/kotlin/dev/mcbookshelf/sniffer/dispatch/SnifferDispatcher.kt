package dev.mcbookshelf.sniffer.dispatch

import dev.mcbookshelf.sniffer.handlers.buildHandlers

/**
 * Process-wide holder for the single [Dispatcher] instance.
 *
 * Java entrypoints reach the dispatcher through this object. The [init]/[get]
 * split keeps the init point explicit rather than hiding it behind a lazy.
 *
 * Usage:
 *  - call [init] exactly once, on `SERVER_STARTED`, after services are ready.
 *  - call [get] from entrypoints to obtain the dispatcher for `dispatch(...)`.
 */
object SnifferDispatcher {

    private var instance: Dispatcher? = null

    /** Build the dispatcher with all wired handlers. Safe to call more than once. */
    @JvmStatic
    fun init() {
        instance = Dispatcher(buildHandlers())
    }

    /** @throws IllegalStateException if [init] has not been called. */
    @JvmStatic
    fun get(): Dispatcher =
        instance ?: error("SnifferDispatcher not initialized — call init() on SERVER_STARTED first")
}
