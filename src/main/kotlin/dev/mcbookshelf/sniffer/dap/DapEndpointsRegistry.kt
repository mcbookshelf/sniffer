package dev.mcbookshelf.sniffer.dap

import dev.mcbookshelf.sniffer.features.trace.TraceClient
import dev.mcbookshelf.sniffer.features.trace.TraceDapService

/**
 * Lists the two halves of the DAP connection, read once per client.
 *
 * @author theogiraudet
 */
object DapEndpointsRegistry {

    /** The facets the editor implements and Sniffer calls. */
    fun buildRemoteInterfaces(): List<Class<out DapRemote>> = listOf(
        StandardDapClient::class.java,
        TraceClient::class.java,
    )

    /** The services Sniffer implements and the editor calls. */
    fun buildLocalServices(dapServer: DapServer): List<DapService> = listOf(
        dapServer,
        TraceDapService(),
    )
}
