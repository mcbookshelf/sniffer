package dev.mcbookshelf.sniffer.dispatch

import dev.mcbookshelf.sniffer.handlers.buildHandlers

/**
 * Process wide holder of the single [Dispatcher], and how the entrypoints reach it.
 * Splitting [init] from [get] keeps the initialisation point explicit instead of hiding it behind a lazy.
 *
 * @author theogiraudet
 */
object SnifferDispatcher {

    private var instance: Dispatcher? = null

    /** Builds the dispatcher and its handlers, on `SERVER_STARTED`. Safe to call more than once. */
    @JvmStatic
    fun init() {
        instance = Dispatcher(buildHandlers())
    }

    /** @throws IllegalStateException if [init] has not been called yet */
    @JvmStatic
    fun get(): Dispatcher =
        instance ?: error("SnifferDispatcher not initialized, call init() on SERVER_STARTED first")
}
