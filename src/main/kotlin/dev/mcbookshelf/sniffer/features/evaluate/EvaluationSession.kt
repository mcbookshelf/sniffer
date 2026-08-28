package dev.mcbookshelf.sniffer.features.evaluate
import dev.mcbookshelf.sniffer.features.variables.VariableNode
import dev.mcbookshelf.sniffer.features.variables.VariableRegistry

/**
 * Tracks the variable nodes an expression evaluation creates,
 * so evaluating the same expression again drops the previous subtree before registering the new one.
 *
 * @author theogiraudet
 */
class EvaluationSession(private val registry: VariableRegistry) {

    private val perExpression = HashMap<String, Int>()

    /** Associates [expression] with [root], so [clearPrevious] can find its subtree later. */
    fun store(expression: String, root: VariableNode) {
        perExpression[expression] = root.id
    }

    /** Drops the tree the last evaluation of [expression] produced, if there was one. */
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
