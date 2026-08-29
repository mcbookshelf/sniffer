package dev.mcbookshelf.sniffer.dap

/**
 * A service Sniffer serves to the attached editor, listed in [DapEndpointsRegistry.buildLocalServices].
 *
 * It declares no method: services are merged into one endpoint and must not share a method name,
 * so they never inherit from one another.
 *
 * @author theogiraudet
 */
interface DapService
