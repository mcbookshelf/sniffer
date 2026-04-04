package net.gunivers.sniffer.debugcmd

import com.mojang.brigadier.Message
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.gunivers.sniffer.util.Extension.expect
import net.gunivers.sniffer.util.Extension.readUntil
import net.gunivers.sniffer.util.Extension.readWord
import net.gunivers.sniffer.util.Extension.test
import net.gunivers.sniffer.util.ReflectUtil
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.*
import net.minecraft.commands.arguments.NbtPathArgument.nbtPath
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.commands.arguments.coordinates.Coordinates
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.nbt.*
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.commands.data.BlockDataAccessor
import net.minecraft.server.commands.data.DataCommands
import net.minecraft.server.commands.data.EntityDataAccessor
import net.minecraft.server.commands.data.StorageDataAccessor

class LogArgumentType: ArgumentType<LogArgumentType.Companion.Log> {
    @Suppress("unused", "PrivatePropertyName")
    private val EXAMPLES = mutableListOf("qwq {(some thing) == 1}")

    override fun getExamples(): MutableCollection<String> = EXAMPLES

    override fun parse(reader: StringReader): Log {
        val log = Log()
        while (reader.canRead()){
            if(reader.test('{')){
                //an argument
                log.logs.add(ExprArgumentType().parse(reader))
            }else {
                //plain text
                log.logs.add(PlainData(reader.readUntil('{')))
            }
        }
        return log
    }

    companion object {
        class Log(val logs: ArrayList<DebugData> = arrayListOf())

        @JvmStatic
        fun getLog(context: CommandContext<*>, name: String?): Log {
            return context.getArgument(name, Log::class.java)
        }

        @JvmStatic
        fun log() = LogArgumentType()
    }
}

class ExprArgumentType: ArgumentType<ExprArgumentType.Companion.Experiment> {

    override fun parse(reader: StringReader): Experiment {
        val startIndex = reader.cursor
        reader.expect('{')
        reader.skipWhitespace()
        var first: DebugData? = null
        var op: String? = null
        val ops = ArrayList<Pair<String, DebugData?>>()
        while(reader.canRead() && !reader.test('}')){
            //Testing whether the next argument is a value or a variable.
            if(reader.test('(')){
                //is a parameter
                val arg = parseArgument(reader)
                if(first == null && ops.isEmpty()){
                    first = arg
                }else{
                    if(op == null){
                        throw MISSING_OP_ERROR.createWithContext(reader)
                    }
                    ops.add(op to arg)
                }
            }else if(reader.test('{')){
                //is another experiment
                if(op == null){
                    throw MISSING_OP_ERROR.createWithContext(reader)
                }
                ops.add(op to parse(reader))
            }else {
                //is an operation or a value
                val isOp = reader.test { supportedOps.contains(reader.readWord()) }
                if(isOp){
                    op = reader.readWord()
                }else{
                    //is a value
                    val arg = PlainData(NbtTagArgument.nbtTag().parse(reader))
                    if(first == null && ops.isEmpty()){
                        first = arg
                    }else{
                        if(op == null){
                            throw MISSING_OP_ERROR.createWithContext(reader)
                        }
                        ops.add(op to arg)
                    }
                }
            }
            reader.skipWhitespace()
        }
        if(ops.isEmpty() && first == null){
            throw EMPTY_EXPR_ERROR.createWithContext(reader)
        }
        if(ops.isNotEmpty() && ops.last().second == null){
            throw MISSING_OP_ERROR.createWithContext(reader)
        }
        reader.expect('}')
        val endIndex = reader.cursor
        return Experiment(first!!, ops.map { (op, arg) -> op to arg!! }, reader.string.substring(startIndex, endIndex))
    }

    fun parseArgumentWithoutBrackets(reader: StringReader): DebugData{
        val qwq = if(reader.test("data")){
            //is data
            DataArgumentType().parse(reader)
        }else if(reader.test("score")){
            //is score
            ScoreArgumentType().parse(reader)
        }else if(reader.test("name")){
            //is a name
            EntityNameType().parse(reader)
        }else {
            PlainData("")
        }
        return qwq
    }

    fun parseArgument(reader: StringReader): DebugData {
        reader.expect('(')
        reader.skipWhitespace()
        val qwq = parseArgumentWithoutBrackets(reader)
        reader.skipWhitespace()
        reader.expect(')')
        return qwq
    }

