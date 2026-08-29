package dev.mcbookshelf.sniffer.dap

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.IInput
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import java.util.concurrent.CompletableFuture

/**
 * What every [DapService] needs to answer a request: the server thread, and the dispatcher.
 *
 * @author theogiraudet
 */
object DapDispatch {

    /**
     * Runs [block] on the Minecraft server thread and completes the returned future with its result.
     * DAP requests arrive on WebSocket threads and the world keeps ticking while the debugger is paused,
     * so everything reading live game state has to go through here.
     */
    fun <T> onServerThread(block: () -> T): CompletableFuture<T> {
        val server = runCatching { ServerReference.get() }.getOrNull()
            ?: return CompletableFuture.failedFuture(IllegalStateException("Minecraft server not available"))
        // MinecraftServer is an Executor running submitted tasks on the server thread, or inline if already on it.
        return CompletableFuture.supplyAsync({ block() }, server)
    }

    /** Dispatches [input] with the command source of the server itself. */
    fun dispatch(input: IInput): Output =
        SnifferDispatcher.get().dispatch(input, Context(ServerReference.getCommandSource()))
}
