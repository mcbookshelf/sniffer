package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.domain.RealPath

/**
 * The scopes a stack frame exposes.
 *
 * @property scopes the variable scopes of that frame
 * @author theogiraudet
 */
data class ScopesOutput(val scopes: List<ScopeData>) : Output

/**
 * A single variable scope.
 *
 * @property id id of the scope, which the client uses as a variables reference
 * @property name name to display
 * @property variableCount how many root variables the scope holds
 * @property functionName location of the function, as `namespace:path`
 * @property path where the function was loaded from, `null` if it could not be resolved
 * @author theogiraudet
 */
data class ScopeData(
    val id: Int,
    val name: String,
    val variableCount: Int,
    val functionName: String,
    val path: RealPath?,
)
