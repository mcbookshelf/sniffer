package dev.mcbookshelf.sniffer.state

/**
 * Tracks variable nodes created by expression evaluation so that re-evaluating
 * the same expression drops the old subtree from the shared [VariableRegistry]
 * before registering the new one.
 *
 * Replaces the old `EvaluationVariableStore` and its magic-threshold ID space:
 * expression-evaluated variables now share one monotonic ID space with scope
 * variables, and cleanup is owned by the session rather than a parallel map.
 */
class EvaluationSession(private val registry: VariableRegistry) {

    private val perExpression = HashMap<String, Int>()

    /**
     * Associates [expression] with the [root] node so a later
     * [clearPrevious] can drop its subtree.
     */
    fun store(expression: String, root: VariableNode) {
        perExpression[expression] = root.id
    }

    /**
     * Drops the previously-evaluated tree for [expression], if any.
     */
    fun clearPrevious(expression: String) {
        val rootId = perExpression.remove(expression) ?: return
        val node = registry.get(rootId) ?: return
        node.invalidate(registry)
        registry.drop(listOf(rootId))
    }

    fun clearAll() {
        for (expression in perExpression.keys.toList()) clearPrevious(expression)
    }
}
