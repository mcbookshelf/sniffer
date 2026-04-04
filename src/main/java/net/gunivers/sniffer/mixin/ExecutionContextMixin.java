package net.gunivers.sniffer.mixin;

import kotlin.Pair;
import net.gunivers.sniffer.command.FunctionInAction;
import net.gunivers.sniffer.util.ReflectUtil;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.*;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.ContinuationTask;
import net.minecraft.commands.execution.tasks.ExecuteCommand;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.profiling.Profiler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.gunivers.sniffer.command.FunctionOutAction;
import net.gunivers.sniffer.dap.DebuggerState;
import net.gunivers.sniffer.dap.ScopeManager;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static net.gunivers.sniffer.command.BreakPointCommand.*;
import static net.gunivers.sniffer.command.StepType.*;

/**
 * Mixin class that extends CommandExecutionContext to add debugging capabilities.
 * This class provides functionality for:
 * - Stepping through command execution
 * - Inspecting variables and NBT data
 * - Managing command queue during debugging
 * - Handling function calls and returns
 *
 * @param <T> The type of command source being used
 *
 * @author Alumopper
 * @author theogiraudet
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(ExecutionContext.class)
abstract public class ExecutionContextMixin<T> implements ExecutionContextUniqueAccessor {

// Shadowed fields from the original class
    @Shadow @Final private static Logger LOGGER;

    @Shadow @Final private int commandLimit;

    @Shadow @Final private Profiler profiler;

    @Shadow private int commandQuota;

    @Shadow private boolean queueOverflow;

    @Shadow @Final private Deque<CommandQueueEntry<T>> commandQueue;

    @Shadow @Final private List<CommandQueueEntry<T>> newTopCommands;

    @Shadow private int currentFrameDepth;

    @Shadow protected abstract void pushNewCommands();

    @SuppressWarnings("DataFlowIssue")
    @Shadow @NotNull private static <T extends ExecutionCommandSource<T>> Frame createTopFrame(ExecutionContext<T> context, CommandResultCallback returnValueConsumer){return null;}

    /**
     * Holds the next command that will be executed in the current context.
     * This field helps track the execution flow for debugging purposes.
     */
    @Unique private UnboundEntryAction<?> nextCommand;

    @Override
    public UnboundEntryAction<?> getNextCommand() {
        return nextCommand;
    }

    @Override
    public void setNextCommand(UnboundEntryAction<?> nextCommand) {
        this.nextCommand = nextCommand;
    }

    /**
     * Indicates whether the current command is the last one.
     * Used to optimize step operations, particularly for stepOut functionality.
     */
    @Unique private boolean isLastCommand;

    @Override
    public boolean getIsLastCommand() {
        return isLastCommand;
    }

    @Override
    public void setIsLastCommand(boolean isLastCommand) {
        this.isLastCommand = isLastCommand;
    }

    @SuppressWarnings("unchecked")
    @Unique private ExecutionContext<T> getThis(){
        return (ExecutionContext<T>) (Object)this;
    }

