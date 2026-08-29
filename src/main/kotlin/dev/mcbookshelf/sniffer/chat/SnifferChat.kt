package dev.mcbookshelf.sniffer.chat

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player

/**
 * The one way Sniffer writes to the game chat.
 *
 * Everything the mod says carries the same `[Sniffer]` prefix, which is what tells a reader that a line in a
 * busy chat comes from the debugger rather than from their own datapack.
 * Going through here rather than through [CommandSourceStack.sendSuccess] and friends is what keeps that true
 * of a message added later.
 *
 * @author theogiraudet
 */
object SnifferChat {

    private const val PREFIX = "[Sniffer] "

    /**
     * Answers the source of a command.
     *
     * @param informAdmins whether the other operators are told too, as vanilla does for a command that changes
     *   something they would want to know about
     */
    fun reply(source: CommandSourceStack, message: Component, informAdmins: Boolean = false) =
        source.sendSuccess({ prefixed(message) }, informAdmins)

    fun reply(source: CommandSourceStack, key: String, vararg args: Any, informAdmins: Boolean = false) =
        reply(source, Component.translatable(key, *args), informAdmins)

    /** Answers the source of a command with a failure, which Minecraft renders in red and never logs to admins. */
    fun fail(source: CommandSourceStack, message: Component) =
        source.sendFailure(prefixed(message))

    fun fail(source: CommandSourceStack, key: String, vararg args: Any) =
        fail(source, Component.translatable(key, *args))

    /** Tells every player, for what happens outside of a command anyone asked for. */
    fun broadcast(server: MinecraftServer, message: Component) =
        server.playerList.broadcastSystemMessage(prefixed(message), false)

    fun broadcast(server: MinecraftServer, key: String, vararg args: Any) =
        broadcast(server, Component.translatable(key, *args))

    /** Tells one player, for what concerns them alone. Takes a [Player] so the client side can say things too. */
    fun tell(player: Player, message: Component) =
        player.sendSystemMessage(prefixed(message))

    fun tell(player: Player, key: String, vararg args: Any) =
        tell(player, Component.translatable(key, *args))

    /**
     * The message as it is written to chat.
     * Public for the one case that cannot send: a command exception, which Brigadier carries and renders itself.
     */
    fun prefixed(message: Component): Component =
        Component.literal(PREFIX).withStyle(ChatFormatting.AQUA).append(message)
}
