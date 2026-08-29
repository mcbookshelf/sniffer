package dev.mcbookshelf.sniffer.dap

import org.eclipse.lsp4j.debug.services.IDebugProtocolClient

/**
 * The standard DAP surface of the editor, as a [DapRemote] facet.
 *
 * @author theogiraudet
 */
interface StandardDapClient : IDebugProtocolClient, DapRemote
