package dev.mcbookshelf.sniffer.gametest.support

import com.mojang.authlib.GameProfile
import dev.mcbookshelf.sniffer.network.AuthPromptPayload
import dev.mcbookshelf.sniffer.network.SetDebugModePayload
import dev.mcbookshelf.sniffer.state.PendingAuthRegistry
import io.netty.channel.embedded.EmbeddedChannel
import jakarta.websocket.CloseReason
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestSequence
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.players.NameAndId
import net.minecraft.server.level.ServerPlayer
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import java.util.Queue
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Steps that wait for something the server has not done yet, and the few that drive something rather than wait for it.
 *
 * A [GameTestSequence] registers all of its steps up front and runs them one tick at a time, which is what makes waiting here safe: nothing blocks the server thread, so work the server itself has to perform can still happen while a step waits for it.
 * Waiting is expressed by failing an assertion: a step that throws is tried again on the next tick, so the message it carries reads as "not yet" until the tick budget runs out and it becomes the failure.
 */

/**
 * Issues a DAP request, waits for its response, and hands the result to [check].
 *
 * The request is built inside the sequence rather than at build time, because every `thenX` call is registered up front:
 * a future created eagerly would be sent before the steps meant to precede it have run.
 *
 * Nothing here ever blocks the server thread.
 * The adapter answers most requests on the server thread itself ([dev.mcbookshelf.sniffer.dap.DapServer] hands them to the server executor),
 * so waiting on the future inline would deadlock: the thread that must run the task would be the one waiting for it.
 */
fun <T> GameTestSequence.thenRequest(
    label: String,
    request: () -> CompletableFuture<T>,
    check: (T) -> Unit = {},
): GameTestSequence {
    lateinit var future: CompletableFuture<T>
    return thenExecute { future = request() }
        .thenWaitUntil { assertTrue(future.isDone, "No response to the $label request yet") }
        .thenExecute { check(future.join()) }
}

/** Waits for the next [queue] event and hands it to [check]. */
fun <T> GameTestSequence.thenAwaitEvent(
    label: String,
    queue: Queue<T>,
    check: (T) -> Unit = {},
): GameTestSequence =
    thenWaitUntil { assertTrue(queue.isNotEmpty(), "No $label event received yet") }
        .thenExecute { check(queue.poll()) }

/**
 * Waits [millis] of wall clock time.
 *
 * Ticks are not a usable clock here: a game test server catching up after world generation runs many ticks per millisecond,
 * so a tick based delay can elapse in no real time at all, which is exactly when something waiting on the filesystem or another thread needs a moment.
 */
fun GameTestSequence.thenWaitMillis(millis: Long): GameTestSequence {
    var deadline = 0L
    return thenWaitUntil {
        val now = System.currentTimeMillis()
        if (deadline == 0L) deadline = now + millis
        assertTrue(now >= deadline, "Still settling")
    }
}

/**
 * Applies a filesystem change, reloads the watched packs, and calls [function] every so often until it stores [expected] under [marker], or fails [message] once the tick budget runs out.
 *
 * With [manualReload] off the reload step is skipped and only the change is applied, which is how a watcher set to reload by itself is checked: nothing asks for the splice, so the splice happening at all is the watcher's own doing.
 *
 * [change] is re-applied periodically rather than performed once, and is handed the attempt number so it can write something the watcher has not seen before:
 * the watcher registers asynchronously, so the first write can land before it is listening, and it hashes file contents, so rewriting the same bytes is deliberately not an event.
 * A retry that changed nothing would therefore be silent forever.
 *
 * Applying the change and reloading alternate, so a reload never runs while a previous one's splice is still in flight.
 *
 * The marker is cleared before every call so a value left by an earlier step cannot satisfy the check.
 */
