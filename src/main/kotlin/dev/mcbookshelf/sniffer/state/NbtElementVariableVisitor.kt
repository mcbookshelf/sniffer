package dev.mcbookshelf.sniffer.state

import net.minecraft.nbt.*
import java.util.LinkedList

/**
 * A visitor implementation for NBT elements that converts them into debugger variables.
 * This class traverses NBT data structures and creates corresponding DebuggerVariable objects
 * that can be displayed in a debugging client.
 *
 * @author theogiraudet
 */
class NbtElementVariableVisitor(
    private var index: Int,
    private var currentName: String,
    private var isRoot: Boolean
) : TagVisitor {

    private val variables = HashMap<Int, DebuggerVariable>()
    private lateinit var returnVariable: DebuggerVariable

    fun get(): Map<Int, DebuggerVariable> = variables

    override fun visitString(element: StringTag) = convertPrimitive(element)
    override fun visitByte(element: ByteTag) = convertPrimitive(element)
    override fun visitShort(element: ShortTag) = convertPrimitive(element)
    override fun visitInt(element: IntTag) = convertPrimitive(element)
    override fun visitLong(element: LongTag) = convertPrimitive(element)
    override fun visitFloat(element: FloatTag) = convertPrimitive(element)
    override fun visitDouble(element: DoubleTag) = convertPrimitive(element)

    override fun visitByteArray(element: ByteArrayTag) = convertList(element)
    override fun visitIntArray(element: IntArrayTag) = convertList(element)
    override fun visitLongArray(element: LongArrayTag) = convertList(element)
    override fun visitList(element: ListTag) = convertList(element)

    override fun visitCompound(compound: CompoundTag) {
        val children = LinkedList<DebuggerVariable>()
        val compoundIndex = index++
        val compoundVar = DebuggerVariable(compoundIndex, currentName, compound.toString(), children, isRoot)
        isRoot = false
        variables[compoundIndex] = compoundVar
        for (key in compound.keySet()) {
            currentName = key
            compound[key]!!.accept(this)
            children.add(returnVariable)
        }
        returnVariable = compoundVar
    }

    override fun visitEnd(element: EndTag) {}

    private fun convertList(list: CollectionTag) {
        val arrayIndex = index++
        val array = LinkedList<DebuggerVariable>()
        val name = currentName
        val result = DebuggerVariable(arrayIndex, name, list.toString(), array, false)
        variables[arrayIndex] = result
        for (i in 0 until list.size()) {
            currentName = index.toString()
            list.get(i).accept(this)
            array.add(returnVariable)
        }
        index++
        returnVariable = result
    }

    private fun convertPrimitive(element: Tag) {
        val i = index++
        returnVariable = DebuggerVariable(i, currentName, element.toString(), emptyList(), isRoot)
        variables[i] = returnVariable
    }
}
