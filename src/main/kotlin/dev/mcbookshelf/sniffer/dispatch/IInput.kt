package dev.mcbookshelf.sniffer.dispatch

/**
 * Marker interface for an action request sent to the [Dispatcher].
 *
 * Each concrete [IInput] corresponds to exactly one debugger action
 * (step over, step into, set breakpoint, ...) and is produced by an
 * entrypoint (DAP server, in-game command) from its own native request
 * format.
 *
 * Implementations should be immutable data classes carrying only the
 * parameters of the action.
 */
interface IInput

/**
 * Specialization of [IInput] for step actions that carry a [lines] count
 * (number of commands to advance before re-pausing).
 *
 * Implemented by [dev.mcbookshelf.sniffer.input.StepInInput],
 * [dev.mcbookshelf.sniffer.input.StepOverInput], and
 * [dev.mcbookshelf.sniffer.input.StepOutInput].
 */
interface StepInput : IInput {
    val lines: Int
}
