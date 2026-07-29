package dev.mcbookshelf.sniffer.commands

import com.mojang.brigadier.StringReader
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ExprArgumentTypeTest {

    @Test
    fun `data argument parses without whitespace before closing parenthesis`() {
        assertParses("{(data storage demo:test value)}")
    }

    @Test
    fun `data argument still parses with whitespace before closing parenthesis`() {
        assertParses("{(data storage demo:test value )}")
    }

    @Test
    fun `name argument parses without whitespace before closing parenthesis`() {
        assertParses("{(name @s)}")
    }

    @Test
    fun `name argument still parses with whitespace before closing parenthesis`() {
        assertParses("{(name @s )}")
    }

    @Test
    fun `score argument still parses without whitespace before closing parenthesis`() {
        assertParses("{(score @s test)}")
    }

    private fun assertParses(expression: String) {
        val reader = StringReader(expression)

        ExprArgumentType().parse(reader)

        assertFalse(reader.canRead(), "Parser did not consume the complete expression")
    }
}
