package dev.mcbookshelf.sniffer.state

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns every [VariableNode] the DAP client can reference, scope variables and evaluated ones alike,
 * in a single id space.
 * A node is registered through a factory receiving the id just allocated, so the node can hold it.
 *
 * @author theogiraudet
 */
class VariableRegistry {

    private val nodes = ConcurrentHashMap<Int, VariableNode>()
    private val nextId = AtomicInteger(1)

    /** Allocates the next id, builds the node with [factory], then stores and returns it. */
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
