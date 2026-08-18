package dev.mcbookshelf.sniffer.expressions

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import dev.mcbookshelf.sniffer.commands.ExprArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.StringTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * What a `{ ... }` expression means once parsed: which tag each operator yields, and which pairs of tags it refuses.
 *
 * The same expression reaches this evaluator from `#!log`, `#!assert` and the DAP `evaluate` request alike,
 * so an operator answers for itself here rather than three times over.
 * Operands are literals throughout, which is what keeps the whole file out of a launched game:
 * an expression built only of literals resolves without reading anything the server owns.
 */
class ExpressionEvaluationTest {

    // A literal operand resolves to `PlainData`, which never reads the source it is handed, so this mock is here to satisfy the signature rather than to stand in for a game.
    private val source: CommandSourceStack = mock(CommandSourceStack::class.java)

    // ── Numeric promotion ───────────────────────────────────────────

    @Test
    fun `two ints stay an int`() {
        assertEquals(IntTag.valueOf(3), eval("{1 + 2}"))
    }

    @Test
    fun `an int and a long promote to a long`() {
        assertEquals(LongTag.valueOf(3L), eval("{1 + 2L}"))
    }

    @Test
    fun `an int and a float promote to a float`() {
        assertEquals(FloatTag.valueOf(3.5f), eval("{1 + 2.5f}"))
    }

    @Test
    fun `a float and a double promote to a double`() {
        assertEquals(DoubleTag.valueOf(3.5), eval("{1.0f + 2.5d}"))
    }

    @Test
    fun `integer division truncates`() {
        assertEquals(IntTag.valueOf(3), eval("{7 / 2}"))
    }

    @Test
    fun `division promotes to double as soon as one side is one`() {
        assertEquals(DoubleTag.valueOf(3.5), eval("{7 / 2.0}"))
    }

    @Test
    fun `subtraction and multiplication follow the same promotion rules`() {
        assertEquals(IntTag.valueOf(-2), eval("{3 - 5}"))
        assertEquals(LongTag.valueOf(12L), eval("{3 * 4L}"))
    }

    // ── Evaluation order ────────────────────────────────────────────

    @Test
    fun `operators have no precedence and apply left to right`() {
        // Not 7: the multiplication does not bind tighter than the addition.
        assertEquals(IntTag.valueOf(9), eval("{1 + 2 * 3}"))
    }

    // ── Text ────────────────────────────────────────────────────────

    @Test
    fun `strings concatenate`() {
        assertEquals(StringTag.valueOf("ab"), eval("""{"a" + "b"}"""))
    }

    // ── Comparison ──────────────────────────────────────────────────

    @Test
    fun `comparisons yield a boolean byte`() {
        assertEquals(TRUE, eval("{1 < 2}"))
        assertEquals(FALSE, eval("{2 < 2}"))
        assertEquals(TRUE, eval("{2 <= 2}"))
        assertEquals(TRUE, eval("{3 > 2}"))
        assertEquals(TRUE, eval("{2 >= 2}"))
    }

    @Test
    fun `equality compares tag values`() {
        assertEquals(TRUE, eval("{1 == 1}"))
        assertEquals(FALSE, eval("{1 == 2}"))
        assertEquals(TRUE, eval("{1 != 2}"))
    }

    @Test
    fun `equality is type sensitive`() {
        // An int and a long holding the same number are different tags.
        assertEquals(FALSE, eval("{1 == 1L}"))
    }

    // ── Type tests ──────────────────────────────────────────────────

    // Operands are written with a type suffix because SNBT reads the `i` of a following `is` as an int suffix, which leaves a bare `1` on the left of `is` unparseable.
    @Test
    fun `the is operator recognises the concrete tag type`() {
        assertEquals(TRUE, eval("""{1b is "byte"}"""))
        assertEquals(TRUE, eval("""{1L is "long"}"""))
        assertEquals(TRUE, eval("""{1.5 is "double"}"""))
        assertEquals(TRUE, eval("""{"x" is "string"}"""))
        assertEquals(TRUE, eval("""{[I;1,2] is "int_array"}"""))
        assertEquals(TRUE, eval("""{[1,2] is "list"}"""))
    }

    @Test
    fun `the is operator does not conflate neighbouring numeric types`() {
        assertEquals(FALSE, eval("""{1L is "int"}"""))
        assertEquals(FALSE, eval("""{1.5 is "float"}"""))
    }

    @Test
    fun `the is operator recognises the number and nbt umbrellas`() {
        assertEquals(TRUE, eval("""{1L is "number"}"""))
        assertEquals(TRUE, eval("""{"x" is "nbt"}"""))
        assertEquals(FALSE, eval("""{"x" is "number"}"""))
    }

    @Test
    fun `an unknown type name never matches`() {
        assertEquals(FALSE, eval("""{1L is "widget"}"""))
    }

    @Test
    fun `the is operator rejects a non-string type name`() {
        assertThrows(CommandSyntaxException::class.java) { eval("{1L is 2}") }
    }

    // ── Logic ───────────────────────────────────────────────────────

    @Test
    fun `and or combine boolean bytes`() {
        assertEquals(TRUE, eval("{1b && 1b}"))
        assertEquals(FALSE, eval("{1b && 0b}"))
        assertEquals(TRUE, eval("{0b || 1b}"))
        assertEquals(FALSE, eval("{0b || 0b}"))
    }

    @Test
    fun `an already decided or skips its right operand entirely`() {
        // The right operand is not a boolean byte, which is a type error anywhere else.
        // A left side that already settles the result makes the operator skip it rather than check it.
        assertEquals(TRUE, eval("{1b || 5}"))
    }

    @Test
    fun `an already decided and skips its right operand entirely`() {
        assertEquals(FALSE, eval("{0b && 5}"))
    }

    @Test
    fun `logic on non-boolean operands is a type error`() {
        assertThrows(CommandSyntaxException::class.java) { eval("{5 && 1b}") }
    }

    // ── Failure modes ───────────────────────────────────────────────

    @Test
    fun `mixing a number and a string is a type error`() {
        assertThrows(CommandSyntaxException::class.java) { eval("""{1 + "a"}""") }
    }

    @Test
    fun `an empty expression is rejected`() {
        assertThrows(CommandSyntaxException::class.java) { eval("{}") }
    }

    @Test
    fun `two operands without an operator are rejected`() {
        assertThrows(CommandSyntaxException::class.java) { eval("{1 2}") }
    }

    private fun eval(expression: String): Any =
        ExprArgumentType().parse(StringReader(expression)).get(source)

    private companion object {
        val TRUE: ByteTag = ByteTag.valueOf(true)
        val FALSE: ByteTag = ByteTag.valueOf(false)
    }
}
