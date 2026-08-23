package dev.mcbookshelf.sniffer.dispatch

/**
 * An action request sent to the [Dispatcher].
 *
 * One implementation stands for one debugger action, and is built by an entrypoint from its own request format.
 * Implementations are immutable data classes carrying nothing but the parameters of the action.
 *
 * @author theogiraudet
 */
interface IInput

/**
 * The inputs of the three step actions.
 *
 * @property lines how many commands to run before pausing again
 * @author theogiraudet
 */
interface StepInput : IInput {
    val lines: Int
}
