package dev.mcbookshelf.sniffer.dap

/**
 * A facet of the attached editor, listed in [DapEndpointsRegistry.buildRemoteInterfaces].
 *
 * It declares no method: facets are gathered into one proxy and must not share a method name,
 * so they never inherit from one another.
 *
 * @author theogiraudet
 */
interface DapRemote