    companion object {

        @JvmStatic
        fun expr() = ExprArgumentType()

        @JvmStatic
        fun getExpr(context: CommandContext<*>, name: String?): Experiment {
            return context.getArgument(name, Experiment::class.java)
        }

        class Experiment(val first: DebugData?, val ops: List<Pair<String, DebugData>>, val content: String): DebugData {
            override fun get(source: CommandSourceStack): Any {
                var qwq = first?.get(source)
                for ((op, arg) in ops){
                    val argValue = arg.get(source)
                    if(op == "||" && qwq is ByteTag && qwq.value == 1.toByte()) continue
                    if(op == "&&" && qwq is ByteTag && qwq.value == 0.toByte()) continue
                    qwq = supportedOps[op]!!.apply(qwq, argValue)
                }
                return qwq!!
            }
        }

        private val supportedOps = mapOf(
            "+" to object: Operation("+"){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is NumericTag && right is NumericTag){
                        return if(left is DoubleTag || right is DoubleTag){
                            DoubleTag.valueOf(left.doubleValue() + right.doubleValue())
                        }else if(left is FloatTag || right is FloatTag){
                            FloatTag.valueOf(left.floatValue() + right.floatValue())
                        }else if(left is LongTag || right is LongTag){
                            LongTag.valueOf(left.longValue() + right.longValue())
                        }else {
                            IntTag.valueOf(left.intValue() + right.intValue())
                        }
                    }else if(left is Component && right is Component){
                        return Component.empty().append(left).append(right)
                    }else if(left is CompoundTag && right is CompoundTag){
                        return left.merge(right)
                    }else if(left is ListTag && right is ListTag) {
                        return left.addAll(right)
                    }else if(left is StringTag && right is StringTag){
                        return StringTag.valueOf(left.value + right.value)
                    }else if(left is Component && right is StringTag){
                        return StringTag.valueOf(left.string + right.value)
                    }else if(left is StringTag && right is Component){
                        return StringTag.valueOf(left.value + right.string)
                    }
                    else {
                        throw OPERATION_TYPE_ERROR.create(name, left?.javaClass, right.javaClass)
                    }
                }
            },
            "-" to object: Operation("-"){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is NumericTag && right is NumericTag){
                        return if(left is DoubleTag || right is DoubleTag){
                            DoubleTag.valueOf(left.doubleValue() - right.doubleValue())
                        }else if(left is FloatTag || right is FloatTag){
                            FloatTag.valueOf(left.floatValue() - right.floatValue())
                        }else if(left is LongTag || right is LongTag){
                            LongTag.valueOf(left.longValue() - right.longValue())
                        }else {
                            IntTag.valueOf(left.intValue() - right.intValue())
                        }
                    }else {
                        throw buildOperationTypeError(left, right)
                    }
                }
            },
            "*" to object: Operation("*"){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is NumericTag && right is NumericTag){
                        return if(left is DoubleTag || right is DoubleTag){
                            DoubleTag.valueOf(left.doubleValue() * right.doubleValue())
                        }else if(left is FloatTag || right is FloatTag){
                            FloatTag.valueOf(left.floatValue() * right.floatValue())
                        }else if(left is LongTag || right is LongTag){
                            LongTag.valueOf(left.longValue() * right.longValue())
                        }else {
                            IntTag.valueOf(left.intValue() * right.intValue())
                        }
                    }else {
                        throw buildOperationTypeError(left, right)
                    }
                }
            },
            "/" to object: Operation("/"){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is NumericTag && right is NumericTag){
                        return if(left is DoubleTag || right is DoubleTag){
                            DoubleTag.valueOf(left.doubleValue() / right.doubleValue())
                        }else if(left is FloatTag || right is FloatTag){
                            FloatTag.valueOf(left.floatValue() / right.floatValue())
                        }else if(left is LongTag || right is LongTag){
                            LongTag.valueOf(left.longValue() / right.longValue())
                        }else {
                            IntTag.valueOf(left.intValue() / right.intValue())
                        }
                    }else {
                        throw buildOperationTypeError(left, right)
                    }
                }
            },
            "<" to object: Operation("<"){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is NumericTag && right is NumericTag){
                        return ByteTag.valueOf(left.doubleValue() < right.doubleValue())
                    }else {
                        throw buildOperationTypeError(left, right)
                    }
                }
            },
            ">" to object: Operation(">"){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is NumericTag && right is NumericTag){
                        return ByteTag.valueOf(left.doubleValue() > right.doubleValue())
                    }else {
                        throw buildOperationTypeError(left, right)
                    }
                }
            },
            "<=" to object: Operation("<="){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is NumericTag && right is NumericTag){
                        return ByteTag.valueOf(left.doubleValue() <= right.doubleValue())
                    }else {
                        throw buildOperationTypeError(left, right)
                    }
                }
            },
            ">=" to object: Operation(">="){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is NumericTag && right is NumericTag){
                        return ByteTag.valueOf(left.doubleValue() >= right.doubleValue())
                    }else {
                        throw buildOperationTypeError(left, right)
                    }
                }
            },
            "==" to object: Operation("=="){
                override fun apply(left: Any?, right: Any): Any {
                    if(left is Tag && right is Tag) return ByteTag.valueOf(left == right)
                    if(left is Component && right is StringTag) return ByteTag.valueOf(left.string == right.value)
                    if(left is StringTag && right is Component) return ByteTag.valueOf(left.value == right.string)
                    return false
                }
            },
            "!=" to object: Operation("!=") {
                override fun apply(left: Any?, right: Any): Any {
                    if (left is Tag && right is Tag) return ByteTag.valueOf(left != right)
                    if (left is Component && right is StringTag) return ByteTag.valueOf(left.string != right.value)
                    if (left is StringTag && right is Component) return ByteTag.valueOf(left.value != right.string)
                    return true
                }
            },
            "is" to object: Operation("is") {
                override fun apply(left: Any?, right: Any): Any {
                    if(right !is StringTag) throw buildOperationTypeError(left, right)
                    val qwq = when(right.value){
                        "nbt" -> left is Tag
                        "text" -> left is Component
                        "string" -> left is StringTag
                        "number" -> left is NumericTag
                        "byte" -> left is ByteTag
                        "short" -> left is ShortTag
                        "int" -> left is IntTag
                        "long" -> left is LongTag
                        "float" -> left is FloatTag
                        "double" -> left is DoubleTag
                        "int_array" -> left is IntArrayTag
                        "long_array" -> left is LongArrayTag
                        "byte_array" -> left is ByteArrayTag
                        "list" -> left is ListTag
                        "compound" -> left is CompoundTag
                        else -> false
                    }
                    return ByteTag.valueOf(qwq)
                }
            },
            "!" to object: Operation("!"){
                override fun apply(left: Any?, right: Any): Any {
                    if(right !is ByteTag) throw buildOperationTypeError(left, right)
                    return ByteTag.valueOf(!right.asBoolean().get())
                }
            },
            "||" to object: Operation("||"){
                override fun apply(left: Any?, right: Any): Any {
                    if(left !is ByteTag || right !is ByteTag) throw buildOperationTypeError(left, right)
                    return ByteTag.valueOf(left.asBoolean().get() || right.asBoolean().get())
                }
            },
            "&&" to object: Operation("&&"){
                override fun apply(left: Any?, right: Any): Any {
                    if(left !is ByteTag || right !is ByteTag) throw buildOperationTypeError(left, right)
                    return ByteTag.valueOf(left.asBoolean().get() && right.asBoolean().get())
                }
            }
        )

        abstract class Operation(val name: String){
            abstract fun apply(left: Any?, right: Any): Any
            override fun toString(): String {
                return name
            }
            fun buildOperationTypeError(left: Any?, right: Any) = 
                OPERATION_TYPE_ERROR.create(name, left?.javaClass?.simpleName, right.javaClass.simpleName)
            
        }

        private val EMPTY_EXPR_ERROR = SimpleCommandExceptionType { "Empty expression" }
        private val MISSING_OP_ERROR = SimpleCommandExceptionType { "Missing operation between arguments" }
        private val MISSING_ARG_ERROR = SimpleCommandExceptionType { "Missing arguments after operation" }
        private val OPERATION_TYPE_ERROR = Dynamic3CommandExceptionType { operation, left, right -> Message { "Operation $operation is not applicable to $left and $right" }}

    }

}

