package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.gametest.support.DebugSession
import dev.mcbookshelf.sniffer.gametest.support.assertEquals
import dev.mcbookshelf.sniffer.gametest.support.assertFalse
import dev.mcbookshelf.sniffer.gametest.support.assertThat
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import dev.mcbookshelf.sniffer.gametest.support.debugModeSentTo
import dev.mcbookshelf.sniffer.gametest.support.placePlayer
import dev.mcbookshelf.sniffer.state.DebugModeState
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

/**
 * `/debugmode`, which turns the Sniffer HUD on or off for the player who ran it.
 *
 * It is a per player setting and nothing more: the server keeps the value so a player who reconnects finds it again, and pushes it to that player's client so the overlay can read it cheaply.
 * The setting is therefore read off the player's own connection, since nothing short of that says it reached anybody.
 *
 * What the command deliberately does not do is gate any debugging.
 * Breakpoints fire the same whether it is on or off, and that is the property most easily broken by accident, so it is the one worth pinning here.
 */
class DebugModeCommandIntegrationGameTest {

    @GameTest(environment = "sniffer_test:debugmode_enable")
    fun enablingRecordsTheSettingAndPushesItToThePlayer(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val (player, channel) = placePlayer(helper, ENABLING, op = true)
        // Drained on the way in, so the first read only sees what the command caused.
        debugModeSentTo(channel)

        session.run("execute as ${player.name.string} run debugmode enable")

        assertTrue(DebugModeState.isEnabled(player.uuid), "The server should have recorded the setting")
        assertEquals(debugModeSentTo(channel), listOf(true), "what was pushed to the player")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:debugmode_disable")
    fun disablingTurnsItBackOffAndTellsThePlayer(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val (player, channel) = placePlayer(helper, DISABLING, op = true)
        session.run("execute as ${player.name.string} run debugmode enable")
        debugModeSentTo(channel)

        session.run("execute as ${player.name.string} run debugmode disable")

        assertFalse(DebugModeState.isEnabled(player.uuid), "The setting should have been turned back off")
        assertEquals(debugModeSentTo(channel), listOf(false), "what was pushed to the player")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:debugmode_per_player")
    fun onePlayersSettingLeavesEveryoneElseAlone(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val (enabling, _) = placePlayer(helper, FIRST, op = true)
        val (other, otherChannel) = placePlayer(helper, SECOND, op = true)
        debugModeSentTo(otherChannel)

        session.run("execute as ${enabling.name.string} run debugmode enable")

        assertTrue(DebugModeState.isEnabled(enabling.uuid), "The player who ran it should have it on")
        // Several players share one server and each keeps their own answer, so nobody else's HUD may be switched from under them.
        assertFalse(DebugModeState.isEnabled(other.uuid), "Nobody else should have been switched on")
        assertEquals(debugModeSentTo(otherChannel), emptyList<Boolean>(), "what reached the other player")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:debugmode_console")
    fun runningItFromTheConsoleSwitchesNobody(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val (player, channel) = placePlayer(helper, WATCHING, op = true)
        debugModeSentTo(channel)

        // The console has no HUD of its own and is not standing in for a player, so there is nobody for the setting to apply to.
        session.run("debugmode enable")

        assertFalse(DebugModeState.isEnabled(player.uuid), "A console call must not switch a player on")
        assertEquals(debugModeSentTo(channel), emptyList<Boolean>(), "what reached the player")
        helper.succeed()
    }

    @GameTest(environment = "sniffer_test:debugmode_not_a_gate")
    fun theSettingDoesNotDecideWhetherBreakpointsFire(helper: GameTestHelper) {
        val session = DebugSession(helper)
        val (player, _) = placePlayer(helper, GATED, op = true)
        session.breakpointAt(LINEAR, line = 1)

        // Off, which is how every player starts, and the breakpoint still has to halt.
        session.run("execute as ${player.name.string} run debugmode disable")
        session.run("function $LINEAR")

        assertThat(session).isPaused("Debug mode is HUD only and must not decide whether a breakpoint fires")
        assertThat(session).hasExecuted("a")
        helper.succeed()
    }

    private companion object {
        const val LINEAR = "sniffer_test:linear"

        const val ENABLING = "sniffer_dbg_on"
        const val DISABLING = "sniffer_dbg_off"
        const val FIRST = "sniffer_dbg_one"
        const val SECOND = "sniffer_dbg_two"
        const val WATCHING = "sniffer_dbg_con"
        const val GATED = "sniffer_dbg_gate"
    }
}
