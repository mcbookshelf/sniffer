package dev.mcbookshelf.sniffer.dap

import dev.mcbookshelf.sniffer.state.DebuggerVariable
import dev.mcbookshelf.sniffer.state.RealPath
import dev.mcbookshelf.sniffer.util.Extension.addSnifferPrefix
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.IInput
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.dispatch.SnifferDispatcher
import dev.mcbookshelf.sniffer.input.*
import dev.mcbookshelf.sniffer.output.*
import dev.mcbookshelf.sniffer.state.DebugEventBus
import dev.mcbookshelf.sniffer.state.ServerReference
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.Thread
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * A DAP (Debug Adapter Protocol) server implementation using LSP4J.
 *
 * This class is a thin translator: it converts DAP protocol requests into
 * v2 [IInput] objects, dispatches them through [SnifferDispatcher],
 * and translates the returned [Output] back into DAP responses.
 * No debugger logic lives here.
 *
 * @author theogiraudet
 */
class DapServer : IDebugProtocolServer {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("sniffer")

        private const val ATTACHED_MESSAGE = "Attached to VSCode!"
        private const val DISCONNECTED_MESSAGE = "Disconnected from VSCode."
        private const val BREAKPOINT_DESCRIPTION = "Breakpoint reached"
        private const val MAIN_THREAD_NAME = "Main Thread"

