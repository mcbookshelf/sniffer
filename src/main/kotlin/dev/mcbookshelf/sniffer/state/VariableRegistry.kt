package dev.mcbookshelf.sniffer.state

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single owner of every [VariableNode] the DAP client can reference.
 *
 * Both scope-owned variables and expression-evaluation variables live here,
 * in one monotonic ID space. Nodes are registered with a factory that
 * receives the freshly allocated ID so the node can embed it.
 *
 * The registry is instance-based (one per server lifetime) but is reached
 * statically through [ScopeManager.get].registry so mixins don't need DI.
 */
class VariableRegistry {

    private val nodes = ConcurrentHashMap<Int, VariableNode>()
    private val nextId = AtomicInteger(1)

    /**
     * Allocates the next ID, builds the node with [factory], stores and returns it.
     */
    fun register(factory: (Int) -> VariableNode): VariableNode {
        val id = nextId.getAndIncrement()
        val node = factory(id)
        nodes[id] = node
        return node
    }

    fun get(id: Int): VariableNode? = nodes[id]

    fun drop(ids: Iterable<Int>) {
        for (id in ids) nodes.remove(id)
    }

    fun clear() {
        nodes.clear()
        nextId.set(1)
    }
}
