package dev.mcbookshelf.sniffer.features.trace

import dev.mcbookshelf.sniffer.dap.DapDispatch
import dev.mcbookshelf.sniffer.dap.DapService
import dev.mcbookshelf.sniffer.dap.ServerReference
import dev.mcbookshelf.sniffer.util.IsolatedExecution
import net.minecraft.commands.Commands
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

/**
 * The tracing requests the editor can send, the incoming counterpart of [TraceClient].
 *
 * @author theogiraudet
 */
class TraceDapService : DapService {

    /**
     * Traces the given command, the graph reaching the editor through [TraceClient] as it is walked.
     * The editor sends text, so unlike `/trace` this parses at request time, then goes through the same command.
     */
    @JsonRequest("snifferTrace")
    fun trace(args: TraceArguments): CompletableFuture<TraceResponse> =
        DapDispatch.onServerThread {
            val mark = TraceState.mark()
            val source = ServerReference.getCommandSource()
            val commands = source.server.commands
            val command = "trace run " + Commands.trimOptionalPrefix(args.command.trim())
            val parse = commands.dispatcher.parse(command, source)
            IsolatedExecution.outsideCurrentContext { commands.performCommand(parse, command) }
            TraceResponse(TraceState.openedSince(mark))
        }
}

/**
 * @property command the command to trace, as it would be typed
 * @author theogiraudet
 */
data class TraceArguments(val command: String)

/**
 * @property traceId the trace that was opened, `null` when it was refused and nothing was executed
 * @author theogiraudet
 */
data class TraceResponse(val traceId: Int?)
