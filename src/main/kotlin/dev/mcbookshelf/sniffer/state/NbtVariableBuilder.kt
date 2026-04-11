package dev.mcbookshelf.sniffer.state

import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

/**
 * Pure, stateless builder that turns an NBT [Tag] into a [VariableNode] tree.
 *
 * Compound and collection tags produce lazy nodes: their children are not
 * materialized until the DAP client expands them. Primitives become leaves.
 * Replaces the old stateful `NbtElementVariableVisitor`.
 */
object NbtVariableBuilder {

    fun build(
        name: String,
        tag: Tag,
        isRoot: Boolean,
        registry: VariableRegistry,
    ): VariableNode = when (tag) {
        is CompoundTag -> registry.register { id ->
            VariableNode(id, name, tag.toString(), isRoot) { reg ->
                tag.keySet().map { key -> build(key, tag[key]!!, isRoot = false, registry = reg) }
            }
        }
        is CollectionTag -> registry.register { id ->
            VariableNode(id, name, tag.toString(), isRoot) { reg ->
                val size = tag.size()
                val result = ArrayList<VariableNode>(size)
                for (i in 0 until size) {
                    result.add(build(i.toString(), tag.get(i), isRoot = false, registry = reg))
                }
                result
            }
        }
        else -> registry.register { id ->
            VariableNode(id, name, tag.toString(), isRoot, childrenFactory = null)
        }
    }
}
