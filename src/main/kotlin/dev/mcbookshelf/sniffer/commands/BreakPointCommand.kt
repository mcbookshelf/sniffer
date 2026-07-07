package dev.mcbookshelf.sniffer.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import dev.mcbookshelf.sniffer.state.ScopeManager
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.IInput
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.input.ContinueInput
import dev.mcbookshelf.sniffer.input.GetAllVariablesInput
import dev.mcbookshelf.sniffer.input.GetStackInput
import dev.mcbookshelf.sniffer.input.GetVariableInput
import dev.mcbookshelf.sniffer.input.ResetSteppingInput
import dev.mcbookshelf.sniffer.input.StepInInput
import dev.mcbookshelf.sniffer.input.StepOutInput
import dev.mcbookshelf.sniffer.input.StepOverInput
import dev.mcbookshelf.sniffer.input.TriggerBreakpointInput
import dev.mcbookshelf.sniffer.output.AllVariablesOutput
import dev.mcbookshelf.sniffer.output.StackOutput
import dev.mcbookshelf.sniffer.output.TriggerBreakpointOutput
import dev.mcbookshelf.sniffer.output.VariableOutput
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.nbt.NbtUtils
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions

/**
 * The `/breakpoint` command tree.
 *
 * It is a translator and holds no debugger logic: arguments become inputs, they go through the dispatcher,
 * and the output comes back as chat feedback.
 *
 * @author Alumopper
 * @author theogiraudet
 */
object BreakPointCommand {

    @JvmStatic
    fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("breakpoint")
                    .requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                    .executes { context -> triggerBreakpoint(context.source) }
                    .then(
                        // The condition is a command read on its success channel, the same thing a DAP breakpoint condition is.
                        Commands.argument("condition", StringArgumentType.greedyString())
                            .suggests(ConditionSuggestionProvider)
                            .executes { context ->
                                triggerBreakpoint(context.source, StringArgumentType.getString(context, "condition"))
                            }
                    )
                    .then(
                        Commands.literal("step")
                            .executes { dispatch(StepInInput(1), it.source); 1 }
                            .then(
                                Commands.argument("lines", IntegerArgumentType.integer())
                                    .executes { dispatch(StepInInput(IntegerArgumentType.getInteger(it, "lines")), it.source); 1 }
                            )
                    )
                    .then(
                        Commands.literal("step_over")
                            .executes { dispatch(StepOverInput(1), it.source); 1 }
                            .then(
                                Commands.argument("lines", IntegerArgumentType.integer())
                                    .executes { dispatch(StepOverInput(IntegerArgumentType.getInteger(it, "lines")), it.source); 1 }
                            )
                    )
                    .then(
                        Commands.literal("step_out")
                            .executes { dispatch(StepOutInput(1), it.source); 1 }
                    )
                    .then(
                        Commands.literal("continue")
                            .executes {
                                it.source.sendSuccess({ Component.translatable("sniffer.commands.breakpoint.move") }, false)
                                dispatch(ContinueInput, it.source)
                                1
                            }
                    )
                    .then(
                        Commands.literal("get")
                            .then(
                                Commands.argument("key", StringArgumentType.string())
                                    .suggests(BreakpointSuggestionProvider)
                                    .executes { context ->
                                        val key = StringArgumentType.getString(context, "key")
                                        val output = dispatch(GetVariableInput(key), context.source) as VariableOutput
                                        when {
                                            output.error != null ->
                                                context.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.get.fail.error", output.error))
                                            output.value != null && output.isMacro ->
                                                context.source.sendSuccess({ Component.translatable("sniffer.commands.breakpoint.get", key, NbtUtils.toPrettyComponent(output.value)) }, false)
                                            output.value != null ->
                                                context.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.get.fail.not_macro"))
                                        }
                                        1
                                    }
                            )
                            .executes { context ->
                                val output = dispatch(GetAllVariablesInput, context.source) as AllVariablesOutput
                                when {
                                    output.error != null ->
                                        context.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.get.fail.error", output.error))
                                    output.value == null ->
                                        context.source.sendFailure(Component.translatable("sniffer.commands.breakpoint.get.fail.not_macro"))
                                    else ->
                                        context.source.sendSuccess({ NbtUtils.toPrettyComponent(output.value) }, false)
                                }
                                1
                            }
                    )
                    .then(
                        Commands.literal("stack")
                            .executes {
                                val output = dispatch(GetStackInput, it.source) as StackOutput
                                it.source.sendSuccess({ output.stack }, false)
                                1
                            }
                    )
                    .then(
                        Commands.literal("run")
                            .redirect(dispatcher.root) { context ->
                                @Suppress("UNCHECKED_CAST")
                                ScopeManager.get().currentScope.map { it.executor }.orElse(null) as? CommandSourceStack
                            }
                    )
                    .then(
                        Commands.literal("clear")
                            .executes { dispatch(ResetSteppingInput, it.source); 1 }
                    )
            )
        }
    }

    /**
     * Dispatches [TriggerBreakpointInput] and announces the halt to every player, if there was one.
     *
     * A condition that simply failed says nothing, so `#!breakpoint <command>` in a hot function does not spam.
     * One that could not be run at all is reported to the caller, since nothing validated it beforehand.
     */
    private fun triggerBreakpoint(source: CommandSourceStack, condition: String? = null): Int {
        val output = dispatch(TriggerBreakpointInput(condition), source) as TriggerBreakpointOutput
        if (output.error != null) {
            source.sendFailure(Component.translatable("sniffer.commands.breakpoint.condition.error", output.error))
            return 0
        }
        if (!output.triggered) return 0
        for (player in source.server.playerList.players) {
            player.sendSystemMessage(Component.translatable("sniffer.commands.breakpoint.set"))
        }
        return 1
    }

    private fun dispatch(input: IInput, source: CommandSourceStack): Output =
        SnifferDispatcher.get().dispatch(input, Context(source, source.server))
}