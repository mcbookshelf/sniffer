package dev.mcbookshelf.sniffer.state

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

/**
 * Holds the current [MinecraftServer] reference for the debugger.
 *
 * Set on `SERVER_STARTED`, cleared on `SERVER_STOPPED`.
 */
object ServerReference {

    private val LOGGER = LoggerFactory.getLogger("sniffer")
    private var server: MinecraftServer? = null

    @JvmStatic
    fun set(server: MinecraftServer?) {
        this.server = server
        LOGGER.debug("Server reference set")
    }

    @JvmStatic
    fun get(): MinecraftServer {
        return server ?: run {
            LOGGER.error("Attempted to get server when it was not set")
            throw IllegalStateException("Server not set")
        }
    }

    @JvmStatic
    fun getCommandSource(): CommandSourceStack = get().createCommandSourceStack()
}
