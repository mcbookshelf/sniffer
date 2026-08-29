package dev.mcbookshelf.sniffer.features.trace

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.Permissions

/**
 * The `/trace run <command>` command, which traces the command it redirects to.
 *
 * @author theogiraudet
 */
object TraceCommand {

    @JvmStatic
    fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("trace")
                    .requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                    // forward rather than fork: the traced command keeps its own result and error handling.
                    .then(Commands.literal("run").forward(dispatcher.root, TraceModifier, false))
            )
        }
    }
}
