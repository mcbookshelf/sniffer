package dev.mcbookshelf.sniffer.state

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages variable references created by expression evaluation in the DAP watch/evaluate panel.
 * Expression-evaluated variables use IDs >= [EXPRESSION_VAR_THRESHOLD] to avoid
 * colliding with scope-based variable IDs managed by [ScopeManager].
 *
 * @author theogiraudet
 */
object EvaluationVariableStore {

    /** IDs at or above this value belong to expression-evaluated variables. */
    const val EXPRESSION_VAR_THRESHOLD: Int = 1_000_000_000

    private val debugVars = ConcurrentHashMap<Int, DebuggerVariable>()
    private val debugVarRefs = ConcurrentHashMap<String, List<Int>>()
    private val nextVarRef = AtomicInteger(EXPRESSION_VAR_THRESHOLD)

    /**
     * Returns true if the given variable reference ID belongs to an expression-evaluated variable.
     */
    fun isExpressionVariable(variablesReference: Int): Boolean =
        variablesReference >= EXPRESSION_VAR_THRESHOLD

    /**
     * Returns the children of an expression-evaluated variable.
     *
     * @param variablesReference the variable reference ID (must be >= threshold)
     * @return the children, or an empty list if not found
     */
    fun getChildren(variablesReference: Int): List<DebuggerVariable> =
        debugVars[variablesReference]?.children ?: emptyList()

    /**
     * Returns the next available reference ID without advancing the counter.
     * Use this as the `startIndex` for [VariableManager.convertNbtCompound],
     * then call [store] with the resulting map.
     */
    fun peekNextRef(): Int = nextVarRef.get()

    /**
     * Stores a set of evaluated variables and associates them with the given expression
     * for later deduplication. Advances the internal ID counter by the number of variables.
     *
     * @param expression the expression string used as dedup key
     * @param vars       the flattened variable map (id -> variable) produced by evaluation
     */
    fun store(expression: String, vars: Map<Int, DebuggerVariable>) {
        debugVars.putAll(vars)
        nextVarRef.getAndAdd(vars.size)
        debugVarRefs[expression] = vars.values.map { it.id }
    }

    /**
     * Removes previously stored variables for the given expression to avoid duplicates
     * when the same expression is re-evaluated.
     */
    fun clearPrevious(expression: String) {
        debugVarRefs.remove(expression)?.forEach { debugVars.remove(it) }
    }
}