interface DebugData {
    fun get(source: CommandSourceStack): Any

    companion object {
        fun toText(any: Any): Component{
            return when(any){
                is Tag -> NbtUtils.toPrettyComponent(any)
                is Component -> any
                else -> Component.literal(any.toString())
            }
        }
    }
}

class PlainData(private val value: Any): DebugData {
    override fun get(source: CommandSourceStack): Any {
        return value
    }
}

class EntityNameType: ArgumentType<EntityNameType.Companion.Name>{
    override fun parse(reader: StringReader): Name {
        reader.skipWhitespace()
        reader.expect("name")
        reader.skipWhitespace()
        val name = MessageArgument.message().parse(reader)
        return Name(name)
    }

    companion object {
        class Name(val msg: MessageArgument.Message): DebugData {
            override fun get(source: CommandSourceStack): Any {
                return msg.toComponent(source, true)
            }

        }
    }
}

class DataArgumentType: ArgumentType<DataArgumentType.Companion.Data> {

    override fun parse(reader: StringReader): Data {
        reader.skipWhitespace()
        reader.expect("data")
        reader.skipWhitespace()
        val qwq = when(reader.readUnquotedString()){
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
                val id = ResourceLocationArgument.id().parse(reader)
                reader.skipWhitespace()
                val path = nbtPath().parse(reader)
                StorageDataSource(id, path)
            }
            else -> throw INVALID_OBJECT_ERROR.createWithContext(reader)
        }
        reader.skipWhitespace()
        return Data(qwq)
    }

    companion object {

        private val INVALID_OBJECT_ERROR = SimpleCommandExceptionType { "Invalid object type for data argument" }
        val INVALID_BLOCK_EXCEPTION = SimpleCommandExceptionType(Component.translatable("commands.data.block.invalid"))

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

        private class StorageDataSource(val id: ResourceLocation, val path: NbtPathArgument.NbtPath): DataSource {
            override fun getNbtElement(source: CommandSourceStack): Tag {
                return DataCommands.getSingleTag(path, ReflectUtil.newInstance(StorageDataAccessor::class.java, source.server.commandStorage, id).data)
            }
        }
    }
}

