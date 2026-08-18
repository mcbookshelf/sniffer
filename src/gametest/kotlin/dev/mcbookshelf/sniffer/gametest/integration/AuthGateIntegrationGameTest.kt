package dev.mcbookshelf.sniffer.gametest.integration

import dev.mcbookshelf.sniffer.config.DebuggerConfig
import dev.mcbookshelf.sniffer.gametest.support.AuthPrompt
import dev.mcbookshelf.sniffer.gametest.support.assertFalse
import dev.mcbookshelf.sniffer.gametest.support.assertTrue
import dev.mcbookshelf.sniffer.gametest.support.placePlayer
import dev.mcbookshelf.sniffer.gametest.support.thenAnswerPrompt
import dev.mcbookshelf.sniffer.gametest.support.thenAwaitPrompt
import dev.mcbookshelf.sniffer.gametest.support.thenAwaitRefusal
import dev.mcbookshelf.sniffer.gametest.support.thenRequest
import dev.mcbookshelf.sniffer.gametest.support.thenWaitMillis
import dev.mcbookshelf.sniffer.state.ConnectionState
import dev.mcbookshelf.sniffer.state.PendingAuthRegistry
import jakarta.websocket.ClientEndpointConfig
import jakarta.websocket.Endpoint
import jakarta.websocket.EndpointConfig
import jakarta.websocket.Session
import java.net.URI
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.glassfish.tyrus.client.ClientManager

/**
 * The gate that stands between a WebSocket connection and a DAP session.
 *
 * A connection names the player it wants to be approved by, and that player is prompted in game.
 * Nothing opens a session but their acceptance: an unnamed player, one who is not an operator, a refusal, a prompt that lapses and a client that vanishes before answering all end with the socket closed.
 * A refusal also puts the player on a cooldown, so an editor reconnecting in a loop cannot keep them under prompts, and the cooldown is theirs alone rather than the server's.
 *
 * Only the newest prompt for a player stands; answers naming a settled one change nothing, in either direction.
 */
class AuthGateIntegrationGameTest : AbstractDapIntegrationGameTest() {

    // ── Refusals ────────────────────────────────────────────────────

