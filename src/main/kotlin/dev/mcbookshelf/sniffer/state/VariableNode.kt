package dev.mcbookshelf.sniffer.state

/**
 * A single variable displayed in the debugger.
 *
 * Replaces the old eager [DebuggerVariable] data class. Children are produced
 * lazily by [childrenFactory] the first time [children] is called, and are
 * registered with the [VariableRegistry] so the DAP client can reference them.
 *
 * Leaves pass `childrenFactory = null`. A subsequent call to [invalidate]
 * drops the memoized children (and their descendant IDs) from the registry,
 * so that the next [children] call rebuilds fresh state — this is how the
 * between-pause refresh mechanism keeps entity positions current.
 *
 * @param isRoot whether this node should appear directly in its owning scope
 *               rather than nested under another variable.
 */
class VariableNode(
    val id: Int,
    val name: String,
    val value: String,
    val isRoot: Boolean,
    private val childrenFactory: ((VariableRegistry) -> List<VariableNode>)?,
) {

    val hasChildren: Boolean get() = childrenFactory != null

    @Volatile
    private var cachedChildren: List<VariableNode>? = null

    fun children(registry: VariableRegistry): List<VariableNode> {
        cachedChildren?.let { return it }
        val produced = childrenFactory?.invoke(registry) ?: emptyList()
        cachedChildren = produced
        return produced
    }

    /**
     * Drops memoized children recursively and removes their IDs from [registry].
     * The node itself stays registered; only its subtree is evicted.
     */
    fun invalidate(registry: VariableRegistry) {
        val current = cachedChildren ?: return
        cachedChildren = null
        val toDrop = ArrayList<Int>(current.size)
        for (child in current) {
            child.invalidate(registry)
            toDrop.add(child.id)
        }
        registry.drop(toDrop)
    }
}