class ScoreArgumentType: ArgumentType<ScoreArgumentType.Companion.Score> {

    @Suppress("unused", "PrivatePropertyName")
    private val EXAMPLES = listOf("{score @s test}", "{score qwq uwu}")
    @Suppress("PrivatePropertyName")
    private val ERROR = SimpleCommandExceptionType { "Invalid score argument" }

    override fun parse(reader: StringReader): Score {
        skipWhitespace(reader)
        val keyword = reader.readUnquotedString()
        //check keyword
        if("score" != keyword){
            throw ERROR.createWithContext(reader)
        }
        skipWhitespace(reader)
        //read selector
        val scoreHolder = ScoreHolderArgument.scoreHolder().parse(reader)
        skipWhitespace(reader)
        val objective = ObjectiveArgument.objective().parse(reader)
        return Score(scoreHolder, objective)
    }

    companion object {
        class Score (
            val scoreHolder: ScoreHolderArgument.Result,
            val objective: String
        ): DebugData {
            override fun get(source: CommandSourceStack): Any {
                val scoreboard = source.server.scoreboard
                val holder = scoreHolder.getNames(source) { ArrayList() }.last()
                val scoreboardObjective = scoreboard.getObjective(objective)
                    ?: throw DynamicCommandExceptionType {
                        Component.translatable("arguments.objective.notFound", *arrayOf(it))
                    }.create(objective)
                val readableScoreboardScore = scoreboard.getOrCreatePlayerScore(holder, scoreboardObjective)
                if (readableScoreboardScore == null) {
                    throw PLAYERS_GET_NULL_EXCEPTION.create(
                        scoreboardObjective.name,
                        holder.displayName
                    )
                } else {
                    return IntTag.valueOf(readableScoreboardScore.get())
                }
            }

        }

        fun score(): ScoreArgumentType = ScoreArgumentType()

        private fun skipWhitespace(reader: StringReader) {
            while (reader.canRead() && Character.isWhitespace(reader.peek())) reader.skip()
        }
        private val PLAYERS_GET_NULL_EXCEPTION = Dynamic2CommandExceptionType { objective: Any?, target: Any? ->
            Component.translatable(
                "commands.scoreboard.players.get.null",
                objective,
                target
            )
        }
    }
}