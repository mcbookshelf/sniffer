package dev.mcbookshelf.sniffer.expression

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.mcbookshelf.sniffer.util.Extension.expect
import dev.mcbookshelf.sniffer.util.Extension.readUntil
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.commands.arguments.NbtPathArgument
import net.minecraft.commands.arguments.NbtPathArgument.nbtPath
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.commands.arguments.coordinates.Coordinates
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.commands.data.BlockDataAccessor
import net.minecraft.server.commands.data.DataCommands
import net.minecraft.server.commands.data.EntityDataAccessor
import net.minecraft.server.commands.data.StorageDataAccessor

/**
 * Parses `data <block|entity|storage> ...`, yielding the NBT it points at.
 *
 * @author Alumopper
 */
class DataArgumentType: ArgumentType<DataArgumentType.Data> {

    override fun parse(reader: StringReader): Data {
        reader.skipWhitespace()
        reader.expect("data")
        reader.skipWhitespace()
        val dataSource = when(reader.readUnquotedString()){
            "block" -> {
                reader.skipWhitespace()
                val pos = BlockPosArgument.blockPos().parse(reader)
                reader.skipWhitespace()
                val path = nbtPath().parse(reader)
                BlockDataSource(pos, path)
            }
            "entity" -> {
                reader.skipWhitespace()
                val selector = EntityArgument.entity().parse(reader)
                reader.skipWhitespace()
                val path = nbtPath().parse(reader)
                EntityDataSource(selector, path)
            }
            "storage" -> {
                reader.skipWhitespace()
                val id = IdentifierArgument.id().parse(reader)
                reader.skipWhitespace()
                val path = nbtPath().parse(reader)
                StorageDataSource(id, path)
            }
            else -> throw INVALID_OBJECT_ERROR.createWithContext(reader)
        }
        reader.skipWhitespace()
        return Data(dataSource)
    }

    class Data(val data: DataSource): DebugData {
        override fun get(source: CommandSourceStack): Any {
            return data.getNbtElement(source)
        }
    }

    interface DataSource {
            fun getNbtElement(source: CommandSourceStack): Tag
    }

    private class EntityDataSource(val selector: EntitySelector, val path: NbtPathArgument.NbtPath) : DataSource {
        override fun getNbtElement(source: CommandSourceStack): Tag {
            return DataCommands.getSingleTag(path, EntityDataAccessor(selector.findSingleEntity(source)))
        }
    }

    private class BlockDataSource(val pos: Coordinates, val path: NbtPathArgument.NbtPath): DataSource {
        override fun getNbtElement(source: CommandSourceStack): Tag {
            val blockPos = pos.getBlockPos(source)
            val world = source.level
            if (!world.isLoaded(blockPos)) {
                throw BlockPosArgument.ERROR_NOT_LOADED.create()
            } else if (!world.isOutsideBuildHeight(blockPos)) {
                throw BlockPosArgument.ERROR_OUT_OF_WORLD.create()
            }
            val blockEntity = source.level.getBlockEntity(blockPos) ?: throw INVALID_BLOCK_EXCEPTION.create()
            return DataCommands.getSingleTag(path, BlockDataAccessor(blockEntity, blockPos))
        }
    }

    private class StorageDataSource(val id: Identifier, val path: NbtPathArgument.NbtPath): DataSource {
        override fun getNbtElement(source: CommandSourceStack): Tag {
            return DataCommands.getSingleTag(path, StorageDataAccessor(source.server.commandStorage, id))
        }
    }

    companion object {

        private val INVALID_OBJECT_ERROR = SimpleCommandExceptionType { "Invalid object type for data argument" }
        val INVALID_BLOCK_EXCEPTION = SimpleCommandExceptionType(Component.translatable("commands.data.block.invalid"))

    }
}
