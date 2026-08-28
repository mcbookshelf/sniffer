package dev.mcbookshelf.sniffer.expression

import com.mojang.brigadier.Message
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.mcbookshelf.sniffer.util.Extension.expect
import dev.mcbookshelf.sniffer.util.Extension.readUntil
import dev.mcbookshelf.sniffer.util.Extension.readWord
import dev.mcbookshelf.sniffer.util.Extension.test
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.NbtTagArgument
import net.minecraft.nbt.*
import net.minecraft.network.chat.Component

/**
 * Argument type of a `{ ... }` block, the expression mini language of the debug commands.
 *
 * @author Alumopper
 */
class ExprArgumentType: ArgumentType<ExprArgumentType.Experiment> {

    override fun parse(reader: StringReader): Experiment {
        val startIndex = reader.cursor
        reader.expect('{')
        reader.skipWhitespace()
        var first: DebugData? = null
        var op: String? = null
        val ops = ArrayList<Pair<String, DebugData?>>()
        while(reader.canRead() && !reader.test('}')){
            if(reader.test('(')){
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
                if(op == null){
                    throw MISSING_OP_ERROR.createWithContext(reader)
                }
                ops.add(op to parse(reader))
            }else {
                val isOp = reader.test { supportedOps.contains(reader.readWord()) }
                if(isOp){
                    op = reader.readWord()
                }else{
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
        val parsedData = if(reader.test("data")){
            DataArgumentType().parse(reader)
        }else if(reader.test("score")){
            ScoreArgumentType().parse(reader)
        }else if(reader.test("name")){
            EntityNameType().parse(reader)
        }else {
            PlainData("")
        }
        return parsedData
    }

    fun parseArgument(reader: StringReader): DebugData {
        reader.expect('(')
        val argumentReader = StringReader(reader.readUntil(')'))
        argumentReader.skipWhitespace()
        val parsedData = parseArgumentWithoutBrackets(argumentReader)
        argumentReader.skipWhitespace()
        if(argumentReader.canRead()){
            throw INVALID_ARG_ERROR.createWithContext(argumentReader)
        }
        reader.expect(')')
        return parsedData
    }

    class Experiment(val first: DebugData?, val ops: List<Pair<String, DebugData>>, val content: String): DebugData {
        override fun get(source: CommandSourceStack): Any {
            var accumulator = first?.get(source)
            for ((op, arg) in ops){
                val argValue = arg.get(source)
                if(op == "||" && accumulator is ByteTag && accumulator.value == 1.toByte()) continue
                if(op == "&&" && accumulator is ByteTag && accumulator.value == 0.toByte()) continue
                accumulator = supportedOps[op]!!.apply(accumulator, argValue)
            }
            return accumulator!!
        }
    }

    abstract class Operation(val name: String){
        abstract fun apply(left: Any?, right: Any): Any
        override fun toString(): String {
            return name
        }
        fun buildOperationTypeError(left: Any?, right: Any) = 
            OPERATION_TYPE_ERROR.create(name, left?.javaClass?.simpleName, right.javaClass.simpleName)
        
    }

    companion object {

        @JvmStatic
        fun expr() = ExprArgumentType()

        @JvmStatic
        fun getExpr(context: CommandContext<*>, name: String?): Experiment {
            return context.getArgument(name, Experiment::class.java)
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
                    val matches = when(right.value){
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
                    return ByteTag.valueOf(matches)
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

        private val EMPTY_EXPR_ERROR = SimpleCommandExceptionType { "Empty expression" }
        private val INVALID_ARG_ERROR = SimpleCommandExceptionType { "Invalid expression argument" }
        private val MISSING_OP_ERROR = SimpleCommandExceptionType { "Missing operation between arguments" }
        private val MISSING_ARG_ERROR = SimpleCommandExceptionType { "Missing arguments after operation" }
        private val OPERATION_TYPE_ERROR = Dynamic3CommandExceptionType { operation, left, right -> Message { "Operation $operation is not applicable to $left and $right" }}

    }

}
