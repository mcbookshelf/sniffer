package dev.mcbookshelf.sniffer.gametest.support

import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.network.chat.Component

/**
 * The assertions the game tests are written in.
 *
 * They throw what [net.minecraft.gametest.framework.GameTestHelper] throws, which is also what a `thenWaitUntil` step catches to try again on the next tick, so failing an assertion is equally a way of saying "not yet".
 * Being free functions rather than methods on the helper is what keeps the helper out of every signature: nothing has to carry one around to be able to fail.
 * The tick a failure reports is therefore always 0, which is read by nothing but the message.
 */
fun fail(message: String): Nothing = throw GameTestAssertException(Component.literal(message), 0)

fun assertTrue(condition: Boolean, message: String) {
    if (!condition) fail(message)
}

fun assertFalse(condition: Boolean, message: String) {
    if (condition) fail(message)
}

/** Asserts that [value] is [expected], naming what was compared since that name is the whole of what a failing run reports. */
fun assertEquals(value: Any?, expected: Any?, name: String) {
    if (value != expected) fail("Wrong $name: expected $expected, got $value")
}
