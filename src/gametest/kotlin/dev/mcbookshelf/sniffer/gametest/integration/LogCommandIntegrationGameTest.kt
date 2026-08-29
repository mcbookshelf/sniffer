package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertEquals
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import dev.mcbookshelf.sniffer.gametest.support.chatSentTo
import dev.mcbookshelf.sniffer.gametest.support.placePlayer
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

/**
 * `/log` prints a line assembled from plain text and `{ ... }` expressions, each expression evaluated against the caller when the command runs.
 * The line is parsed as a whole before any of it runs, so a single malformed expression costs the entire command.
 *
 * The line goes out as a broadcast to every player rather than back to whoever ran the command, since a log line is meant to be seen by anyone watching the pack run.
 * So a player is placed and the line is read off their own connection: nothing short of that proves the log reached anybody.
 */
class LogCommandIntegrationGameTest {

    @GameTest(environment = "sniffer_test:log_plain")
    fun aLineOfPlainTextIsLogged(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val chat = watcher(helper, "log_plain_watcher")

        session.run("log a debug line")

        assertEquals(chat(), listOf("[Sniffer] a debug line"), "chat after logging plain text")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:log_expression")
    fun anEmbeddedExpressionIsEvaluated(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val chat = watcher(helper, "log_expr_watcher")

        session.run("log total={1 + 1}")

        assertEquals(chat(), listOf("[Sniffer] total=2"), "chat after logging an expression")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:log_malformed")
    fun anUnclosedExpressionIsRefused(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val chat = watcher(helper, "log_malformed_watcher")

        val failures = session.runCapturing("log total={1 +")

        // The argument never parsed, so the command never ran and broadcast nothing.
        assertEquals(chat(), emptyList<String>(), "chat after a malformed expression")
        assertTrue(failures.isNotEmpty(), "A malformed expression should be reported back to the caller")
        helper.succeed()
    }

    /**
     * Places a player to broadcast to and returns a reader of the chat they have been sent since the last read.
     * The join message is drained on the way out, so the first read only sees what the test caused.
     */
    private fun watcher(helper: GameTestHelper, name: String): () -> List<String> {
        val (_, channel) = placePlayer(helper, name, op = false)
        chatSentTo(channel)
        return { chatSentTo(channel) }
    }
}