/**
     * Injects code to handle procedure calls during debugging.
     * This method ensures proper frame setup for function calls.
     *
     * @param context The command execution context
     * @param procedure The procedure being called
     * @param source The command source
     * @param returnValueConsumer The consumer for return values
     * @param ci The callback info
     */
    @Inject(method = "queueInitialFunctionCall", at = @At("HEAD"), cancellable = true)
    private static <T extends ExecutionCommandSource<T>> void queueInitialFunctionCall(
            ExecutionContext<T> context, InstantiatedFunction<T> procedure, T source, CommandResultCallback returnValueConsumer, CallbackInfo ci
    ) {
        // Create a new frame for the procedure call
        Frame frame = createTopFrame(context, returnValueConsumer);
        ReflectUtil.set(frame, "function", InstantiatedFunction.class, procedure)
                .onFailure(e -> LOGGER.error("Failed to set function in frame for procedure call: {}", e)
        );
        // Add the command to the queue with the modified frame
        context.queueNext(
                new CommandQueueEntry<>(frame, new CallFunction<>(procedure, source.callback(), false).bind(source))
        );
        ci.cancel();
    }

    @Unique
    private void sendOverflowMessage(){
        LOGGER.info("Command execution stopped due to limit (executed {} commands)", this.commandLimit);
        MutableComponent text = Component.literal("Command execution stopped due to limit (executed " + this.commandLimit + " commands)")
                .withColor(TextColor.parseColor("red").getOrThrow().getValue());
        text.append("\n");
        text.append("Stack trace:").append("\n");
        text.append(getErrorStack(10));
        MinecraftServer server = null;
        var executor = ScopeManager.get().getDebugScopes().getFirst().getExecutor();
        if(executor instanceof CommandSourceStack source){
            server = source.getServer();
        }
        LOGGER.error(text.getString());
        if(server != null){
            server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(text));
        }
    }

