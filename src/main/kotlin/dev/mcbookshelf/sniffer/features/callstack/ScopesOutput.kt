package dev.mcbookshelf.sniffer.features.callstack

import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.source.FunctionIdentity

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
 * @property identity the function this scope belongs to, located as well as named
 * @author theogiraudet
 */
data class ScopeData(
    val id: Int,
    val name: String,
    val variableCount: Int,
    val identity: FunctionIdentity,
)
