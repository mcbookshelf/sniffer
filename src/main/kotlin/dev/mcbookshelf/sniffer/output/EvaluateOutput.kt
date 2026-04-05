package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of evaluating a debug expression.
 *
 * @property result the string representation of the evaluated value, or the error message.
 * @property variablesReference reference ID for expanding compound results (0 if not expandable).
 */
data class EvaluateOutput(
    val result: String,
    val variablesReference: Int = 0,
) : Output
