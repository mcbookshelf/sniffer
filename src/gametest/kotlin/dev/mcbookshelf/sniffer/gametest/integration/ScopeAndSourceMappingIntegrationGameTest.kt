package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertThat
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

/**
 * A frame's scope and the source lines a breakpoint is placed against.
 *
 * A call pushes a debug scope and every way out of the frame pops it exactly once, whether the frame runs to its end or is discarded by `/return`.
 * Breakpoints are placed against the file as it was written, so the mapping the parsing mixin builds has to count the comments and blank lines the preprocessed function no longer holds.
 */
class ScopeAndSourceMappingIntegrationGameTest {

    @GameTest(environment = "sniffer_test:early_return")
    fun anEarlyReturnPopsItsScopeAndSkipsTheRest(helper: GameTestHelper) {
        val session = DebugSession(helper)
        session.breakpointAt(EARLY_RETURN, line = 1)
        session.run("function $CALLS_EARLY_RETURN")

        helper.startSequence()
            .thenExecute { session.continueExecution() }
            .thenWaitUntil { assertThat(session).hasExecuted("returned", "after_early_return") }
            .thenExecute {
                // Nothing left on the stack: the discarded frame popped exactly the one scope it pushed.
                assertThat(session).hasCallStack()
            }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:source_lines")
    fun breakpointLinesCountCommentsAndBlankLines(helper: GameTestHelper) {
        val session = DebugSession(helper)
        // Line 5 of the file: the second command, after two comments and two blanks.
        session.breakpointAt(COMMENTED, line = 5)

        session.run("function $COMMENTED")

        assertThat(session).isPaused("Execution should be paused on the breakpoint")
        assertThat(session).hasExecuted("first")
        helper.succeed()
    }

    private companion object {
        const val COMMENTED = "sniffer_test:commented"
        const val EARLY_RETURN = "sniffer_test:early_return"
        const val CALLS_EARLY_RETURN = "sniffer_test:calls_early_return"
    }
}