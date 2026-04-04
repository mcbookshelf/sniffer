package net.gunivers.sniffer.util

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object Extension {

    @JvmStatic
    fun MutableComponent.appendLine(str: String): MutableComponent =
        this.append(str).append("\n")

    @JvmStatic
    fun MutableComponent.appendLine(text: Component): MutableComponent =
        this.append(text).append("\n")

    @JvmStatic
    fun MutableComponent.appendLine(): MutableComponent =
        this.append("\n")

    class StringReaderTester(val reader: StringReader, val cursor: Int = reader.cursor) {

        private var result = true

        fun expect(char: Char): StringReaderTester {
            if(!result) return this
            return if(reader.canRead() && reader.peek() == char){
                reader.cursor++
                this
            }else{
                this.result = false
                this
            }
        }

        fun expect(str: String): StringReaderTester {
            if(!result) return this
            return if(reader.canRead(str.length) && reader.string.substring(reader.cursor, reader.cursor + str.length) == str){
                reader.cursor += str.length
                this
            }else{
                this.result = false
                this
            }
        }

        fun skipWhitespace(): StringReaderTester {
            if(!result) return this
            reader.skipWhitespace()
            return this
        }

        fun result(): Boolean{
            reader.cursor = cursor
            return result
        }
    }

    @JvmStatic
    fun StringReader.backUtil(stop: Char){
        while(this.canRead() && this.peek() != stop){
            this.cursor--
        }
    }

    @JvmStatic
    fun StringReader.readWord(): String{
        val start = this.cursor
        while(this.canRead() && !this.peek().isWhitespace()){
            this.cursor++
        }
        return this.string.substring(start, this.cursor)
    }

    @JvmStatic
    fun StringReader.readUntil(stop: Char): String {
        val start = this.cursor
        while(this.canRead() && this.peek() != stop){
            this.cursor++
        }
        return this.string.substring(start, this.cursor)
    }

    @JvmStatic
    fun StringReader.test(): StringReaderTester = StringReaderTester(this)

    @JvmStatic
    fun StringReader.test(expected: (StringReader) -> Boolean): Boolean {
        val curr = this.cursor
        val qwq = expected(this)
        this.cursor = curr
        return qwq
    }

    @JvmStatic
    fun StringReader.test(expected: Char): Boolean{
        return this.canRead() && this.peek() == expected
    }

    @JvmStatic
    fun StringReader.test(expected: String): Boolean{
        return this.canRead(expected.length) && this.string.substring(this.cursor, this.cursor + expected.length) == expected
    }

    @JvmStatic
    fun StringReader.expect(expected: String){
        val qwq = this.string.substring(this.cursor, this.cursor + expected.length)
        if(qwq != expected){
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol()
                .createWithContext(this, expected)
        }
        this.cursor += expected.length
    }
}