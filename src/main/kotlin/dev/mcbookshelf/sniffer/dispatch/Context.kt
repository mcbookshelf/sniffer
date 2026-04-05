package dev.mcbookshelf.sniffer.dispatch

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer

/**
 * Per-invocation state passed to every [Handler.handle] call.
 *
 * Holds only values whose binding genuinely varies **per dispatch call**:
 * who triggered the action (entrypoint-specific [CommandSourceStack]) and
 * which [MinecraftServer] instance is running.
 *
 * Long-lived services (ScopeManager, VariableManager, ...)
 * MUST NOT be added here — they are injected into individual handlers via
 * their constructor in `Handlers.buildHandlers`, so that each handler only
 * reaches what it actually needs. This keeps [Context] narrow and prevents
 * it from degenerating into a god object.
 *
 * @property source the command source that triggered this action. For the
 *   DAP entrypoint, the synthetic server source; for the in-game command
 *   entrypoint, the source of the player/console that ran the command.
 * @property server the running server instance. Passed explicitly rather
 *   than fetched statically so handlers stay trivially testable.
 */
data class Context(
    val source: CommandSourceStack,
    val server: MinecraftServer,
)
