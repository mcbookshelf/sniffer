package dev.mcbookshelf.sniffer.dispatch

import kotlin.reflect.KClass

/**
 * Handles one concrete [IInput] subtype.
 *
 * The generic parameter [I] plus [inputType] let the [Dispatcher] route
 * inputs by exact class without any `instanceof` check. Each handler is
 * expected to live in its own file and to be added to the central list
 * in `HandlersRegistry.kt`.
 *
 * @param I the [IInput] subtype this handler is responsible for.
 */
interface Handler<I : IInput> {

    /** The exact [IInput] subtype this handler accepts. Used as the routing key. */
    val inputType: KClass<I>

    /**
     * Execute the action described by [input], possibly mutating state through [ctx],
     * and return an [Output] that the calling entrypoint will translate back to its
     * native response format.
     */
    fun handle(input: I, ctx: Context): Output
}
