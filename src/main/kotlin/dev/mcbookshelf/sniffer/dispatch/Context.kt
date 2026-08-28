package dev.mcbookshelf.sniffer.dispatch

import net.minecraft.commands.CommandSourceStack

/**
 * State passed to every [Handler.handle] call, holding only what varies from one dispatch to the next.
 *
 * Long lived services do not belong here.
 * They are injected into the handlers that need them by `buildHandlers`,
 * which keeps this class from turning into a god object.
 *
 * @property source the command source behind the action, the server one for DAP and the caller one for a command
 * @author theogiraudet
 */
data class Context(val source: CommandSourceStack)
