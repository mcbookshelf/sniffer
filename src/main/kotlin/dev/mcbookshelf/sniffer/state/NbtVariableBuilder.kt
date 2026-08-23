package dev.mcbookshelf.sniffer.state

import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

/**
 * Turns an NBT [Tag] into a [VariableNode] tree.
 * Compounds and collections keep their children unbuilt until the client expands them, and primitives are leaves.
 *
 * @author theogiraudet
 * @author Alumopper
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
