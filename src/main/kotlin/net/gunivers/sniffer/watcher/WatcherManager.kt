package net.gunivers.sniffer.watcher

import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import kotlinx.io.IOException
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension

object WatcherManager {
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
            if (throwable != null) {
                WATCHERS.remove(id)
                FUTURES.remove(id)
                server.execute {
                    server.playerList.broadcastSystemMessage(
                        Component.literal("[watch:$id] watcher stopped: " + throwable.message),
                        false
                    )
                }
                throwable.printStackTrace()
            }else {
                WATCHERS.remove(id)
                WATCHERS.remove(id)
                FUTURES.remove(id)
                server.execute {
                    server.playerList.broadcastSystemMessage(
                        Component.literal("[watch:$id] watcher stopped"),
                        false
                    )
                }
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