package dev.mcbookshelf.sniffer.dispatch

import kotlin.reflect.KClass

/**
 * Handles one concrete [IInput] subtype.
 * Each handler lives in its own file and is added to the list built by `HandlersRegistry`.
 *
 * @param I the [IInput] subtype this handler is responsible for
 * @author theogiraudet
 */
interface Handler<I : IInput> {

    /** Routing key of the handler, the exact input subtype it accepts. */
    val inputType: KClass<I>

    /**
     * Runs the action described by [input] and returns what the entrypoint has to answer with.
     */
    fun handle(input: I, ctx: Context): Output
}
