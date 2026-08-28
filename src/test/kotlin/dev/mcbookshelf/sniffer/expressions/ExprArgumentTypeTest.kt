package dev.mcbookshelf.sniffer.expressions

import com.mojang.brigadier.StringReader
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import dev.mcbookshelf.sniffer.expression.ExprArgumentType

/**
 * Where `ExprArgumentType` stops reading a `{ ... }` expression off a command line.
 *
 * A `(score ...)`, `(data ...)` or `(name ...)` operand delegates to one of Minecraft's own parsers, and those stop the moment they have what they came for, whether or not a space follows.
 * Closing the parenthesis is therefore the expression parser's own job, and each case asserts the reader was left with nothing unread, since an operand that gives up early leaves the rest of the line to be misread as something else.
 */
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
