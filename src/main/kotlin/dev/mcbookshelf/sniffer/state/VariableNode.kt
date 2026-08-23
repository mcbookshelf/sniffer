package dev.mcbookshelf.sniffer.state

/**
 * A single variable displayed in the debugger.
 *
 * Children are produced by [childrenFactory] the first time [children] is asked for,
 * and registered with the [VariableRegistry] so the DAP client can reference them.
 * [invalidate] then drops them again, which is how a node rebuilds from fresh game state.
 *
 * @param id the reference the DAP client uses to ask for the children of this node
 * @param isRoot whether the node belongs directly to its scope rather than under another variable
 * @param childrenFactory `null` for a leaf
 * @author theogiraudet
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
     * Drops the memoized children recursively and removes their ids from [registry].
     * The node itself stays registered, only its subtree is evicted.
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
