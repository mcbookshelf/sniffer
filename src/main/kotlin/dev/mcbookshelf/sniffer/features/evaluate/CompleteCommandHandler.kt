package dev.mcbookshelf.sniffer.features.evaluate

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.features.callstack.ScopeManager

/**
 * Answers what could be typed next in a command, with the game's own completions.
 *
 * The completions are those of the executor of the current scope, so they name what that source can see.
 *
 * @author theogiraudet
 */
class CompleteCommandHandler(private val scopeManager: ScopeManager) : Handler<CompleteCommandInput> {

    override val inputType = CompleteCommandInput::class

    override fun handle(input: CompleteCommandInput, ctx: Context): Output {
        val source = scopeManager.commandSource(ctx.source)
        // The dispatcher never sees the slash a player types, so it is cut off here and added back to every span.
        val offset = if (input.command.startsWith("/")) 1 else 0
        val command = input.command.substring(offset)
        val cursor = (input.cursor - offset).coerceIn(0, command.length)

        val dispatcher = source.server.commands.dispatcher
        val parse = dispatcher.parse(command, source)
        // Read without waiting: every suggestion provider on a server builds a future that is already done, and
        // one that did not would be holding the server thread rather than the console.
        val suggestions = dispatcher.getCompletionSuggestions(parse, cursor).getNow(null)
            ?: return CompletionsOutput(emptyList())

        return CompletionsOutput(
            suggestions.list.map {
                Completion(
                    text = it.text,
                    start = it.range.start + offset,
                    length = it.range.length,
                    tooltip = it.tooltip?.string,
                )
            }
        )
    }
}
