package dev.mcbookshelf.sniffer.dispatch

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer

/**
 * State passed to every [Handler.handle] call, holding only what varies from one dispatch to the next.
 *
 * Long lived services do not belong here.
 * They are injected into the handlers that need them by `buildHandlers`,
 * which keeps this class from turning into a god object.
 *
 * @property source the command source behind the action, the server one for DAP and the caller one for a command
 * @property server the running server, passed explicitly rather than fetched statically to keep handlers testable
 * @author theogiraudet
 */
data class Context(
    val source: CommandSourceStack,
    val server: MinecraftServer,
)
