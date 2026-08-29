package dev.mcbookshelf.sniffer.dap

import dev.mcbookshelf.sniffer.dispatch.IInput
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.features.breakpoints.BreakpointSpec
import dev.mcbookshelf.sniffer.features.breakpoints.SetBreakpointsInput
import dev.mcbookshelf.sniffer.features.breakpoints.SetBreakpointsOutput
import dev.mcbookshelf.sniffer.features.callstack.GetScopesInput
import dev.mcbookshelf.sniffer.features.callstack.GetStackTraceInput
import dev.mcbookshelf.sniffer.features.callstack.ScopesOutput
import dev.mcbookshelf.sniffer.features.callstack.StackTraceOutput
import dev.mcbookshelf.sniffer.features.evaluate.EvaluateInput
import dev.mcbookshelf.sniffer.features.evaluate.EvaluateOutput
import dev.mcbookshelf.sniffer.features.source.Line
import dev.mcbookshelf.sniffer.features.source.GetSourceInput
import dev.mcbookshelf.sniffer.features.source.SourceFactory
import dev.mcbookshelf.sniffer.features.source.SourceOutput
import dev.mcbookshelf.sniffer.features.stepping.*
import dev.mcbookshelf.sniffer.features.variables.ResolveVariablesInput
import dev.mcbookshelf.sniffer.features.variables.ResolveVariablesOutput
import dev.mcbookshelf.sniffer.features.variables.VariableNode
import dev.mcbookshelf.sniffer.chat.SnifferChat
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * Debug Adapter Protocol server backed by LSP4J.
 *
 * It is a translator and holds no debugger logic: requests become [IInput] objects,
 * go through [SnifferDispatcher], and their [Output] is turned back into DAP responses.
 *
 * @author theogiraudet
 * @author Alumopper
 */
class DapServer : IDebugProtocolServer, DapService {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("sniffer")

        private const val ATTACHED_MESSAGE = "sniffer.dap.attached"
        private const val DISCONNECTED_MESSAGE = "sniffer.dap.disconnected"
        private const val BREAKPOINT_DESCRIPTION = "Breakpoint reached"
        private const val MAIN_THREAD_NAME = "Main Thread"

