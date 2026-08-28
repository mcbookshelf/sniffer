package dev.mcbookshelf.sniffer.expression

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.mcbookshelf.sniffer.util.Extension.readUntil
import dev.mcbookshelf.sniffer.util.Extension.test

/**
 * Argument type of the `#!log` command, parsing plain text interleaved with `{ ... }` expression blocks.
 * The blocks are evaluated at runtime, so scores, data, names and arithmetic can be printed inline.
 *
 * @author Alumopper
 */
class LogArgumentType: ArgumentType<LogArgumentType.Companion.Log> {
    @Suppress("unused", "PrivatePropertyName")
    private val EXAMPLES = mutableListOf("hello {(some thing) == 1}")

    override fun getExamples(): MutableCollection<String> = EXAMPLES

    override fun parse(reader: StringReader): Log {
        val log = Log()
        while (reader.canRead()){
            if(reader.test('{')){
                log.logs.add(ExprArgumentType().parse(reader))
            }else {
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
