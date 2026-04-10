package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.state.DebuggerVariable
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of a variables resolution query.
 *
 * @property variables the resolved debugger variables.
 */
data class ResolveVariablesOutput(val variables: List<DebuggerVariable>) : Output