/**
     * Injects code to handle command execution during debugging.
     * This method manages the command queue and handles breakpoints.
     *
     * @param ci The callback info
     */
    @Inject(method = "runCommandQueue", at = @At("HEAD"), cancellable = true)
    private void onRunCommandQueue(CallbackInfo ci){
        final var THIS = getThis();

        // Process pending commands before starting the main loop
        this.pushNewCommands();

        while (true) {
            // Check if we've hit the command execution limit
            if (this.commandQuota <= 0) {
                sendOverflowMessage();
                break;
            }

            // Get the next command from the queue
            CommandQueueEntry<T> commandQueueEntry = this.commandQueue.pollFirst();

            if (commandQueueEntry == null) {
                ci.cancel();
                return;
            }

            boolean isDapBreakpoint = processBreakpointForEntry(commandQueueEntry);

            // If we're debugging and the command is inside a function (depth > 0)
            // and we're not stepping through code, pause execution
            if(isDebugging && (commandQueueEntry.frame().depth() != 0 && moveSteps == 0 || isDapBreakpoint)) {
                pauseExecution(commandQueueEntry, THIS);
                ci.cancel();
                return;
            }

            // Update the current depth and execute the command
            executeCommandEntry(commandQueueEntry, THIS);
            
            // Check for queue overflow
            if (this.queueOverflow) {
                sendOverflowMessage();
                break;
            }

            // Process any new commands that were added during execution
            this.pushNewCommands();
        }

        this.currentFrameDepth = 0;
        ci.cancel();
    }

    /**
     * Handles stepping through command execution.
     * This method manages the command queue and handles breakpoints during step-by-step execution.
     * It implements the core stepping logic that controls the execution flow during debugging.
     * This includes:
     * - Processing commands one at a time
     * - Checking for breakpoints and pause conditions
     * - Updating debug state based on step type (stepIn, stepOver, stepOut)
     * - Managing command execution depth
     */
    @Unique
    private void onStep() {
        final var THIS = getThis();

        // Process pending commands before starting the step
        this.pushNewCommands();

        while (true) {
            // If we are in step over mode, update the stepOverDepth if needed
            updateStepDepthIfNeeded();

            // Check command execution limits
            if (this.commandQuota <= 0) {
                LOGGER.info("Command execution stopped due to limit (executed {} commands)", this.commandLimit);
                break;
            }

            // Get the next command from the queue
            CommandQueueEntry<T> commandQueueEntry = this.commandQueue.pollFirst();

            if (commandQueueEntry == null) {
                return;
            }

            this.getNextCommand(commandQueueEntry).ifPresent(command -> this.nextCommand = command);

            var shouldPause = mustPause(commandQueueEntry);

            // If we're debugging and the command is inside a function
            // and we're not stepping through code, pause execution
            if(shouldPause) {
                DebuggerState.get().stop("step");
                moveSteps = 0;
                pauseExecution(commandQueueEntry, THIS);
                resetStepTypeIfNeeded();
                return;
            }

            // Update debugging state based on step over conditions
            updateDebuggingState(commandQueueEntry);

            // Execute the command
            executeCommandEntry(commandQueueEntry, THIS);
            
            // Check for queue overflow
            if (this.queueOverflow) {
                LOGGER.error("Command execution stopped due to command queue overflow (max {})", 10000000);
                break;
            }

            // Process any new commands that were added during execution
            this.pushNewCommands();
        }

        this.currentFrameDepth = 0;
    }

    /**
     * Updates the step depth if needed.
     * This method tracks the depth level at which stepping operations were initiated.
     * For stepOver and stepOut operations, we need to remember the depth at which
     * they were triggered to correctly determine when to pause execution.
     */
    @Unique
    private void updateStepDepthIfNeeded() {
        if(!isStepIn() && (stepDepth == -1 || isDebugging)) {
            stepDepth = this.currentFrameDepth;
        }
    }

    /**
     * Updates the debugging state based on step conditions.
     * This method determines whether debugging should be active based on:
     * - The current stepping mode (stepIn, stepOver, stepOut)
     * - The current depth relative to the step depth
     * - Whether we're at the last command of a function
     *
     * @param commandQueueEntry The current command entry
     */
    @Unique
    private void updateDebuggingState(CommandQueueEntry<T> commandQueueEntry) {
        // When we are in step over, we only activate the debug mode if we are in the same depth level than the one where the step over has been triggered
        // If the debug mode is already activate and the previous condition is false, then we keep the debug mode since this means we find a nested breakpoint
        isDebugging = (isStepOver() && this.currentFrameDepth >= commandQueueEntry.frame().depth() && this.currentFrameDepth == stepDepth)
                || (isStepOut() && (isLastCommand || (this.currentFrameDepth > commandQueueEntry.frame().depth())))
                || (isStepIn() && isDebugging);
    }

    /**
     * Executes a command entry and updates the current depth.
     * This method is responsible for the actual execution of commands during debugging.
     * It updates the current depth based on the frame depth and then executes the command.
     *
     * @param commandQueueEntry The command entry to execute
     * @param context The command execution context
     */
    @Unique
    private void executeCommandEntry(CommandQueueEntry<T> commandQueueEntry, ExecutionContext<T> context) {
        this.currentFrameDepth = commandQueueEntry.frame().depth();
        commandQueueEntry.execute(context);
    }

    /**
     * Resets step type state if needed.
     * This method handles the cleanup of stepping-related state after a step operation
     * has completed. For non-stepIn operations, it resets the step type and depth.
     */
    @Unique
    private void resetStepTypeIfNeeded() {
        if(!isStepIn()) {
            stepType = STEP_IN;
            stepDepth = -1;
        }
    }

    /**
     * Pauses execution by returning the command to the queue and storing the context.
     * 
     * @param commandQueueEntry The command entry to pause
     * @param context The command execution context
     */
    @Unique
    private void pauseExecution(CommandQueueEntry<T> commandQueueEntry, ExecutionContext<T> context) {
        // Put the command back in the queue
        commandQueue.addFirst(commandQueueEntry);
        // Store the current context if not already stored
        if(storedCommandExecutionContext.peekFirst() != context) {
            storedCommandExecutionContext.addFirst(context);
        }
    }

    /**
     * Processes breakpoint information for a command entry.
     * This method checks if a breakpoint is set at the current line in the current function.
     * If a breakpoint is found, it triggers the debugger to stop.
     * 
     * @param commandQueueEntry The command entry to process
     * @return true if a breakpoint was triggered, false otherwise
     */
    @Unique
    private boolean processBreakpointForEntry(CommandQueueEntry<T> commandQueueEntry) {
        if(commandQueueEntry.frame().depth() <= 0) {
            return false;
        }

        var function = this.getNextCommand(commandQueueEntry);

        var lineOpt = function.flatMap(fun -> {
            if(fun instanceof BuildContexts.Unbound<?> unbound) {
                return Optional.of(UnboundUniqueAccessor.of(unbound).getSourceLine());
            }else{
                return Optional.empty();
            }
        });
        if (lineOpt.isEmpty()) {
            return false;
        }

        var funcId = getExpandedMacroFromFrame(commandQueueEntry.frame()).id();

        var line = (int) lineOpt.get();
        boolean isDapBreakpoint = DebuggerState.get().mustStop(funcId.toString(), line);
        ScopeManager.get().getCurrentScope().ifPresent(scope -> scope.setLine(line));
        
        if (isDapBreakpoint) {
            DebuggerState.get().triggerBreakpoint(DebuggerState.get().getServer().createCommandSourceStack());
        }
        
        return isDapBreakpoint;
    }

    /**
     * Determines whether execution should pause at the current command.
     * This method checks various conditions to decide if debugging should stop:
     * - If we're in a debugging state and inside a function
     * - If the command is not a function entry/exit action
     * - If we've used up all our move steps
     * 
     * @param commandQueueEntry The command entry to check
     * @return true if execution should pause, false otherwise
     */
    @Unique
    private boolean mustPause(CommandQueueEntry<T> commandQueueEntry) {
        var shouldPause = isDebugging && commandQueueEntry.frame().depth() != 0;

        var nextCommandOpt = Optional.ofNullable(this.nextCommand);

        if(commandQueueEntry.frame().depth() > 0 && nextCommandOpt.isPresent()) {
            var nextCommand = nextCommandOpt.get();
            if(nextCommand instanceof FunctionOutAction<?> || nextCommand instanceof FunctionInAction<?>) {
                shouldPause = false;
            } else {
                int line = ((UnboundUniqueAccessor) nextCommand).getSourceLine();
                ScopeManager.get().getCurrentScope().ifPresent(scope -> scope.setLine(line));
            }
        }

        // For stepOut, if this is the last command of the function, we can optimize
        // by ensuring the debugger will stop after this command
        if (isLastCommand && commandQueueEntry.frame().depth() <= 1) {
            // Quickly reduce moveSteps to stop after this last command execution
            moveSteps = 1; // Will be reduced to 0 during execution
            if(isStepOut()) moveSteps++;
        }

        return shouldPause && moveSteps == 0;
    }

    /**
     * Gets the next command to be executed from a command queue entry.
     * This method also sets the isLastCommand flag if the next command 
     * is the last one in the function.
     *
     * @param commandQueueEntry The command queue entry to process
     * @return An Optional containing the next command, or empty if not found
     */
    @Unique
    private Optional<UnboundEntryAction<?>> getNextCommand(CommandQueueEntry<T> commandQueueEntry) {
        var function = getExpandedMacroFromFrame(commandQueueEntry.frame());
        if(function == null) {
            return Optional.empty();
        }

        if (!(commandQueueEntry.action() instanceof ContinuationTask<?, ?> steppedAction)) {
            return Optional.empty();
        }

        int index = ((ContinuationTaskAccessors) steppedAction).getIndex();
        if(index < 0) {
            return Optional.empty();
        }

        this.isLastCommand = index + 1 >= function.entries().size() && commandQueueEntry.frame().depth() <= 1;

        return Optional.ofNullable(function.entries().get(index));
    }

    /**
     * Gets the expanded macro from a frame using reflection.
     * 
     * @param frame The frame to get the macro from
     * @return The expanded macro, or null if an error occurred
     */
    @Unique
    private PlainTextFunction<?> getExpandedMacroFromFrame(Frame frame) {
        return ReflectUtil.getT(frame, "function", PlainTextFunction.class).onFailure(LOGGER::error).getDataOrElse(null);
    }

    /**
     * Retrieves the expanded macro and its arguments from a command queue entry.
     * This method extracts function information and arguments from a command entry.
     *
     * @param commandQueueEntry The command queue entry
     * @return A pair containing the expanded macro and its arguments, or null if not available
     */
    @SuppressWarnings({"unchecked"})
    @Unique
    private Pair<PlainTextFunction<T>, CompoundTag> getMacroAndArgsFromEntry(CommandQueueEntry<T> commandQueueEntry) {
        if (commandQueueEntry == null) {
            return null;
        }

        var frame = commandQueueEntry.frame();
        try {
            var function = ReflectUtil.getT(frame, "function", PlainTextFunction.class).onFailure(LOGGER::error).getDataOrElse(null);
            
            if (function == null) {
                return null;
            }

            var args = ReflectUtil.getT(function, "arguments", CompoundTag.class).onFailure(LOGGER::error).getDataOrElse(null);

            return new Pair<PlainTextFunction<T>, CompoundTag>(function, args);
        } catch (Exception e) {
            LOGGER.error("Failed to get macro and args: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves the NBT value for a given key from the current command context.
     * This method looks up a specific key in the arguments of the current function.
     * 
     * @param key The key to look up
     * @return A pair containing the NBT element and whether it's a macro, or null if not found
     */
    @Unique
    private Pair<Tag, Boolean> getKey(String key) {
        CommandQueueEntry<T> commandQueueEntry = this.commandQueue.peekFirst();
        
        var macroAndArgs = getMacroAndArgsFromEntry(commandQueueEntry);
        if (macroAndArgs == null) {
            return null;
        }
        
        var args = macroAndArgs.getSecond();
        // Return null if no arguments are available
        if (args == null) {
            return new Pair<>(null, false);
        }
        
        // Return the requested NBT value and indicate it's from a macro
        return new Pair<>(args.get(key), true);
    }

    /**
     * Retrieves all NBT values from the current command context.
     * This method returns all arguments of the current function as an NBT compound.
     * 
     * @return The NBT compound containing all values, or null if not available
     */
    @Unique
    private Tag getAllNBT() {
        CommandQueueEntry<T> commandQueueEntry = this.commandQueue.peekFirst();
        
        var macroAndArgs = getMacroAndArgsFromEntry(commandQueueEntry);
        if (macroAndArgs == null) {
            return null;
        }
        
        return macroAndArgs.getSecond();
    }

    /**
     * Retrieves all available keys from the current command context.
     * This method gets a list of all argument keys in the current function.
     * 
     * @return A list of all available keys, or null if no NBT data is available
     */
    @Unique
    private List<String> getKeys() {
        CommandQueueEntry<T> commandQueueEntry = this.commandQueue.peekFirst();
        
        var macroAndArgs = getMacroAndArgsFromEntry(commandQueueEntry);
        if (macroAndArgs == null) {
            return null;
        }
        
        var args = macroAndArgs.getSecond();
        if (args != null) {
            return args.keySet().stream().toList();
        } else {
            return null;
        }
    }

    /**
     * Checks if a command action is a fixed command action.
     * This method uses reflection to examine the fields of the action.
     * 
     * @param action The command action to check
     * @return true if the action is a fixed command action, false otherwise
     */
    @Unique
    private boolean isFixCommandAction(@NotNull EntryAction<T> action) {
        // Only check sourced command actions
        if (!(action instanceof UnboundEntryAction)) return false;
        try {
            // Use reflection to check all fields of the action
            Class<?> actionClass = action.getClass();
            Field[] fields = actionClass.getDeclaredFields();

            // Look for a field that contains a FixedCommandAction
            for (Field field : fields) {
                field.setAccessible(true);
                Object fieldValue = field.get(action);
                if (fieldValue instanceof ExecuteCommand) {
                    return true;
                }
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("Failed to check if action is fix command action: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Checks if the current command context contains a command action.
     * This is a simple helper method that checks if the command queue has any entries.
     * 
     * @return true if a command action is present, false otherwise
     */
    @Unique
    private boolean ifContainsCommandAction() {
        // Simply check if there are any commands in the queue
        return this.commandQueue.peekFirst() != null;
    }
}

