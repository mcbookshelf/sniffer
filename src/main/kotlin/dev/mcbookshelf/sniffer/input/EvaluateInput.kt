package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Evaluate a debug expression in the current scope.
 *
 * @property expression the expression string to evaluate.
 */
data class EvaluateInput(val expression: String) : IInput
