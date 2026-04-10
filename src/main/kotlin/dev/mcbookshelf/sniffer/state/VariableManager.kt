package dev.mcbookshelf.sniffer.state

import com.mojang.brigadier.StringReader
import dev.mcbookshelf.sniffer.commands.DebugData
import dev.mcbookshelf.sniffer.commands.ExprArgumentType

import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.text.MessageFormat

/**
 * Manages variable conversion and representation for the debugger.
 * Provides utility methods to convert Minecraft objects into debugger variables
 * that can be displayed in the debugging client.
 *
 * @author theogiraudet
 */
object VariableManager {

    @JvmStatic
    fun convertCommandSource(source: ExecutionCommandSource<*>, startIndex: Int): MutableMap<Int, DebuggerVariable> {
        if (source is CommandSourceStack) {
            val executorVariable = convertEntityVariables(source.entity, startIndex, true)
            var currentIndex = executorVariable.second

            val locId = currentIndex++

            val posVariable = convertPos(source.position, currentIndex, false)
            currentIndex = posVariable.second

            val rotVariable = convertRotation(source.rotation, currentIndex, false)
            currentIndex = rotVariable.second

            val worldVariable = convertWorld(source.level, currentIndex, false)

            val locationVariable = DebuggerVariable(
                locId, "location", posVariable.first.value,
                listOf(posVariable.first, rotVariable.first, worldVariable), true
            )

            val result = ArrayList<DebuggerVariable>(currentIndex)
            result.add(executorVariable.first)
            result.add(locationVariable)

            return flattenToMap(result)
        }
        throw IllegalStateException(
            "AbstractServerCommandSource is not a ServerCommandSource but a ${source.javaClass.simpleName}"
        )
    }

    private fun convertEntityVariables(entity: Entity?, startIndex: Int, isRoot: Boolean): Pair<DebuggerVariable, Int> {
        if (entity == null) {
            return Pair(
                DebuggerVariable(startIndex, "executor", "server", listOf(), isRoot),
                startIndex + 1
            )
        }

        var id = startIndex + 1

        val objectType = DebuggerVariable(id++, "type", typeToString(entity.type), listOf(), false)
        val objectName = DebuggerVariable(id++, "name", entity.name.string, listOf(), false)
        val objectUuid = DebuggerVariable(id++, "uuid", entity.stringUUID, listOf(), false)

        val pos = convertPos(entity.position(), id, false)
        id = pos.second

        val rot = convertRotation(entity.rotationVector, id, false)
        id = rot.second

        val objectDimension = convertWorld(entity.level(), id++, false)

        val displayName = objectName.value ?: objectType.value

        val children = listOf(objectType, objectName, objectUuid, pos.first, rot.first, objectDimension)
        return Pair(DebuggerVariable(startIndex, "executor", displayName, children, isRoot), id)
    }

    private fun typeToString(type: EntityType<*>): String {
        val str = type.toString()
        return str.substring(str.lastIndexOf(".") + 1)
    }

    private fun convertPos(vec: Vec3, id: Int, isRoot: Boolean): Pair<DebuggerVariable, Int> {
        var nextId = id
        val posId = nextId++
        val objectPosX = DebuggerVariable(nextId++, "x", vec.x.toString(), listOf(), false)
        val objectPosY = DebuggerVariable(nextId++, "y", vec.y.toString(), listOf(), false)
        val objectPosZ = DebuggerVariable(nextId++, "z", vec.z.toString(), listOf(), false)
        val posStr = MessageFormat.format("[{0}, {1}, {2}]", vec.x, vec.y, vec.z)
        val objectPosition = DebuggerVariable(posId, "position", posStr, listOf(objectPosX, objectPosY, objectPosZ), isRoot)
        return Pair(objectPosition, nextId)
    }

    private fun convertRotation(vec: Vec2, id: Int, isRoot: Boolean): Pair<DebuggerVariable, Int> {
        var nextId = id
        val posId = nextId++
        val objectPosX = DebuggerVariable(nextId++, "yaw", vec.x.toDouble().toString(), listOf(), false)
        val objectPosY = DebuggerVariable(nextId++, "pitch", vec.y.toDouble().toString(), listOf(), false)
        val posStr = MessageFormat.format("[{0}, {1}]", vec.x, vec.y)
        val objectPosition = DebuggerVariable(posId, "rotation", posStr, listOf(objectPosX, objectPosY), isRoot)
        return Pair(objectPosition, nextId)
    }

    private fun convertWorld(world: Level, id: Int, isRoot: Boolean): DebuggerVariable =
        DebuggerVariable(id, "world", world.dimension().identifier().path, listOf(), isRoot)

    private fun flattenToMap(variables: List<DebuggerVariable>): MutableMap<Int, DebuggerVariable> {
        val map = HashMap<Int, DebuggerVariable>()
        variables.forEach { flattenToMap(it, map) }
        return map
    }

    private fun flattenToMap(variable: DebuggerVariable, variables: MutableMap<Int, DebuggerVariable>) {
        variables[variable.id] = variable
        variable.children.forEach { flattenToMap(it, variables) }
    }

    @JvmStatic
    fun convertNbtCompound(name: String, compound: CompoundTag?, startIndex: Int, isRoot: Boolean): Map<Int, DebuggerVariable> {
        if (compound != null) {
            val visitor = NbtElementVariableVisitor(startIndex, name, isRoot)
            compound.accept(visitor)
            return visitor.get()
        }
        return emptyMap()
    }

    @JvmStatic
    fun evaluate(expression: String): Result<DebugData> {
        val trimmed = expression.trim()
        return runCatching {
            ExprArgumentType().parseArgumentWithoutBrackets(StringReader(trimmed))
        }
    }
}
