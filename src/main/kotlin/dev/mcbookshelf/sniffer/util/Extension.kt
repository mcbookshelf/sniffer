package dev.mcbookshelf.sniffer.util

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Extension functions for Minecraft [MutableComponent] and Brigadier [StringReader].
 *
 * @author Alumopper
 */
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
    fun StringReader.test(expected: (StringReader) -> Boolean): Boolean {
        val curr = this.cursor
        val result = expected(this)
        this.cursor = curr
        return result
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
        val actual = this.string.substring(this.cursor, this.cursor + expected.length)
        if(actual != expected){
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedSymbol()
                .createWithContext(this, expected)
        }
        this.cursor += expected.length
    }
}