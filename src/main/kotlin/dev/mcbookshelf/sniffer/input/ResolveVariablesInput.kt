package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Retrieve variables for a given reference (scope or expression-evaluated).
 *
 * @property variablesReference the reference ID (scope ID or expression variable ID).
 * @property start optional 0-based start index for pagination.
 * @property count optional maximum number of variables to return.
 */
data class ResolveVariablesInput(
    val variablesReference: Int,
    val start: Int? = null,
    val count: Int? = null,
) : IInput
