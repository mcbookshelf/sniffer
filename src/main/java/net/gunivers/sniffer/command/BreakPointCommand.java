// TODO(Ravel): Failed to fully resolve file: null
package net.gunivers.sniffer.command;

import com.google.common.collect.Queues;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import kotlin.Pair;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.gunivers.sniffer.dap.DebuggerState;
import net.gunivers.sniffer.dap.ScopeManager;
import net.gunivers.sniffer.util.ReflectUtil;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;

import static net.gunivers.sniffer.util.Utils.addSnifferPrefix;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Main command handler for the datapack debugging system.
 * Provides commands for setting breakpoints, stepping through code, and inspecting variables.
 *
 * @author Alumopper
 * @author theogiraudet
 */
public class BreakPointCommand {

    /** Indicates if a debug command is currently being executed */
    public static boolean isDebugCommand = false;
    /** Indicates if the debugger is currently active */
    public static boolean isDebugging = false;
    /** Controls whether debug mode is enabled */
    public static boolean debugMode = true;
    /** 
     * Number of steps to execute in step mode.
     * When this value is greater than 0, the debugger will continue execution
     * for that many steps before pausing again. This is decremented
     * as each command is executed.
     */
    public static int moveSteps = 0;
    /** 
     * The current stepping mode for the debugger.
     * Controls how the debugger behaves when stepping through code:
     * - STEP_IN: Steps into function calls
     * - STEP_OVER: Executes function calls as a single step
     * - STEP_OUT: Continues execution until returning from the current function
     * @see StepType
     */
    public static StepType stepType = StepType.STEP_IN;
    /** Queue storing command execution contexts for debugging */
    public static final Deque<ExecutionContext<?>> storedCommandExecutionContext = Queues.newArrayDeque();
    /** Logger instance for this class */
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /**
     * Tracks the depth level at which a step operation was initiated.
     * Used by stepOver and stepOut operations to determine when to stop execution.
     * A value of -1 indicates that no depth tracking is active.
     */
    public static int stepDepth = -1;

