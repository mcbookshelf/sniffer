package dev.mcbookshelf.sniffer.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.logging.LogUtils
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import dev.mcbookshelf.sniffer.util.Extension.appendLine
import dev.mcbookshelf.sniffer.ui.StackFormatter
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.server.permissions.Permissions
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors
import org.slf4j.Logger

/**
 * Registers the `/assert` debug command that evaluates an expression
 * and reports whether the assertion passed or failed, including the
 * call stack on failure.
 */
object AssertCommand {

    private val LOGGER: Logger = LogUtils.getLogger()
    @JvmStatic
    fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal<CommandSourceStack?>("assert")
                    .requires{it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}
                    .then(argument("expr", ExprArgumentType())
                        .executes {
                            try{
                                val expr = ExprArgumentType.getExpr(it, "expr")
                                val result = expr.get(it.source)
                                //check result
                                if(result !is ByteTag){
                                    val text = Component.translatable("sniffer.commands.assert.failed.not_a_byte").withStyle { style -> style.withColor(CommonColors.RED) }
                                    when (result) {
                                        is Tag -> text.append(NbtUtils.toPrettyComponent(result))
                                        is Component -> text.append(result)
                                        else -> text.append(result.toString())
                                    }
                                    text.appendLine()
                                    text.appendLine(Component.translatable("sniffer.commands.assert.failed.expression", expr.content))
                                    text.appendLine(Component.translatable("sniffer.commands.assert.failed.stack"))
                                    text.append(StackFormatter.errorStack(10))
                                    it.source.server.playerList.broadcastSystemMessage(text, false)
                                    return@executes 0
                                }
                                if(result.value.toInt() == 0){
                                    val text = Component.translatable("sniffer.commands.assert.failed.result_is_zero").withStyle { style -> style.withColor(CommonColors.RED) }
                                    text.appendLine()
                                    text.appendLine(Component.translatable("sniffer.commands.assert.failed.expression", expr.content))
                                    text.appendLine(Component.translatable("sniffer.commands.assert.failed.stack"))
                                    text.append(StackFormatter.errorStack(10))
                                    it.source.server.playerList.broadcastSystemMessage(text, false)
                                    return@executes 0
                                }
                                it.source.sendSuccess({ Component.translatable("sniffer.commands.assert.passed") }, false)
                                return@executes 1
                            }catch (ex: CommandSyntaxException){
                                LOGGER.error("Exception while execution command:",ex)
                                val text = Component.translatable("sniffer.commands.assert.failed").withStyle() { style -> style.withColor(CommonColors.RED) }
                                text.appendLine(ex.message?.let(Component::literal) ?: Component.translatable("sniffer.commands.assert.failed.unknown_error"))
                                text.appendLine(Component.translatable("sniffer.commands.assert.failed.stack"))
                                text.append(StackFormatter.stack(10))
                                it.source.server.playerList.broadcastSystemMessage(text, false)
                                return@executes 0
                            }

                        }
                    )
            )
        }
    }
}