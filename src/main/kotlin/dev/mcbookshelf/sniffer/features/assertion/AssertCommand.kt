package dev.mcbookshelf.sniffer.features.assertion

import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.logging.LogUtils
import dev.mcbookshelf.sniffer.util.Extension.appendLine
import dev.mcbookshelf.sniffer.features.callstack.StackFormatter
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.CommonColors
import org.slf4j.Logger
import dev.mcbookshelf.sniffer.expression.ExprArgumentType
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.mcbookshelf.sniffer.command.SnifferCommand

/**
 * The `/assert` command, which evaluates an expression and reports the call stack when it does not hold.
 *
 * @author Alumopper
 * @author theogiraudet
 */
object AssertCommand : SnifferCommand {

    private val LOGGER: Logger = LogUtils.getLogger()

    /**
     * Signals a failed assertion, carrying the same message that was broadcast.
     *
     * The failure has to leave by an exception rather than by returning 0,
     * because vanilla derives the success of a command from whether it returned at all.
     * Returning 0 would make `execute store success` and `execute if` read a failed assertion as a passing one.
     */
    private val ASSERT_FAILED = DynamicCommandExceptionType { it as Component }

    override fun build(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal<CommandSourceStack>("assert")
            .then(argument("expr", ExprArgumentType())
                .executes { ctx ->
                    val failure = failureOf(ctx)
                    if (failure == null) {
                        ctx.source.sendSuccess({ Component.translatable("sniffer.commands.assert.passed") }, false)
                        return@executes 1
                    }
                    // Broadcast as well as thrown: a command that fails inside a function reports nowhere a player would look.
                    ctx.source.server.playerList.broadcastSystemMessage(failure, false)
                    throw ASSERT_FAILED.create(failure)
                }
            )

    /** Why the assertion did not hold, or null if it did. */
    private fun failureOf(ctx: CommandContext<CommandSourceStack>): Component? {
        val expr = ExprArgumentType.getExpr(ctx, "expr")
        val result = try {
            expr.get(ctx.source)
        } catch (ex: CommandSyntaxException) {
            LOGGER.error("Exception while execution command:", ex)
            return failed("sniffer.commands.assert.failed")
                .appendLine(ex.message?.let(Component::literal) ?: Component.translatable("sniffer.commands.assert.failed.unknown_error"))
                .appendLine(Component.translatable("sniffer.commands.assert.failed.stack"))
                .append(StackFormatter.stack(10))
        }

        if (result is ByteTag && result.value.toInt() != 0) return null

        val text = if (result is ByteTag) {
            failed("sniffer.commands.assert.failed.result_is_zero")
        } else {
            failed("sniffer.commands.assert.failed.not_a_byte").append(
                when (result) {
                    is Tag -> NbtUtils.toPrettyComponent(result)
                    is Component -> result
                    else -> Component.literal(result.toString())
                }
            )
        }
        return text.appendLine()
            .appendLine(Component.translatable("sniffer.commands.assert.failed.expression", expr.content))
            .appendLine(Component.translatable("sniffer.commands.assert.failed.stack"))
            .append(StackFormatter.errorStack(10))
    }

    private fun failed(key: String): MutableComponent =
        Component.translatable(key).withStyle { style -> style.withColor(CommonColors.RED) }
}