    /**
     * Initializes the breakpoint command system.
     * Registers all subcommands including:
     * - breakpoint: Sets a breakpoint
     * - continue: Continues execution
     * - step: Steps through code
     * - get: Retrieves variable values
     * - stack: Shows function call stack
     * - run: Executes commands
     * - clear: Clears debug state
     * - on/off: Toggles debug mode
     */
    public static void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("breakpoint")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    if(!debugMode) return 1;
                    var server = context.getSource().getServer();
                    var players = server.getPlayerList().getPlayers();
                    for (var player : players){
                        player.sendSystemMessage(Component.translatable("sniffer.commands.breakpoint.set"));
                    }
                    DebuggerState.get().triggerBreakpoint(context.getSource());
                    return 1;
                })
                .then(literal("step")
                        .executes(context -> {
                            stepType = StepType.STEP_IN;
                            step(1, context.getSource());
                            return 1;
                        })
                        .then(argument("lines", IntegerArgumentType.integer())
                                .executes(context -> {
                                    final int lines = IntegerArgumentType.getInteger(context, "lines");
                                    stepType = StepType.STEP_IN;
                                    step(lines, context.getSource());
                                    return 1;
                                })
                        )
                )
                .then(literal("step_over")
                        .executes(context -> {
                            stepType = StepType.STEP_OVER;
                            step(1, context.getSource());
                            return 1;
                        })
                        .then(argument("lines", IntegerArgumentType.integer())
                                .executes(context -> {
                                    stepType = StepType.STEP_OVER;
                                    final int lines = IntegerArgumentType.getInteger(context, "lines");
                                    step(lines, context.getSource());
                                    return 1;
                                })
                        )
                )
                .then(literal("step_out")
                        .executes(context -> {
                            stepType = StepType.STEP_OUT;
                            step(1, context.getSource());
                            return 1;
                        })
                )
                .then(literal("continue")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable("sniffer.commands.breakpoint.move"), false);
                            continueExec(context.getSource());
                            return 1;
                        })
                )
                .then(literal("get")
                        .then(argument("key", StringArgumentType.string())
                                .suggests(BreakpointSuggestionProvider.INSTANCE)
                                .executes(context -> {
                                    final String key = StringArgumentType.getString(context, "key");
                                    var nbt = getNBT(key, context.getSource());
                                    if(nbt != null){
                                        if(nbt.getSecond()){
                                            context.getSource().sendSuccess(() -> Component.translatable("sniffer.commands.breakpoint.get", key, NbtUtils.toPrettyComponent(nbt.getFirst())), false);
                                        }else {
                                            context.getSource().sendFailure(Component.translatable("sniffer.commands.breakpoint.get.fail.not_macro"));
                                        }
                                    }
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            final var args = getAllNBT(context.getSource());
                            if(args == null){
                                context.getSource().sendFailure(Component.translatable("sniffer.commands.breakpoint.get.fail.not_macro"));
                            }else {
                                context.getSource().sendSuccess(() -> (NbtUtils.toPrettyComponent(args)), false);
                            }
                            return 1;
                        })
                )
                .then(literal("stack")
                        .executes(context -> {
                            final var finalText = getStack();
                            context.getSource().sendSuccess(() -> finalText, false);
                            return 1;
                        })
                )
                .then(literal("run")
                        .redirect(dispatcher.getRoot(), context -> (CommandSourceStack) ScopeManager.get().getCurrentScope().map(ScopeManager.DebugScope::getExecutor).orElse(null))
                )
                .then(literal("clear")
                        .executes(context -> {
                            clear();
                            return 1;
                        })
                )
                .then(literal("on")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable("sniffer.commands.breakpoint.on"), false);
                            debugMode = true;
                            return 1;
                        })
                )
                .then(literal("off")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable("sniffer.commands.breakpoint.off"), false);
                            debugMode = false;
                            return 1;
                        })
                )
        ));
    }

    /**
     * Clears all debugging state and resets the system.
     * This includes clearing breakpoints, command contexts, and function stacks.
     */
    public static void clear(){
        isDebugCommand = false;
        isDebugging = false;
        debugMode = true;
        moveSteps = 0;
        stepType = StepType.STEP_IN;
        stepDepth = -1;
        storedCommandExecutionContext.clear();
    }

    /**
     * Steps through the code execution for a specified number of steps.
     * @param steps Number of steps to execute
     * @param source The command source that triggered the step
     */
    @SuppressWarnings("DataFlowIssue")
    public static void step(int steps, CommandSourceStack source) {
        if (!isDebugging) {
            source.sendFailure(Component.translatable("sniffer.commands.breakpoint.step.fail"));
            return;
        }
        isDebugCommand = true;
        moveSteps = steps;
        ExecutionContext<?> context = null;
        while (moveSteps > 0) {
            context = storedCommandExecutionContext.peekFirst();
            if (context != null) {
                ReflectUtil.invoke(context, "onStep").onFailure(LOGGER::error);
                if (moveSteps != 0) {
                    storedCommandExecutionContext.pollFirst().close();
                }else {
                    var result = (boolean) ReflectUtil.invoke(context, "ifContainsCommandAction").onFailure(LOGGER::error).getData();
                    if(!result){
                        storedCommandExecutionContext.pollFirst().close();
                    }
                    break;
                }
            } else {
                source.sendSuccess(() -> addSnifferPrefix(Component.translatable("sniffer.commands.breakpoint.step.over").withColor(CommonColors.WHITE)), false);
                continueExec(source);
            }
        }
        isDebugCommand = false;
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
            }
        }
    }

    /**
     * Continues execution from the current breakpoint.
     * @param source The command source that triggered the continue
     */
    public static void continueExec(@NotNull CommandSourceStack source) {
        if(!isDebugging){
            source.sendFailure(Component.translatable("sniffer.commands.breakpoint.move.not_debugging"));
            return;
        }
        source.getServer().tickRateManager().setFrozen(false);
        DebuggerState.get().continueExec();
        isDebugging = false;
        moveSteps = 0;
        for (ExecutionContext<?> context : storedCommandExecutionContext) {
            try {
                context.runCommandQueue();
                context.close();
            } catch (Exception e) {
                LOGGER.error(e.toString());
            }
        }
    }

    /**
     * Retrieves the NBT value for a given key from the current context.
     * @param key The key to look up
     * @param source The command source requesting the value
     * @return A pair containing the NBT element and whether it's a macro, or null if not found
     */
    private static @Nullable Pair<Tag, Boolean> getNBT(String key, CommandSourceStack source){
        var context = storedCommandExecutionContext.peekFirst();
        if(context == null){
            return null;
        }
        try {
            //noinspection unchecked
            return (Pair<Tag, Boolean>) ReflectUtil.invoke(context, "getKey", key)
                    .onFailure(LOGGER::error)
                    .getDataOrElse(null);
        }catch (Exception e){
            LOGGER.error(e.toString());
            source.sendFailure(Component.translatable("sniffer.commands.breakpoint.get.fail.error", e.toString()));
            return null;
        }
    }

    /**
     * Retrieves all NBT values from the current context.
     * @param source The command source requesting the values
     * @return The NBT element containing all values, or null if not available
     */
    private static @Nullable Tag getAllNBT(CommandSourceStack source){
        var context = storedCommandExecutionContext.peekFirst();
        if(context == null){
            return null;
        }
        try {
            var cls = context.getClass();
            var method = cls.getDeclaredMethod("getAllNBT");
            method.setAccessible(true);
            return (Tag) method.invoke(context);
        }catch (Exception e){
            LOGGER.error(e.toString());
            source.sendFailure(Component.translatable("sniffer.commands.breakpoint.get.fail.error", e.toString()));
            return null;
        }
    }

    public static MutableComponent getStack(int maxStack){
        int count = 0;
        MutableComponent text = Component.empty();
        var stacks = ScopeManager.get().getDebugScopes();
        for (var stack : stacks) {
            if(count >= maxStack){
                text.append(Component.literal("... (" + (stacks.size() - count) + " more)").withColor(CommonColors.WHITE));
                break;
            }
            var t = Component.literal(stack.getFunction());
            var style = t.getStyle();
            if(stacks.indexOf(stack) == 0){
                style = style.withBold(true).withColor(CommonColors.HIGH_CONTRAST_DIAMOND);
            }else {
                style = style.withBold(false).withColor(CommonColors.WHITE);
            }
            t.setStyle(style);
            text = text.append(t);
            if(stacks.getLast() != stack) text.append("\n");
            count++;
        }
        return text;
    }

    public static Component getStack(){
        MutableComponent text = Component.empty();
        var stacks = ScopeManager.get().getDebugScopes();
        for (var stack : stacks) {
            var t = Component.literal(stack.getFunction());
            var style = t.getStyle();
            if(stacks.indexOf(stack) == 0){
                style = style.withBold(true).withColor(CommonColors.HIGH_CONTRAST_DIAMOND);
            }else {
                style = style.withBold(false).withColor(CommonColors.WHITE);
            }
            t.setStyle(style);
            text = text.append(t);
            text.append("\n");
        }
        return text;
    }


    public static MutableComponent getErrorStack(int maxStack){
        int count = 0;
        var color = TextColor.parseColor("#E4514C").getOrThrow().getValue();
        MutableComponent text = Component.empty();
        var stacks = ScopeManager.get().getDebugScopes();
        for (var stack : stacks) {
            if(count >= maxStack){
                text.append(Component.literal("... (" + (stacks.size() - count) + " more)").withColor(color));
                break;
            }
            var t = Component.literal(stack.getFunction());
            var style = t.getStyle();
            if(stacks.indexOf(stack) == 0){
                style = style.withBold(true).withColor(color);
            }else {
                style = style.withBold(false).withColor(color);
            }
            t.setStyle(style);
            text = text.append(t);
            if(stacks.getLast() != stack) text.append("\n");
            count++;
        }
        return text;
    }

    @SuppressWarnings("unused")
    public static Component getErrorStack(){
        var color = TextColor.parseColor("#E4514C").getOrThrow().getValue();
        MutableComponent text = Component.empty();
        var stacks = ScopeManager.get().getDebugScopes();
        for (var stack : stacks) {
            var t = Component.literal(stack.getFunction());
            var style = t.getStyle();
            if(stacks.indexOf(stack) == 0){
                style = style.withBold(true).withColor(color);
            }else {
                style = style.withBold(false).withColor(color);
            }
            t.setStyle(style);
            text = text.append(t);
            text.append("\n");
        }
        return text;
    }
}
