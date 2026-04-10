package dev.mcbookshelf.sniffer.dispatch

import kotlin.reflect.KClass

/**
 * Routes an [IInput] to the single [Handler] registered for its exact type.
 *
 * Entrypoints (DAP server, in-game commands) hold one [Dispatcher] instance,
 * build an [IInput] from their native request, and call [dispatch]. All actual
 * debugger behavior lives in the handlers — entrypoints are thin translators.
 *
 * Handler discovery is explicit: the list of handlers is provided at construction
 * time (see `HandlersRegistry.kt`). Adding a new action means adding one handler class
 * and one line to that list.
 */
class Dispatcher(handlers: List<Handler<*>>) {

    private val handlers: Map<KClass<out IInput>, Handler<*>> =
        handlers.associateBy { it.inputType }.also {
            require(it.size == handlers.size) {
                "Duplicate Handler registered for the same IInput type"
            }
        }

    /**
     * Dispatch [input] to its registered handler.
     *
     * @throws IllegalStateException if no handler is registered for [input]'s exact type.
     */
    @Suppress("UNCHECKED_CAST")
    fun <I : IInput> dispatch(input: I, ctx: Context): Output {
        val handler = handlers[input::class]
            ?: error("No Handler registered for ${input::class.qualifiedName}")
        return (handler as Handler<I>).handle(input, ctx)
    }
}
