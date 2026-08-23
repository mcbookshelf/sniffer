package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Evaluates a debug expression in the current scope.
 *
 * @property expression the expression to evaluate
 * @author theogiraudet
 */
data class EvaluateInput(val expression: String) : IInput