fun GameTestSequence.thenReloadUntil(
    session: DebugSession,
    function: String,
    marker: String,
    expected: Int?,
    message: String,
    manualReload: Boolean = true,
    change: (attempt: Int) -> Unit,
): GameTestSequence {
    var dueAt = 0L
    var applyChange = true
    var attempt = 0
    var landed = false
    return thenWaitUntil {
        val now = System.currentTimeMillis()
        // Only worth looking right after something happened: a game test server ticks as fast as it can, so calling the function every tick would say the same thing thousands of times.
        if (now >= dueAt) {
            if (applyChange) change(attempt++) else if (manualReload) session.run("watch reload")
            applyChange = !applyChange
            dueAt = now + RELOAD_STEP_MS
            session.clearStored(marker)
            session.run("function $function")
            landed = session.stored(marker) == expected
        }
        assertTrue(landed, message)
    }
}

/** Wall clock gap between applying a change and the reload that picks it up. */
private const val RELOAD_STEP_MS = 250L

/**
 * Waits until the mod's side of the DAP connection is wired up.
 *
 * `initDapClient` returns as soon as the client side of the WebSocket handshake completes,
 * but the mod builds its `DapServer` and registers the stop listener afterwards, on the WebSocket thread.
 * A breakpoint firing in that window finds no client, and its stopped event is dropped for good rather than queued,
 * leaving anything waiting on that event waiting forever.
 * Triggering a breakpoint without having exchanged a message first has to wait here.
 *
 * A real editor never meets this window: it sends the initialize and attach handshake before anything can run, and those responses do the same waiting.
 *
 * `initialize` is the probe because it touches no debugger state.
 * What matters is only that a response came back, which proves the other side is listening.
 */
fun GameTestSequence.thenAwaitDapReady(dap: IDebugProtocolServer): GameTestSequence =
    thenRequest("initialize", { dap.initialize(InitializeRequestArguments()) })

/**
 * Reads the variables of the innermost paused frame, walking the tree an editor walks: a stack trace for the frame, the scopes of that frame, then what the first scope holds.
 *
 * The two ids in between are of no interest to a test, and they are held in locals because a sequence is built before any of it runs.
 * [start] and [count] ask for one page of them, as an editor does with a scope too wide to show at once.
 */
fun GameTestSequence.thenPausedVariables(
    dap: IDebugProtocolServer,
    start: Int? = null,
    count: Int? = null,
    check: (List<Variable>) -> Unit,
): GameTestSequence {
    var frameId = -1
    var scopeReference = 0
    return thenRequest("stackTrace", { dap.stackTrace(StackTraceArguments()) }) { response ->
        frameId = response.stackFrames[0].id
    }.thenRequest("scopes", { dap.scopes(ScopesArguments().apply { this.frameId = frameId }) }) { response ->
        scopeReference = response.scopes[0].variablesReference
    }.thenRequest("variables", { dap.variables(variablesOf(scopeReference, start, count)) }) { response ->
        check(response.variables.toList())
    }
}

/**
 * Reads the children of the variable [reference] points at.
 *
 * The reference is taken as a function rather than a value, since the step reading it is registered before the step that finds it has run.
 */
fun GameTestSequence.thenExpand(
    dap: IDebugProtocolServer,
    reference: () -> Int,
    check: (List<Variable>) -> Unit,
): GameTestSequence =
    thenRequest("variables", { dap.variables(variablesOf(reference())) }) { response ->
        check(response.variables.toList())
    }

/** The arguments of a `variables` request, optionally asking for one page of them. */
fun variablesOf(reference: Int, start: Int? = null, count: Int? = null) = VariablesArguments().apply {
    variablesReference = reference
    this.start = start
    this.count = count
}

/**
 * The in game approval prompt a sequence is working with.
 *
 * A sequence is built before any of it runs, so the request id cannot be a local that later steps close over: nothing knows it until the step waiting for the prompt has run.
 */
class AuthPrompt {
    var requestId: UUID? = null
}

/**
 * Waits for the next auth prompt sent to the player owning [channel], and records it in [prompt].
 *
 * The prompt reaches the player as a packet, so reading it off their connection stands in for the dialog they would be looking at.
 */