        private const val DEFAULT_START_FRAME = 0
        private const val DEFAULT_MAX_LEVELS = 1000
        private const val DEFAULT_EXIT_CODE = 0
        private const val THREAD_ID = 1
    }

    private var client: IDebugProtocolClient? = null

    init {
        DebugEventBus.onStop(::onStop)
        DebugEventBus.onContinue(::onContinue)
        DebugEventBus.onShutdown(::exit)
    }

    // ===== Lifecycle Methods =====

    override fun initialize(args: InitializeRequestArguments): CompletableFuture<Capabilities> {
        LOGGER.debug("Initialize request received with arguments: {}", args)

        val capabilities = Capabilities().apply {
            supportsConfigurationDoneRequest = true
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
        return CompletableFuture.completedFuture(null)
    }

    override fun disconnect(args: DisconnectArguments): CompletableFuture<Void> {
        LOGGER.debug("Disconnect request received with arguments: {}", args)
        sendMessageToAllPlayers(DISCONNECTED_MESSAGE)
        dispatchAction(ContinueInput, "disconnect")
        return CompletableFuture.completedFuture(null)
    }

    override fun configurationDone(args: ConfigurationDoneArguments?): CompletableFuture<Void> {
        LOGGER.debug("ConfigurationDone request received with arguments: {}", args)
        return CompletableFuture.completedFuture(null)
    }

    // ===== Breakpoint Methods =====

    override fun setBreakpoints(args: SetBreakpointsArguments): CompletableFuture<SetBreakpointsResponse> {
        LOGGER.debug("SetBreakpoints request received with arguments: {}", args)

        if (args?.source?.path == null) {
            LOGGER.warn("Received invalid SetBreakpoints request with null arguments")
            return CompletableFuture.completedFuture(SetBreakpointsResponse())
        }

        val lines = args.breakpoints.map { it.line - 1 }

        val output = dispatch(SetBreakpointsInput(args.source.path, lines)) as SetBreakpointsOutput

        val dapBreakpoints = output.results.map { result ->
            Breakpoint().apply {
                line = result.line + 1
                isVerified = result.verified
                if (result.id != null) {
                    id = result.id
                } else {
                    reason = BreakpointNotVerifiedReason.FAILED
                }
            }
        }

        return CompletableFuture.completedFuture(SetBreakpointsResponse().apply {
            breakpoints = dapBreakpoints.toTypedArray()
        })
    }

    override fun setInstructionBreakpoints(args: SetInstructionBreakpointsArguments): CompletableFuture<SetInstructionBreakpointsResponse> {
        LOGGER.debug("SetInstructionBreakpoints request received with arguments: {}", args)
        return CompletableFuture.completedFuture(null)
    }

    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments): CompletableFuture<SetExceptionBreakpointsResponse> {
        LOGGER.debug("SetExceptionBreakpoints request received with arguments: {}", args)
        return CompletableFuture.completedFuture(null)
    }

    // ===== Execution Control Methods =====

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
        return CompletableFuture.completedFuture(null)
    }

    // ===== Inspection Methods =====

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

        val output = dispatch(GetStackTraceInput(startFrame, maxLevels)) as StackTraceOutput

        val frames = output.frames.map { data ->
            StackFrame().apply {
                id = data.id
                name = data.functionName
                line = data.line + 1
                source = toSource(data.functionName, data.path)
            }
        }

        return CompletableFuture.completedFuture(StackTraceResponse().apply {
            stackFrames = frames.toTypedArray()
            totalFrames = output.totalFrames
        })
    }

    override fun source(args: SourceArguments): CompletableFuture<SourceResponse> {
        LOGGER.debug("Source request received with arguments: {}", args)

        val output = dispatch(GetSourceInput(args.source.name)) as SourceOutput

        return CompletableFuture.completedFuture(SourceResponse().apply {
            content = output.content
            mimeType = output.mimeType
        })
    }

    override fun scopes(args: ScopesArguments): CompletableFuture<ScopesResponse> {
        LOGGER.debug("Scopes request received with arguments: {}", args)

        val output = dispatch(GetScopesInput(args.frameId)) as ScopesOutput

        val dapScopes = output.scopes.map { data ->
            Scope().apply {
                name = data.name
                line = 0
                presentationHint = "locals"
                namedVariables = data.variableCount
                variablesReference = data.id
                source = toSource(data.functionName, data.path)
            }
        }

        return CompletableFuture.completedFuture(ScopesResponse().apply {
            scopes = dapScopes.toTypedArray()
        })
    }

    override fun variables(args: VariablesArguments): CompletableFuture<VariablesResponse> {
        LOGGER.debug("Variables request received with arguments: {}", args)

        return CompletableFuture.supplyAsync {
            val output = dispatch(
                ResolveVariablesInput(args.variablesReference, args.start, args.count)
            ) as ResolveVariablesOutput

            val dapVars = output.variables
                .map { toDapVariable(it) }
                .sortedBy { it.variablesReference }

            VariablesResponse().apply {
                variables = dapVars.toTypedArray()
            }
        }
    }

    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluateResponse> {
        LOGGER.debug("Evaluate request received with arguments: {}", args)

        return CompletableFuture.supplyAsync {
            val output = dispatch(EvaluateInput(args.expression)) as EvaluateOutput

            EvaluateResponse().apply {
                result = output.result
                variablesReference = output.variablesReference
            }
        }
    }

    // ===== Event Handlers =====

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

    // ===== Dispatch & Translation Helpers =====

    private fun dispatch(input: IInput): Output {
        val source = ServerReference.getCommandSource()
        return SnifferDispatcher.get().dispatch(input, Context(source, ServerReference.get()))
    }

    private fun dispatchAction(input: IInput, label: String) {
        try {
            dispatch(input)
        } catch (e: Exception) {
            LOGGER.warn("Error during {} execution", label, e)
        }
    }

    private fun toSource(functionName: String, path: RealPath?): Source {
        val source = Source().apply {
            name = functionName
        }
        if (path != null) {
            when (path.kind) {
                RealPath.Kind.DIRECTORY -> source.path = path.path
                RealPath.Kind.ZIP -> {
                    source.sourceReference = 1
                    source.path = path.path
                }
            }
        }
        return source
    }

    private fun toDapVariable(variable: DebuggerVariable): Variable {
        return Variable().apply {
            name = variable.name
            value = variable.value
            variablesReference = if (variable.children.isNotEmpty()) variable.id else 0
            indexedVariables = variable.children.size
            presentationHint = VariablePresentationHint().apply {
                kind = "data"
            }
        }
    }

    private fun sendMessageToAllPlayers(message: String) {
        try {
            ServerReference.get().playerList.players.forEach { player ->
                player.sendSystemMessage(addSnifferPrefix(message))
            }
        } catch (e: Exception) {
            LOGGER.warn("Error sending message to players", e)
        }
    }
}
