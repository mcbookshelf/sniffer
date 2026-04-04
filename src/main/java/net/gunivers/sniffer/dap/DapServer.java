// TODO(Ravel): Failed to fully resolve file: null
// TODO(Ravel): Failed to fully resolve file: null
// TODO(Ravel): Failed to fully resolve file: null
package net.gunivers.sniffer.dap;

import net.gunivers.sniffer.command.StepType;
import net.gunivers.sniffer.debugcmd.DebugData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.gunivers.sniffer.command.BreakPointCommand;
import net.gunivers.sniffer.command.FunctionTextLoader;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static net.gunivers.sniffer.util.Utils.addSnifferPrefix;
import static net.gunivers.sniffer.command.BreakPointCommand.continueExec;

/**
 * A DAP (Debug Adapter Protocol) server implementation using LSP4J.
 * Provides debugging capabilities for Minecraft datapacks.
 *
 * @author theogiraudet
 */
public class DapServer implements IDebugProtocolServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("sniffer");

    // Constants for messages
    private static final String ATTACHED_MESSAGE = "Attached to VSCode!";
    private static final String DISCONNECTED_MESSAGE = "Disconnected from VSCode.";
    private static final String BREAKPOINT_DESCRIPTION = "Breakpoint reached";
    private static final String MAIN_THREAD_NAME = "Main Thread";

    // Default values for stack trace parameters
    private static final int DEFAULT_START_FRAME = 0;
    private static final int DEFAULT_MAX_LEVELS = 1000;
    private static final int DEFAULT_EXIT_CODE = 0;

    /** The main debugger state that tracks execution and breakpoints */
    private final DebuggerState debuggerState = DebuggerState.get();
    /** Manager for variable scopes during debugging */
    private final ScopeManager scopeManager = ScopeManager.get();
    /** The client connected to this debug server */
    private IDebugProtocolClient client;

    /**
     * Creates a new DAP server instance and sets up the necessary event handlers.
     * Registers callbacks for stop and continue events from the debugger state.
     */
    public DapServer() {
        debuggerState.onStop(this::onStop);
        debuggerState.onContinue(this::onContinue);
        debuggerState.onShutdown(this::exit);
    }

    // ===== Lifecycle Methods =====

    /**
     * Called during the initialization phase.
     * Sets the supported capabilities and registers event handlers.
     */
    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        LOGGER.debug("Initialize request received with arguments: {}", args);

        Capabilities capabilities = new Capabilities();
        capabilities.setSupportsConfigurationDoneRequest(true);
        // capabilities.setSupportsBreakpointLocationsRequest(true);

        LOGGER.debug("Sending capabilities response: {}", capabilities);
        return CompletableFuture.completedFuture(capabilities).thenApply(capabilities1 -> {
            LOGGER.debug("Sending initialized event");
            if (client != null) {
                client.initialized();
            } else {
                LOGGER.warn("Client is null during initialize, couldn't send initialized event");
            }
            return capabilities1;
        });
    }

    /**
     * Handles the launch request.
     * In a real adapter, you would start your debuggee process here.
     */
    @Override
    public CompletableFuture<Void> launch(Map<String, Object> args) {
        LOGGER.debug("Launch request received with arguments: {}", args);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles the attach request.
     * Notifies Minecraft players that a debugging session has started.
     */
    @Override
    public CompletableFuture<Void> attach(Map<String, Object> args) {
        LOGGER.debug("Attach request received with arguments: {}", args);

        sendMessageToAllPlayers(ATTACHED_MESSAGE);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles the disconnect request.
     * Notifies Minecraft players that the debugging session has ended.
     */
    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args) {
        LOGGER.debug("Disconnect request received with arguments: {}", args);

        sendMessageToAllPlayers(DISCONNECTED_MESSAGE);
        continueExec(DebuggerState.get().getCommandSource());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles configurationDone request.
     * Called when the client signals that configuration is complete.
     */
    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        LOGGER.debug("ConfigurationDone request received with arguments: {}", args);
        return CompletableFuture.completedFuture(null);
    }

    // ===== Breakpoint Methods =====

    /**
     * Handles setting breakpoints.
     * Maps VS Code breakpoints to Minecraft function locations.
     */
    @Override
    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
        LOGGER.debug("SetBreakpoints request received with arguments: {}", args);

        if (args == null || args.getSource() == null || args.getSource().getPath() == null) {
            LOGGER.warn("Received invalid SetBreakpoints request with null arguments");
            return CompletableFuture.completedFuture(new SetBreakpointsResponse());
        }

        String path = args.getSource().getPath();
        var state = debuggerState;
        state.clearBreakpoints(path);

        List<Breakpoint> breakpoints = new ArrayList<>(args.getBreakpoints().length);
        for(SourceBreakpoint sourceBreakpoint : args.getBreakpoints()) {
            breakpoints.add(createBreakpoint(path, sourceBreakpoint, state));
        }

        SetBreakpointsResponse response = new SetBreakpointsResponse();
        response.setBreakpoints(breakpoints.toArray(Breakpoint[]::new));

        LOGGER.debug("Sending SetBreakpoints response: {}", response);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Creates a DAP Breakpoint from a source breakpoint.
     */
    private Breakpoint createBreakpoint(String path, SourceBreakpoint sourceBreakpoint, DebuggerState state) {
        // Store the line as 0-indexed (convert from 1-indexed)
        var uuidOpt = state.registerBreakpoint(path, sourceBreakpoint.getLine() - 1);

        var dapBreakpoint = new Breakpoint();
        dapBreakpoint.setLine(sourceBreakpoint.getLine());
        dapBreakpoint.setVerified(uuidOpt.isPresent());
        uuidOpt.ifPresent(dapBreakpoint::setId);

        if(uuidOpt.isEmpty()) {
            dapBreakpoint.setReason(BreakpointNotVerifiedReason.FAILED);
        }

        return dapBreakpoint;
    }

    /**
     * Handles setting instruction breakpoints.
     * Not currently implemented.
     */
    @Override
    public CompletableFuture<SetInstructionBreakpointsResponse> setInstructionBreakpoints(SetInstructionBreakpointsArguments args) {
        LOGGER.debug("SetInstructionBreakpoints request received with arguments: {}", args);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles setting exception breakpoints.
     * Not currently implemented.
     */
    @Override
    public CompletableFuture<SetExceptionBreakpointsResponse> setExceptionBreakpoints(SetExceptionBreakpointsArguments args) {
        LOGGER.debug("SetExceptionBreakpoints request received with arguments: {}", args);
        return CompletableFuture.completedFuture(null);
    }

    // ===== Execution Control Methods =====

    /**
     * Handles step over (next) execution command.
     */
    @Override
    public CompletableFuture<Void> next(NextArguments args) {
        LOGGER.debug("Next request received with arguments: {}", args);

        try {
            BreakPointCommand.stepType = StepType.STEP_OVER;
            BreakPointCommand.step(1, getCommandSource());
        } catch (Exception e) {
            LOGGER.warn("Error during step over execution", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles step into execution command.
     */
    @Override
    public CompletableFuture<Void> stepIn(StepInArguments args) {
        LOGGER.debug("StepIn request received with arguments: {}", args);

        try {
            BreakPointCommand.stepType = StepType.STEP_IN;
            BreakPointCommand.step(1, getCommandSource());
        } catch (Exception e) {
            LOGGER.warn("Error during step in execution", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles step out execution command.
     */
    @Override
    public CompletableFuture<Void> stepOut(StepOutArguments args) {
        LOGGER.debug("StepOut request received with arguments: {}", args);

        try {
            BreakPointCommand.stepType = StepType.STEP_OUT;
            BreakPointCommand.step(1, getCommandSource());
        } catch (Exception e) {
            LOGGER.warn("Error during step out execution", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handles continue execution command.
     */
    @Override
    public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
        LOGGER.debug("Continue request received with arguments: {}", args);

        try {
            continueExec(getCommandSource());
        } catch (Exception e) {
            LOGGER.warn( "Error during continue execution", e);
        }

        var continueResponse = new ContinueResponse();

        LOGGER.debug("Sending Continue response: {}", continueResponse);
        return CompletableFuture.completedFuture(continueResponse);
    }

    /**
     * Handles the pause execution command.
     * Not currently implemented.
     */
    @Override
    public CompletableFuture<Void> pause(PauseArguments args) {
        LOGGER.debug("Pause request received with arguments: {}", args);
        return CompletableFuture.completedFuture(null);
    }

    // ===== Inspection Methods =====

    /**
     * Provides a list of threads.
     * Returns a single thread for the datapack execution.
     */
    @Override
    public CompletableFuture<ThreadsResponse> threads() {
        LOGGER.debug("Threads request received");

        Thread thread = new Thread();
        thread.setId(debuggerState.THREAD_ID);
        thread.setName(MAIN_THREAD_NAME);

        ThreadsResponse response = new ThreadsResponse();
        response.setThreads(new Thread[]{thread});

        LOGGER.debug("Sending Threads response: {}", response);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Provides a stack trace for a given thread.
     * Maps Minecraft function call stack to DAP stack frames.
     */
    @Override
    public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
        LOGGER.debug("StackTrace request received with arguments: {}", args);

        var startFrame = args.getStartFrame() != null ? args.getStartFrame() : DEFAULT_START_FRAME;
        var maxLevels = args.getLevels() != null ? args.getLevels() : DEFAULT_MAX_LEVELS;
        var endFrame = Math.min(startFrame + maxLevels, ScopeManager.get().count());

        var frames = buildStackFrames(startFrame, endFrame);
        updateHeadFrame(frames);

        StackTraceResponse response = new StackTraceResponse();
        response.setStackFrames(frames.toArray(StackFrame[]::new));
        response.setTotalFrames(frames.size());

        LOGGER.debug("Sending StackTrace response: {}", response);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<SourceResponse> source(SourceArguments args) {
        LOGGER.debug("Source request received with arguments: {}", args);

        var response = new SourceResponse();
        var id = ResourceLocation.tryParse(args.getSource().getName());
        var content = String.join("\n", FunctionTextLoader.get(id));

        response.setContent(content);
        response.setMimeType("text/mcfunction");

        LOGGER.debug("Sending Source response: {}", response);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Builds stack frames for the specified range of frames.
     */
    private List<StackFrame> buildStackFrames(int startFrame, int endFrame) {
        var frames = new ArrayList<StackFrame>(endFrame - startFrame);

        for(var frame: this.scopeManager.getDebugScopes().subList(startFrame, endFrame)) {
            StackFrame stackFrame = createStackFrame(frame);
            frames.add(stackFrame);
        }

        return frames;
    }

    /**
     * Creates a DAP StackFrame from a function stack scope.
     */
    private StackFrame createStackFrame(ScopeManager.DebugScope scope) {
        StackFrame stackFrame = new StackFrame();
        stackFrame.setId(scope.getId());

        stackFrame.setName(scope.getFunction());
        // Convert to 1-indexed
        stackFrame.setLine(scope.getLine() + 1);

        var source = createSource(scope);
        stackFrame.setSource(source);

        return stackFrame;
    }

    /**
     * Creates a DAP Source for a function stack scope.
     */
    private Source createSource(ScopeManager.DebugScope scope) {
        var source = new Source();
        var pathOpt = scope.getPath();

        if(pathOpt.isPresent()) {
            var path = pathOpt.get();
            if(path.kind() == RealPath.Kind.DIRECTORY) {
                source.setPath(path.path());
            } else if(path.kind() == RealPath.Kind.ZIP){
                source.setSourceReference(1);
                source.setPath(path.path());
            }
        }

        source.setName(scope.getFunction());

        return source;
    }

    /**
     * Updates the head scope with current file and line information.
     */
    private void updateHeadFrame(List<StackFrame> frames) {
        if (!frames.isEmpty()) {
            var headLine = scopeManager.getCurrentScope().map(ScopeManager.DebugScope::getLine).orElse(0) + 1;
            frames.getFirst().setName(scopeManager.getCurrentScope().map(ScopeManager.DebugScope::getFunction).orElse(""));
            frames.getFirst().setLine(headLine);
        }
    }

    /**
     * Provides variable scopes for a given stack scope.
     * Currently returns empty scopes.
     */
    @Override
    public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
        LOGGER.debug("Scopes request received with arguments: {}", args);

        var response = new ScopesResponse();

        var localScope = new Scope();
        localScope.setName("Function");
        localScope.setLine(0);
        localScope.setPresentationHint("locals");

        if(scopeManager.count() > 0) {
            var scopeOpt = scopeManager.getScope(args.getFrameId());
            if(scopeOpt.isPresent()) {
                var scope = scopeOpt.get();
                var rootVariable = scope.getRootVariables();
                localScope.setSource(createSource(scope));
                localScope.setNamedVariables(rootVariable.size());
                localScope.setVariablesReference(scope.getId());
            }
        }

        response.setScopes(new Scope[]{localScope});

        LOGGER.debug("Sending Scopes response: {}", response);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
        LOGGER.debug("Variables request received with arguments: {}", args);

        return CompletableFuture.supplyAsync(() -> {

            var dapVariables = new LinkedList<Variable>();
            List<DebuggerVariable> variables;

            //get debugger variable
            if(args.getVariablesReference() >= 1000000000){
                //from expression
                variables = debugVars.get(args.getVariablesReference()).children();
            }else{
                //from scope
                var variableOpt = scopeManager.getVariables(args.getVariablesReference());
                variables = variableOpt.orElseGet(ArrayList::new);
            }

            var start = args.getStart() == null ? 0 : args.getStart();
            var count = args.getCount() == null ? variables.size() : args.getCount();
            var min = start >= 0 && start < variables.size() ? start : 0;
            var max = Math.min(min + count, variables.size());
            variables = variables.subList(min, max);

            for(var variable: variables) {
                var var = new Variable();
                var.setName(variable.name());
                var.setValue(variable.value());
                var.setVariablesReference(!variable.children().isEmpty() ? variable.id() : 0);
                var.setIndexedVariables(variable.children().size());
                var display = new VariablePresentationHint();
                display.setKind("data");
                var.setPresentationHint(display);
                dapVariables.add(var);
            }

            var response = new VariablesResponse();
            response.setVariables(dapVariables.stream().sorted(Comparator.comparingInt(Variable::getVariablesReference)).toArray(Variable[]::new));
            LOGGER.debug("Sending Variables response: {}", response);
            return response;
        });
    }

    private final ConcurrentHashMap<Integer, DebuggerVariable> debugVars = new ConcurrentHashMap<>();
    private final AtomicInteger nextVarRef = new AtomicInteger(1000000000);

    private final ConcurrentHashMap<String, List<Integer>> debugVarRefs = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
        return CompletableFuture.supplyAsync(() -> {
            //remove duplicate variables
            if(debugVarRefs.containsKey(args.getExpression())){
                for(var ref : debugVarRefs.get(args.getExpression())){
                    debugVars.remove(ref);
                }
                debugVarRefs.remove(args.getExpression());
            }
            var result = VariableManager.evaluate(args.getExpression());
            var response = new EvaluateResponse();
            if(result.isFailure()){
                response.setResult(result.getError());
                response.setVariablesReference(0);
                return response;
            }
            DebugData data = result.getData();
            var scope = ScopeManager.get().getCurrentScope();
            if(scope.isEmpty()){
                response.setResult("Scope is null");
                response.setVariablesReference(0);
                return response;
            }
            var source = scope.get().getExecutor();
            if(source instanceof CommandSourceStack serverSource){
                try{
                    Object value = data.get(serverSource);
                    if(value instanceof CompoundTag compound){
                        var ref = nextVarRef.get();
                        var var = VariableManager.convertNbtCompound("debug", compound, ref, true);
                        debugVars.putAll(var);
                        nextVarRef.getAndAdd(var.size());
                        debugVarRefs.put(args.getExpression(), new ArrayList<>(var.values().stream().map(DebuggerVariable::id).toList()));
                        response.setResult(value.toString());
                        response.setVariablesReference(ref);
                    }else {
                        response.setResult(value.toString());
                        response.setVariablesReference(0);
                    }
                    return response;
                }catch (Exception e){
                    response.setResult(e.getMessage());
                    response.setVariablesReference(0);
                    return response;
                }
            }
            response.setResult("Source is not a server command source");
            response.setVariablesReference(0);
            return response;
        });
    }

    // ===== Event Handlers =====

    /**
     * Sets the DAP client for callback communication.
     *
     * @param client The client to send events to
     * @throws IllegalArgumentException if client is null
     */
    public void setClient(IDebugProtocolClient client) {
        LOGGER.debug("Setting client: {}", client);
        
        if (client == null) {
            LOGGER.warn("Attempt to set null client");
            throw new IllegalArgumentException("Client cannot be null");
        }
        
        this.client = client;
    }

    /**
     * Callback handler for when execution stops at a breakpoint.
     * Notifies the connected DAP client.
     */
    public void onStop(int breakpointId, String reason) {
        LOGGER.debug("onStop called with breakpointId: {}, reason: {}", breakpointId, reason);
        
        if (client == null) {
            LOGGER.warn("Cannot send stopped event: client is null");
            return;
        }
        
        var stoppedEvent = new StoppedEventArguments();
        stoppedEvent.setReason(reason);
        stoppedEvent.setDescription(BREAKPOINT_DESCRIPTION);
        stoppedEvent.setThreadId(debuggerState.THREAD_ID);
        
        if(breakpointId != -1) {
            stoppedEvent.setHitBreakpointIds(new Integer[]{breakpointId});
        }

        LOGGER.debug("Sending stopped event: {}", stoppedEvent);
        this.client.stopped(stoppedEvent);
    }

    /**
     * Callback handler for when execution continues.
     * Notifies the connected DAP client.
     */
    public void onContinue() {
        LOGGER.debug("onContinue called");
        
        if (client == null) {
            LOGGER.warn("Cannot send continued event: client is null");
            return;
        }
        
        var continuedEvent = new ContinuedEventArguments();
        continuedEvent.setThreadId(debuggerState.THREAD_ID);

        LOGGER.debug("Sending continued event: {}", continuedEvent);
        this.client.continued(continuedEvent);
    }

    /**
     * Notifies the client that the debugging session has ended.
     */
    public void exit() {
        LOGGER.debug("exit called");
        
        if (client == null) {
            LOGGER.warn("Cannot send exited event: client is null");
            return;
        }
        
        try {
            // Send end events to the client
            sendTerminatedEvent();
            sendExitedEvent();
        } catch (Exception e) {
            LOGGER.warn("Error while sending exit events", e);
        }
    }
    
    /**
     * Sends a 'terminated' event to the client.
     * This event indicates that the debug session has ended.
     */
    private void sendTerminatedEvent() {
        try {
            TerminatedEventArguments terminatedEvent = new TerminatedEventArguments();
            LOGGER.debug("Sending terminated event: {}", terminatedEvent);
            client.terminated(terminatedEvent);
        } catch (Exception e) {
            LOGGER.warn("Failed to send terminated event", e);
        }
    }
    
    /**
     * Sends an 'exited' event to the client.
     * This event indicates that the debugged process has stopped.
     */
    private void sendExitedEvent() {
        try {
            var exitedEvent = new ExitedEventArguments();
            exitedEvent.setExitCode(DEFAULT_EXIT_CODE);

            LOGGER.debug("Sending exited event: {}", exitedEvent);
            client.exited(exitedEvent);
        } catch (Exception e) {
            LOGGER.warn("Failed to send exited event", e);
        }
    }
    
    // ===== Helper Methods =====
    
    /**
     * Sends a message to all players on the server.
     */
    private void sendMessageToAllPlayers(String message) {
        try {
            debuggerState.getServer().getPlayerList().getPlayers().forEach(player -> {
                player.sendSystemMessage(addSnifferPrefix(message));
            });
        } catch (Exception e) {
            LOGGER.warn("Error sending message to players", e);
        }
    }
    
    /**
     * Gets the command source from the server.
     */
    private CommandSourceStack getCommandSource() {
        return debuggerState.getServer().createCommandSourceStack();
    }
}
