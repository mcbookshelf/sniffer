package dev.mcbookshelf.sniffer.watcher

import com.mojang.logging.LogUtils
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import kotlinx.io.IOException
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension

/**
 * Owns the filesystem watchers, one per datapack, that report `.mcfunction` changes to the hot reload.
 *
 * @author Alumopper
 */
object WatcherManager {
    private val LOGGER = LogUtils.getLogger()
    @JvmStatic
    private val WATCHERS = ConcurrentHashMap<String, DirectoryWatcher>()
    @JvmStatic
    private val FUTURES = ConcurrentHashMap<String, CompletableFuture<Void>>()

    @JvmStatic
    @Throws(IOException::class)
    fun start(id: String, root: Path, server: MinecraftServer, callback: (DirectoryChangeEvent) -> Unit): Boolean{
        if(WATCHERS.containsKey(id)){
            return false
        }
        if(Files.notExists(root)){
            return false
        }

        val watcher = DirectoryWatcher.builder()
            .path(root)
            .listener { event ->
                val s = event.path().extension
                if(s == "mcfunction"){
                    server.execute {
                        callback(event)
                    }
                }
            }
            .build()

        WATCHERS[id] = watcher
        val future = watcher.watchAsync()

        future.whenComplete { _, throwable ->
            WATCHERS.remove(id)
            FUTURES.remove(id)
            // [stop] cancels the future, so a cancellation is how a watcher normally ends and the caller has already said so.
            // Anything else ended it on its own, and is the only case worth a log line and a message nobody asked for.
            val failure = throwable?.takeUnless { it is CancellationException } ?: return@whenComplete
            LOGGER.error("Watcher stopped: {}", id, failure)
            server.execute {
                server.playerList.broadcastSystemMessage(
                    Component.literal("[watch:$id] watcher stopped: ${failure.message ?: failure.javaClass.simpleName}"),
                    false
                )
            }
        }

        FUTURES[id] = future
        return true
    }

    @JvmStatic
    fun stop(id: String): Boolean {
        val future = FUTURES.remove(id)
        val watcher = WATCHERS.remove(id)
        future?.cancel(true)
        watcher?.close()
        return future != null && watcher != null
    }

    @JvmStatic
    fun stopAll(){
        for (id in WATCHERS.keys){
            stop(id)
        }
    }
}