        private const val DEFAULT_START_FRAME = 0
        private const val DEFAULT_MAX_LEVELS = 1000
        private const val DEFAULT_EXIT_CODE = 0
        private const val THREAD_ID = 1
    }

    private var client: IDebugProtocolClient? = null

    /**
     * One DAP `sourceReference` per function packed in a zip, since clients cache source content by reference.
     * Only touched from the server thread.
     */

    init {
        DebugEventBus.onStop(::onStop)
        DebugEventBus.onContinue(::onContinue)
        DebugEventBus.onShutdown(::exit)
    }


    override fun initialize(args: InitializeRequestArguments): CompletableFuture<Capabilities> {
        LOGGER.debug("Initialize request received with arguments: {}", args)

        val capabilities = Capabilities().apply {
            supportsConfigurationDoneRequest = true
            supportsConditionalBreakpoints = true
            supportsRestartRequest = true
        }

        return CompletableFuture.completedFuture(capabilities).thenApply { c ->
            LOGGER.debug("Sending initialized event")
            if (client != null) {
                client!!.initialized()
            } else {
                LOGGER.warn("Client is null during initialize, couldn't send initialized event")
            }
            c
        }
    }

    override fun launch(args: Map<String, Any>): CompletableFuture<Void> {
        LOGGER.debug("Launch request received with arguments: {}", args)
        return CompletableFuture.completedFuture(null)
    }

    override fun attach(args: Map<String, Any>): CompletableFuture<Void> {
        LOGGER.debug("Attach request received with arguments: {}", args)
        sendMessageToAllPlayers(ATTACHED_MESSAGE)
        LogForwarder.start(::sendOutput)
        return CompletableFuture.completedFuture(null)
    }

    override fun disconnect(args: DisconnectArguments): CompletableFuture<Void> {
        LOGGER.debug("Disconnect request received with arguments: {}", args)
        sendMessageToAllPlayers(DISCONNECTED_MESSAGE)
        LogForwarder.stop()
        if (SteppingState.isDebugging) {
            dispatchAction(ContinueInput, "disconnect")
        }
        return CompletableFuture.completedFuture(null)
    }

    /**
     * Sniffer attaches to a running game and never launches nor replays anything, so there is nothing to restart.
     * The request is answered and ignored: VS Code always shows the button, and claiming the capability
     * is the only way to stop it from falling back to a disconnect and reconnect,
     * which would drop the session and raise a new authorization prompt in game.
     */
    override fun restart(args: RestartArguments): CompletableFuture<Void> {
        LOGGER.debug("Restart request received with arguments: {}, ignoring it", args)
        return CompletableFuture.completedFuture(null)
    }

    override fun configurationDone(args: ConfigurationDoneArguments?): CompletableFuture<Void> {
        LOGGER.debug("ConfigurationDone request received with arguments: {}", args)
        return CompletableFuture.completedFuture(null)
    }


    override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> {
        LOGGER.debug("SetBreakpoints request received with arguments: {}", args)

        if (args?.source?.path == null) {
            LOGGER.warn("Received invalid SetBreakpoints request with null arguments")
            return CompletableFuture.completedFuture(SetBreakpointsResponse())
        }

        val specs = args.breakpoints.map { BreakpointSpec(Line.inEditor(it.line), it.condition) }

        return onServerThread {
            val output = dispatch(SetBreakpointsInput(args.source.path, specs)) as SetBreakpointsOutput

            val dapBreakpoints = output.results.map { result ->
                Breakpoint().apply {
                    line = result.line.inEditor
                    isVerified = result.verified
                    if (result.message != null) {
                        message = result.message
                    }
                    if (result.id != null) {
                        id = result.id
                    } else {
                        reason = BreakpointNotVerifiedReason.FAILED
                    }
                }
            }

            SetBreakpointsResponse().apply {
                breakpoints = dapBreakpoints.toTypedArray()
            }
        }
    }

    override fun setInstructionBreakpoints(args: SetInstructionBreakpointsArguments): CompletableFuture<SetInstructionBreakpointsResponse> {
        LOGGER.debug("SetInstructionBreakpoints request received with arguments: {}", args)
        return CompletableFuture.completedFuture(SetInstructionBreakpointsResponse())
    }

    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments): CompletableFuture<SetExceptionBreakpointsResponse> {
        LOGGER.debug("SetExceptionBreakpoints request received with arguments: {}", args)
        return CompletableFuture.completedFuture(SetExceptionBreakpointsResponse())
    }


    override fun next(args: NextArguments): CompletableFuture<Void> {
        LOGGER.debug("Next request received with arguments: {}", args)
        dispatchAction(StepOverInput(1), "step over")
        return CompletableFuture.completedFuture(null)
    }

    override fun stepIn(args: StepInArguments): CompletableFuture<Void> {
        LOGGER.debug("StepIn request received with arguments: {}", args)
        dispatchAction(StepInInput(1), "step in")
        return CompletableFuture.completedFuture(null)
    }

    override fun stepOut(args: StepOutArguments): CompletableFuture<Void> {
        LOGGER.debug("StepOut request received with arguments: {}", args)
        dispatchAction(StepOutInput(1), "step out")
        return CompletableFuture.completedFuture(null)
    }

    override fun continue_(args: ContinueArguments): CompletableFuture<ContinueResponse> {
        LOGGER.debug("Continue request received with arguments: {}", args)
        dispatchAction(ContinueInput, "continue")
        return CompletableFuture.completedFuture(ContinueResponse())
    }

    override fun pause(args: PauseArguments): CompletableFuture<Void> {
        LOGGER.debug("Pause request received with arguments: {}", args)
        dispatchAction(PauseInput, "pause")
        return CompletableFuture.completedFuture(null)
    }


    override fun threads(): CompletableFuture<ThreadsResponse> {
        LOGGER.debug("Threads request received")

        val thread = Thread().apply {
            id = THREAD_ID
            name = MAIN_THREAD_NAME
        }

        return CompletableFuture.completedFuture(ThreadsResponse().apply {
            threads = arrayOf(thread)
        })
    }

    override fun stackTrace(args: StackTraceArguments): CompletableFuture<StackTraceResponse> {
        LOGGER.debug("StackTrace request received with arguments: {}", args)

        val startFrame = args.startFrame ?: DEFAULT_START_FRAME
        val maxLevels = args.levels ?: DEFAULT_MAX_LEVELS

        return onServerThread {
            val output = dispatch(GetStackTraceInput(startFrame, maxLevels)) as StackTraceOutput

            val frames = output.frames.map { data ->
                StackFrame().apply {
                    id = data.id
                    name = data.identity.minecraftPath
                    line = data.line?.inEditor ?: 0
                    source = SourceFactory.toSource(data.identity)
                }
            }

            StackTraceResponse().apply {
                stackFrames = frames.toTypedArray()
                totalFrames = output.totalFrames
            }
        }
    }

    override fun source(args: SourceArguments): CompletableFuture<SourceResponse> {
        LOGGER.debug("Source request received with arguments: {}", args)

        return onServerThread {
            val output = dispatch(GetSourceInput(args.source.name)) as SourceOutput

            SourceResponse().apply {
                content = output.content
                mimeType = output.mimeType
            }
        }
    }

    override fun scopes(args: ScopesArguments): CompletableFuture<ScopesResponse> {
        LOGGER.debug("Scopes request received with arguments: {}", args)

        return onServerThread {
            val output = dispatch(GetScopesInput(args.frameId)) as ScopesOutput

            val dapScopes = output.scopes.map { data ->
                Scope().apply {
                    name = data.name
                    line = 0
                    presentationHint = "locals"
                    namedVariables = data.variableCount
                    variablesReference = data.id
                    source = SourceFactory.toSource(data.identity)
                }
            }

            ScopesResponse().apply {
                scopes = dapScopes.toTypedArray()
            }
        }
    }

    override fun variables(args: VariablesArguments): CompletableFuture<VariablesResponse> {
        LOGGER.debug("Variables request received with arguments: {}", args)

        return onServerThread {
            val output = dispatch(
                ResolveVariablesInput(args.variablesReference, args.start, args.count)
            ) as ResolveVariablesOutput

            val dapVars = output.variables.map { toDapVariable(it) }

            VariablesResponse().apply {
                variables = dapVars.toTypedArray()
            }
        }
    }

    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluateResponse> {
        LOGGER.debug("Evaluate request received with arguments: {}", args)

        return onServerThread {
            val output = dispatch(EvaluateInput(args.expression)) as EvaluateOutput

            EvaluateResponse().apply {
                result = output.result
                variablesReference = output.variablesReference
            }
        }
    }


    fun setClient(client: IDebugProtocolClient) {
        LOGGER.debug("Setting client: {}", client)
        this.client = client
    }

    fun onStop(breakpointId: Int, reason: String) {
        LOGGER.debug("onStop called with breakpointId: {}, reason: {}", breakpointId, reason)
        val c = client
        if (c == null) {
            LOGGER.warn("Cannot send stopped event: client is null")
            return
        }

        val stoppedEvent = StoppedEventArguments().apply {
            this.reason = reason
            description = BREAKPOINT_DESCRIPTION
            threadId = THREAD_ID
            if (breakpointId != -1) {
                hitBreakpointIds = arrayOf(breakpointId)
            }
        }

        c.stopped(stoppedEvent)
    }

    fun onContinue() {
        LOGGER.debug("onContinue called")
        val c = client
        if (c == null) {
            LOGGER.warn("Cannot send continued event: client is null")
            return
        }

        val continuedEvent = ContinuedEventArguments().apply {
            threadId = THREAD_ID
        }
        c.continued(continuedEvent)
    }

    fun exit() {
        LOGGER.debug("exit called")
        LogForwarder.stop()
        val c = client ?: run {
            LOGGER.warn("Cannot send exited event: client is null")
            return
        }

        try {
            c.terminated(TerminatedEventArguments())
            c.exited(ExitedEventArguments().apply {
                exitCode = DEFAULT_EXIT_CODE
            })
        } catch (e: Exception) {
            LOGGER.warn("Error while sending exit events", e)
        }
    }


    /**
     * Sends one line of the game log to the client, which shows it in its debug console.
     */
    private fun sendOutput(line: String) {
        val c = client ?: return
        c.output(OutputEventArguments().apply {
            category = OutputEventArgumentsCategory.STDOUT
            output = line
        })
    }

    private fun <T> onServerThread(block: () -> T): CompletableFuture<T> = DapDispatch.onServerThread(block)

    private fun dispatch(input: IInput): Output = DapDispatch.dispatch(input)

    private fun dispatchAction(input: IInput, label: String) {
        try {
            dispatch(input)
        } catch (e: Exception) {
            LOGGER.warn("Error during {} execution", label, e)
        }
    }

    private fun toDapVariable(variable: VariableNode): Variable {
        return Variable().apply {
            name = variable.name
            value = variable.value
            variablesReference = if (variable.hasChildren) variable.id else 0
            presentationHint = VariablePresentationHint().apply {
                kind = "data"
            }
        }
    }

    private fun sendMessageToAllPlayers(key: String) {
        try {
            SnifferChat.broadcast(ServerReference.get(), key)
        } catch (e: Exception) {
            LOGGER.warn("Error sending message to players", e)
        }
    }
}
