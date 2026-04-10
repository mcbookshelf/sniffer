package dev.mcbookshelf.sniffer.output

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of a source text retrieval.
 *
 * @property content the full source text of the function.
 * @property mimeType the MIME type of the content.
 */
data class SourceOutput(
    val content: String,
    val mimeType: String = "text/mcfunction",
) : Output
