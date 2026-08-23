package dev.mcbookshelf.sniffer.dispatch

import kotlin.reflect.KClass

/**
 * Routes an [IInput] to the single [Handler] registered for its exact type.
 *
 * An entrypoint builds an [IInput] from its own request format and calls [dispatch].
 * All the debugger behaviour lives in the handlers, entrypoints are only translators.
 * Handlers are listed explicitly at construction, so a new action costs one class and one line.
 *
 * @author theogiraudet
 */
class Dispatcher(handlers: List<Handler<*>>) {

    private val handlers: Map<KClass<out IInput>, Handler<*>> =
        handlers.associateBy { it.inputType }.also {
            require(it.size == handlers.size) {
                "Duplicate Handler registered for the same IInput type"
            }
        }

    /**
     * @throws IllegalStateException if no handler is registered for the exact type of [input]
     */
    @Suppress("UNCHECKED_CAST")
    fun <I : IInput> dispatch(input: I, ctx: Context): Output {
        val handler = handlers[input::class]
            ?: error("No Handler registered for ${input::class.qualifiedName}")
        return (handler as Handler<I>).handle(input, ctx)
    }
}
