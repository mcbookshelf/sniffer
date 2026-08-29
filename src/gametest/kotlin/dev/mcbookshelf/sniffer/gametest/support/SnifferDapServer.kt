package dev.mcbookshelf.sniffer.gametest.support

import dev.mcbookshelf.sniffer.features.trace.TraceArguments
import dev.mcbookshelf.sniffer.features.trace.TraceResponse
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

/**
 * The whole surface the mod serves, standard DAP plus what Sniffer adds on top.
 *
 * The mod splits it into several [dev.mcbookshelf.sniffer.dap.DapService] facets that LSP4J merges into one
 * endpoint, so a client only ever needs one interface to reach all of it.
 */
interface SnifferDapServer : IDebugProtocolServer {

    @JsonRequest("snifferTrace")
    fun snifferTrace(args: TraceArguments): CompletableFuture<TraceResponse>
}
