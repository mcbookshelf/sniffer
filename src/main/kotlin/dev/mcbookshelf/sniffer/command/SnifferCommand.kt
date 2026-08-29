package dev.mcbookshelf.sniffer.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack

/**
 * One of the commands Sniffer adds to the game.
 *
 * An implementation describes its tree and nothing else: registering it and gating it behind the operator
 * level are the same for every command, and belong to [CommandsRegistry].
 *
 * @author theogiraudet
 */
interface SnifferCommand {

    /**
     * The tree of the command, built once per command registration.
     *
     * @param dispatcher the tree being built, which a command redirecting to its root needs
     */
    fun build(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack>
}
