package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.state.VariableNode
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of resolving a variables reference.
 *
 * @property variables the resolved nodes
 * @author theogiraudet
 */
data class ResolveVariablesOutput(val variables: List<VariableNode>) : Output
