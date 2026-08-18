package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertEquals
import dev.mcbookshelf.sniffer.gametest.support.assertFalse
import dev.mcbookshelf.sniffer.gametest.support.assertThat
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

/**
 * `#!` and `#@` are ordinary `.mcfunction` comments, which is what makes them usable: a datapack carrying them still loads in a game without Sniffer, where they do nothing at all.
 * With Sniffer loaded they are rewritten as the function is built, `#!` into the command written after it and `#@` into a tag on the function, which is collected but never executed.
 *
 * Rewriting changes what a line becomes, and that has two costs.
 * A breakpoint is placed on a line of the file the author wrote, so the mapping back to it has to survive the rewrite.
 * And a `#!` is parsed as a command rather than skipped as a comment, so naming one that does not exist turns a line vanilla ignores into a function that fails to load.
 */
class DebugDirectiveIntegrationGameTest {

    @GameTest(environment = "sniffer_test:debug_directives")
    fun hashBangLinesRunAsCommands(helper: GameTestHelper) {
        val session = DebugSession(helper)

        session.run("function $DIRECTIVES")

        // The two `#!` lines are `execute store result ... run assert`: they only write anything if they ran as commands.
        assertThat(session).hasExecuted("first", "assert_pass", "assert_fail", "last")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:debug_tags")
    fun atSignLinesBecomeTagsAndNeverRun(helper: GameTestHelper) {
        val session = DebugSession(helper)

        session.run("function $DIRECTIVES")

        assertEquals(session.debugTags(DIRECTIVES), listOf("audited"), "debug tags")
        // Nothing in the storage came from the `#@` line: it is not a command.
        assertFalse(session.executed().contains("audited"), "A `#@` line must not execute")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:directive_unknown_command")
    fun aHashBangNamingAnUnknownCommandCostsItsFunction(helper: GameTestHelper) {
        val session = DebugSession(helper)

        assertFalse(session.isLoaded(BROKEN), "A function with an unparseable `#!` line should not load")
        // Only that function, though: a bad directive does not take the datapack down with it.
        assertTrue(session.isLoaded(DIRECTIVES), "The rest of the datapack should still load")

        session.run("function $BROKEN")

        assertTrue(session.executed().isEmpty(), "A function that never loaded cannot run")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:directive_line_mapping")
    fun breakpointLinesSurviveDirectiveRewriting(helper: GameTestHelper) {
        val session = DebugSession(helper)
        // Line 5: the last command, below one `#@` tag and three `#!` commands.
        session.breakpointAt(DIRECTIVES, line = 5)

        session.run("function $DIRECTIVES")

        assertThat(session).isPaused("Execution should be paused on the breakpoint")
        // Everything above the breakpoint ran, the breakpointed line did not.
        assertThat(session).hasExecuted("first", "assert_pass", "assert_fail")
        helper.succeed()
    }

    private companion object {
        const val DIRECTIVES = "sniffer_test:directives"
        const val BROKEN = "sniffer_test:broken_directive"
    }
}