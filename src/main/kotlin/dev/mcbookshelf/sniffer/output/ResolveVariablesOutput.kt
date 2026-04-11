package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.state.VariableNode
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of a variables resolution query.
 *
 * @property variables the resolved variable nodes.
 */
data class ResolveVariablesOutput(val variables: List<VariableNode>) : Output