fun GameTestSequence.thenAwaitPrompt(
    channel: EmbeddedChannel,
    prompt: AuthPrompt,
): GameTestSequence = thenWaitUntil {
    prompt.requestId = prompt.requestId ?: promptSentTo(channel)
    assertTrue(prompt.requestId != null, "No auth prompt reached the player yet")
}

/**
 * Answers [prompt] the way [player] would by accepting or dismissing it.
 *
 * An answer naming a prompt that is no longer the one standing is dropped rather than refused, so a step that changes nothing is a result a test can be after.
 */
fun GameTestSequence.thenAnswerPrompt(
    player: ServerPlayer,
    prompt: AuthPrompt,
    accepted: Boolean,
): GameTestSequence = thenExecute {
    val requestId = prompt.requestId ?: fail("There is no prompt to answer")
    PendingAuthRegistry.resolve(player.uuid, requestId, accepted)
}

/**
 * Waits for the server to close the connection, and checks it said why.
 *
 * Every refusal the auth gate makes is a policy violation, so the phrase is the only thing telling one apart from another.
 */
fun GameTestSequence.thenAwaitRefusal(
    closures: Queue<CloseReason>,
    saying: String,
): GameTestSequence = thenAwaitEvent("close", closures) { reason ->
    assertEquals(reason.closeCode.code, CloseReason.CloseCodes.VIOLATED_POLICY.code, "close code")
    assertTrue(
        reason.reasonPhrase.contains(saying),
        "Expected a refusal saying '$saying', got: ${reason.reasonPhrase}",
    )
}

/** The request id of the next auth prompt queued on [channel], or null if none has been sent yet. */
private fun promptSentTo(channel: EmbeddedChannel): UUID? {
    while (true) {
        val message = channel.outboundMessages().poll() ?: return null
        val payload = (message as? ClientboundCustomPayloadPacket)?.payload
        if (payload is AuthPromptPayload) return payload.requestId
    }
}

/**
 * Logs a player named [name] into the running server, opped or not, and hands back the channel their packets are written to.
 *
 * The player is real: they join the player list, and anything the mod addresses to them, an auth prompt or a broadcast, is written to their connection like any other.
 * That connection runs on an [EmbeddedChannel] rather than a socket, so those packets can be polled back off it instead of leaving for a client that does not exist.
 *
 * They stay logged in until the server stops.
 */
fun placePlayer(helper: GameTestHelper, name: String, op: Boolean): Pair<ServerPlayer, EmbeddedChannel> {
    val server = helper.level.server
    val cookie = CommonListenerCookie.createInitial(GameProfile(UUID.randomUUID(), name), false)
    val player = ServerPlayer(server, helper.level, cookie.gameProfile(), cookie.clientInformation())
    val connection = Connection(PacketFlow.SERVERBOUND)
    val channel = EmbeddedChannel(connection)
    server.playerList.placeNewPlayer(connection, player, cookie)
    if (op) server.playerList.op(NameAndId(player.gameProfile))
    return player to channel
}

/**
 * The debug mode values the server has pushed to [channel] so far, draining what it reads.
 *
 * The setting reaches the player as a packet, so reading it off their connection stands in for the HUD they would be looking at.
 * Draining is destructive and shared with [chatSentTo], so a test reads a connection through one of them, not both.
 */
fun debugModeSentTo(channel: EmbeddedChannel): List<Boolean> {
    val values = mutableListOf<Boolean>()
    while (true) {
        val message = channel.outboundMessages().poll() ?: return values
        val payload = (message as? ClientboundCustomPayloadPacket)?.payload
        if (payload is SetDebugModePayload) values.add(payload.enabled)
    }
}

/** The chat the server has sent to [channel] so far, as text, draining what it reads. */
fun chatSentTo(channel: EmbeddedChannel): List<String> {
    val messages = mutableListOf<String>()
    while (true) {
        val message = channel.outboundMessages().poll() ?: return messages
        if (message is ClientboundSystemChatPacket) messages.add(message.content().string)
    }
}