    @GameTest(environment = "sniffer_test:auth_no_user", maxTicks = MAX_TICKS)
    fun aConnectionThatNamesNoPlayerIsRefused(helper: GameTestHelper) {
        // No `user` parameter: nothing says which player should be asked, so there is nobody to prompt and nothing to do but refuse.
        initDapClient(authEnabled = true)

        helper.startSequence()
            .thenAwaitRefusal(closures, "missing 'user' parameter")
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:auth_not_operator", maxTicks = MAX_TICKS)
    fun aConnectionNamingAPlayerWhoIsNotAnOperatorIsRefused(helper: GameTestHelper) {
        // Online, but never opped: a DAP session can read and drive the whole server, so being logged in is not enough to open one.
        val (player, _) = placePlayer(helper, PLAIN_PLAYER, op = false)
        initDapClient(authEnabled = true, user = player.name.string)

        helper.startSequence()
            .thenAwaitRefusal(closures, "is not an operator")
            .thenSucceedAndClose()
    }

    // ── Answering the prompt ────────────────────────────────────────

    @GameTest(environment = "sniffer_test:auth_approved", maxTicks = MAX_TICKS)
    fun anApprovedPromptTurnsTheConnectionIntoADapSession(helper: GameTestHelper) {
        val (player, channel) = placePlayer(helper, OPERATOR, op = true)
        val dap = initDapClient(authEnabled = true, user = player.name.string)
        val prompt = AuthPrompt()

        helper.startSequence()
            .thenAwaitPrompt(channel, prompt)
            .thenAnswerPrompt(player, prompt, accepted = true)
            // Only an approved connection is given a DapServer to talk to, so a response at all is the approval taking effect.
            .thenRequest("initialize", { dap.initialize(InitializeRequestArguments()) }) { capabilities ->
                assertTrue(
                    capabilities.supportsConfigurationDoneRequest,
                    "An approved connection should get a working adapter",
                )
            }
            .thenExecute {
                assertTrue(closures.isEmpty(), "An approved connection should not have been closed")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:auth_rejected", maxTicks = MAX_TICKS)
    fun aRejectedPromptClosesTheSocketAndHoldsThePlayerOnCooldown(helper: GameTestHelper) {
        val (player, channel) = placePlayer(helper, REJECTING_PLAYER, op = true)
        initDapClient(authEnabled = true, user = player.name.string)
        val prompt = AuthPrompt()

        helper.startSequence()
            .thenAwaitPrompt(channel, prompt)
            .thenAnswerPrompt(player, prompt, accepted = false)
            .thenAwaitRefusal(closures, "rejected by player")
            .thenExecute {
                assertFalse(ConnectionState.isConnected(), "A refused client must not hold a session")
            }
            // Reconnecting is exactly what a refused client does, so the refusal has to hold for a while rather than let it prompt the player again at once.
            .thenExecute { initDapClient(authEnabled = true, user = player.name.string) }
            .thenAwaitRefusal(closures, "recently rejected")
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:auth_cooldown_scope", maxTicks = MAX_TICKS)
    fun oneOperatorsRefusalDoesNotLockOutAnother(helper: GameTestHelper) {
        val (refuser, refuserChannel) = placePlayer(helper, REFUSING_PLAYER, op = true)
        val (other, otherChannel) = placePlayer(helper, OTHER_OPERATOR, op = true)
        initDapClient(authEnabled = true, user = refuser.name.string)
        val refused = AuthPrompt()
        val accepted = AuthPrompt()

        helper.startSequence()
            .thenAwaitPrompt(refuserChannel, refused)
            .thenAnswerPrompt(refuser, refused, accepted = false)
            .thenAwaitRefusal(closures, "rejected by player")
            // The cooldown protects the player who said no from being asked again, and nobody else.
            .thenExecute { initDapClient(authEnabled = true, user = other.name.string) }
            .thenAwaitPrompt(otherChannel, accepted)
            .thenAnswerPrompt(other, accepted, accepted = true)
            .thenExecute {
                assertTrue(ConnectionState.isConnected(), "The other operator should still be able to approve")
            }
            .thenSucceedAndClose()
    }

    // ── Answers that must not count ─────────────────────────────────

    @GameTest(environment = "sniffer_test:auth_answered_once", maxTicks = MAX_TICKS)
    fun anAnswerArrivingAfterThePromptWasSettledChangesNothing(helper: GameTestHelper) {
        val (player, channel) = placePlayer(helper, ANSWERING_TWICE, op = true)
        val dap = initDapClient(authEnabled = true, user = player.name.string)
        val prompt = AuthPrompt()

        helper.startSequence()
            .thenAwaitPrompt(channel, prompt)
            .thenAnswerPrompt(player, prompt, accepted = true)
            // A duplicate packet, or a client sending both answers, must not be able to tear down the session it already opened.
            .thenAnswerPrompt(player, prompt, accepted = false)
            .thenRequest("initialize", { dap.initialize(InitializeRequestArguments()) })
            .thenExecute {
                assertTrue(ConnectionState.isConnected(), "The approved session should have survived")
                assertTrue(closures.isEmpty(), "A settled prompt should not close a live session")
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:auth_superseded", maxTicks = MAX_TICKS)
    fun aSecondConnectionSupersedesTheFirstAndOnlyTheNewAnswerCounts(helper: GameTestHelper) {
        val (player, channel) = placePlayer(helper, RECONNECTING_PLAYER, op = true)
        initDapClient(authEnabled = true, user = player.name.string)
        val abandoned = AuthPrompt()
        val standing = AuthPrompt()
        var other: Session? = null

        helper.startSequence()
            .thenAwaitPrompt(channel, abandoned)
            // An editor reconnecting while the prompt is still up would otherwise leave the player staring at two of them.
            .thenExecute { other = openSecondConnection(player.name.string) }
            .thenAwaitPrompt(channel, standing)
            .thenExecute {
                assertTrue(
                    abandoned.requestId != standing.requestId,
                    "The second connection should get a prompt of its own",
                )
            }
            .thenAwaitRefusal(closures, "superseded")
            // The player only ever sees the newest prompt, so an answer naming the old one answers a question nobody is still asking.
            .thenAnswerPrompt(player, abandoned, accepted = true)
            .thenExecute {
                assertFalse(ConnectionState.isConnected(), "A superseded prompt must not open a session")
            }
            .thenAnswerPrompt(player, standing, accepted = true)
            .thenExecute {
                assertTrue(ConnectionState.isConnected(), "The prompt that stands should still be answerable")
                other?.close()
            }
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:auth_dropped", maxTicks = MAX_TICKS)
    fun aClientThatDisappearsTakesItsPromptWithIt(helper: GameTestHelper) {
        val (player, channel) = placePlayer(helper, VANISHING_CLIENT, op = true)
        initDapClient(authEnabled = true, user = player.name.string)
        val prompt = AuthPrompt()

        helper.startSequence()
            .thenAwaitPrompt(channel, prompt)
            .thenExecute { closeDapClient() }
            // The socket closes on a Tyrus thread, so the cancellation it triggers is the one thing here that has to be waited out rather than observed.
            .thenWaitMillis(DROP_SETTLE_MS)
            // The player may well press accept after their editor died, and it must not open a session on a socket that is already gone.
            .thenAnswerPrompt(player, prompt, accepted = true)
            .thenExecute {
                assertFalse(ConnectionState.isConnected(), "A dropped connection must not be approvable")
            }
            .thenSucceed()
    }

    @GameTest(environment = "sniffer_test:auth_timeout", maxTicks = MAX_TICKS)
    fun anUnansweredPromptLapsesAndPutsThePlayerOnCooldown(helper: GameTestHelper) {
        val (player, _) = placePlayer(helper, LAPSING_PLAYER, op = true)
        initDapClient(authEnabled = true, user = player.name.string, promptTimeoutSeconds = 1)

        helper.startSequence()
            // A prompt nobody answers cannot leave the connection waiting forever, or an unattended server would hold a half open session for good.
            .thenAwaitRefusal(closures, "timed out")
            // Lapsing counts as a refusal, so a client cannot reconnect in a loop and keep the player under prompts.
            .thenExecute {
                initDapClient(authEnabled = true, user = player.name.string, promptTimeoutSeconds = 1)
            }
            .thenAwaitRefusal(closures, "recently rejected")
            .thenSucceedAndClose()
    }

    @GameTest(environment = "sniffer_test:auth_shutdown", maxTicks = MAX_TICKS)
    fun aServerStoppingDropsThePromptsStillStanding(helper: GameTestHelper) {
        val (player, channel) = placePlayer(helper, PLAYER_AT_SHUTDOWN, op = true)
        initDapClient(authEnabled = true, user = player.name.string)
        val prompt = AuthPrompt()

        helper.startSequence()
            .thenAwaitPrompt(channel, prompt)
            // What the mod does on SERVER_STOPPING. Stopping the server for real would take the rest of the suite with it.
            .thenExecute { PendingAuthRegistry.clearAll() }
            .thenAwaitRefusal(closures, "server stopping")
            .thenAnswerPrompt(player, prompt, accepted = true)
            .thenExecute {
                assertFalse(ConnectionState.isConnected(), "A dropped prompt must not open a session")
            }
            .thenSucceedAndClose()
    }

    // ── Support ─────────────────────────────────────────────────────

    /**
     * Opens a second WebSocket connection alongside the one [initDapClient] holds.
     *
     * It speaks no DAP: all it has to do is exist, so the gate sees two connections asking for the same player.
     */
    private fun openSecondConnection(user: String): Session {
        val config = DebuggerConfig.getInstance()
        return ClientManager.createClient().connectToServer(
            object : Endpoint() {
                override fun onOpen(session: Session, endpointConfig: EndpointConfig) {
                    session.maxIdleTimeout = 0
                }
            },
            ClientEndpointConfig.Builder.create().build(),
            URI("ws://${config.host}:$dapPort/${config.path}?user=$user"),
        )
    }

    /**
     * Logs a player in, on a connection whose packets go nowhere but can be read back.
     *
     * The gate looks the player up in the server's own player list and checks the ops file, so nothing short of a placed player exercises it.
     */
    private companion object {
        const val PLAIN_PLAYER = "sniffer_plain"
        const val OPERATOR = "sniffer_op"
        const val REJECTING_PLAYER = "sniffer_no"
        const val REFUSING_PLAYER = "sniffer_ref"
        const val OTHER_OPERATOR = "sniffer_other"
        const val ANSWERING_TWICE = "sniffer_dup"
        const val RECONNECTING_PLAYER = "sniffer_again"
        const val VANISHING_CLIENT = "sniffer_gone"
        const val LAPSING_PLAYER = "sniffer_lapse"
        const val PLAYER_AT_SHUTDOWN = "sniffer_stop"

        /** Long enough for a local socket close to reach the server, short enough not to stall the suite. */
        const val DROP_SETTLE_MS = 1_000L

        const val MAX_TICKS = 100_000
    }
}
