package dev.mcbookshelf.sniffer.state

import com.mojang.brigadier.StringReader
import dev.mcbookshelf.sniffer.commands.DebugData
import dev.mcbookshelf.sniffer.commands.ExprArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.text.MessageFormat

/**
 * Produces the lazy [VariableNode] trees a [DebugScope] exposes.
 *
 * Everything is lazy past the root level: children lambdas close over the
 * live [Entity] / [CommandSourceStack] so they re-read state on every call.
 * Between-pause invalidation ([ScopeManager.refreshForPause]) drops those
 * cached children so the next DAP `variables` request rebuilds from current
 * entity position, rotation, etc.
 *
 * @author theogiraudet
 */
object VariableManager {

    /**
     * Builds the ordered list of root-level variables for a debug scope.
     * Each entry is already registered with [registry].
     */
    fun buildRootVariables(
        source: ExecutionCommandSource<*>,
        macroVariables: CompoundTag?,
        registry: VariableRegistry,
    ): List<VariableNode> {
        if (source !is CommandSourceStack) {
            throw IllegalStateException(
                "AbstractServerCommandSource is not a ServerCommandSource but a ${source.javaClass.simpleName}"
            )
        }
        val result = ArrayList<VariableNode>(3)
        result.add(registry.register { id -> buildExecutorNode(source, id) })
        result.add(registry.register { id -> buildLocationNode(source, id) })
        if (macroVariables != null) {
            result.add(NbtVariableBuilder.build("macro", macroVariables, isRoot = true, registry = registry))
        }
        return result
    }

    private fun buildExecutorNode(source: CommandSourceStack, id: Int): VariableNode {
        val entity = source.entity
        if (entity == null) {
            return VariableNode(id, "executor", "server", isRoot = true, childrenFactory = null)
        }
        val displayName = entity.name.string
        return VariableNode(id, "executor", displayName, isRoot = true) { reg ->
            buildEntityChildren(entity, source, reg)
        }
    }

    private fun buildEntityChildren(
        entity: Entity,
        source: CommandSourceStack,
        reg: VariableRegistry,
    ): List<VariableNode> {
        val children = ArrayList<VariableNode>(7)
        children.add(reg.register { id -> leaf(id, "type", typeToString(entity.type)) })
        children.add(reg.register { id -> leaf(id, "name", entity.name.string) })
        children.add(reg.register { id -> leaf(id, "uuid", entity.stringUUID) })
        children.add(reg.register { id -> posNode(id, "position", entity.position()) })
        children.add(reg.register { id -> rotNode(id, "rotation", entity.rotationVector) })
        children.add(reg.register { id -> worldNode(id, "world", entity.level()) })
        children.add(reg.register { id -> buildEntityNbtNode(entity, source, id) })
        return children
    }

    private fun buildEntityNbtNode(entity: Entity, source: CommandSourceStack, id: Int): VariableNode =
        VariableNode(id, "nbt", "{...}", isRoot = false) { reg ->
            val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, source.registryAccess())
            entity.saveWithoutId(output)
            val compound = output.buildResult()
            compound.keySet().map { key ->
                NbtVariableBuilder.build(key, compound[key]!!, isRoot = false, registry = reg)
            }
        }

    private fun buildLocationNode(source: CommandSourceStack, id: Int): VariableNode {
        val pos = source.position
        val summary = MessageFormat.format("[{0}, {1}, {2}]", pos.x, pos.y, pos.z)
        return VariableNode(id, "location", summary, isRoot = true) { reg ->
            listOf(
                reg.register { cid -> posNode(cid, "position", source.position) },
                reg.register { cid -> rotNode(cid, "rotation", source.rotation) },
                reg.register { cid -> worldNode(cid, "world", source.level) },
            )
        }
    }

    private fun posNode(id: Int, name: String, vec: Vec3): VariableNode {
        val summary = MessageFormat.format("[{0}, {1}, {2}]", vec.x, vec.y, vec.z)
        return VariableNode(id, name, summary, isRoot = false) { reg ->
            listOf(
                reg.register { cid -> leaf(cid, "x", vec.x.toString()) },
                reg.register { cid -> leaf(cid, "y", vec.y.toString()) },
                reg.register { cid -> leaf(cid, "z", vec.z.toString()) },
            )
        }
    }

    private fun rotNode(id: Int, name: String, vec: Vec2): VariableNode {
        val summary = MessageFormat.format("[{0}, {1}]", vec.x, vec.y)
        return VariableNode(id, name, summary, isRoot = false) { reg ->
            listOf(
                reg.register { cid -> leaf(cid, "yaw", vec.x.toDouble().toString()) },
                reg.register { cid -> leaf(cid, "pitch", vec.y.toDouble().toString()) },
            )
        }
    }

    private fun worldNode(id: Int, name: String, world: Level): VariableNode =
        leaf(id, name, world.dimension().identifier().path)

    private fun leaf(id: Int, name: String, value: String?): VariableNode =
        VariableNode(id, name, value ?: "null", isRoot = false, childrenFactory = null)

    private fun typeToString(type: EntityType<*>): String {
        val str = type.toString()
        return str.substring(str.lastIndexOf(".") + 1)
    }

    @JvmStatic
    fun evaluate(expression: String): Result<DebugData> {
        val trimmed = expression.trim()
        return runCatching {
            ExprArgumentType().parseArgumentWithoutBrackets(StringReader(trimmed))
        }
    }
}
