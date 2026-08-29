package dev.mcbookshelf.sniffer.features.evaluate

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * What could be typed next, each carrying the span of the command it stands for.
 *
 * The span is what lets an editor replace a half typed word rather than append to it, and it is given in the
 * coordinates of the command the request carried, slash included, so the caller has nothing to shift.
 *
 * @author theogiraudet
 */
data class CompletionsOutput(val completions: List<Completion>) : Output

/**
 * @property text the suggestion itself
 * @property start where the text it replaces begins
 * @property length how long that text is, `0` when the suggestion is inserted at the caret
 * @property tooltip what the game says the suggestion means, `null` when it says nothing
 */
data class Completion(
    val text: String,
    val start: Int,
    val length: Int,
    val tooltip: String?,
)
