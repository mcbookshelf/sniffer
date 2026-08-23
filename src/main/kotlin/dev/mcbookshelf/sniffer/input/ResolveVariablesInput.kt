package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieves the variables a reference points to, whether it comes from a scope or from an evaluation.
 *
 * @property variablesReference id of the node whose children are wanted
 * @property start zero indexed position of the first variable to return, `null` to start at the beginning
 * @property count how many variables to return at most, `null` for all of them
 * @author theogiraudet
 */
data class ResolveVariablesInput(
    val variablesReference: Int,
    val start: Int? = null,
    val count: Int? = null,
) : IInput
