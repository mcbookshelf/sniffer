package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.state.RealPath
import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of a scopes query for a given stack frame.
 *
 * @property scopes the list of variable scopes for the frame.
 */
data class ScopesOutput(val scopes: List<ScopeData>) : Output

/**
 * Domain representation of a variable scope.
 *
 * @property id unique scope ID (used as variablesReference).
 * @property name display name of the scope.
 * @property variableCount number of root-level variables in this scope.
 * @property functionName the Minecraft function path.
 * @property path the resolved filesystem path and kind, or null if unresolved.
 */
data class ScopeData(
    val id: Int,
    val name: String,
    val variableCount: Int,
    val functionName: String,
    val path: RealPath?,
)
