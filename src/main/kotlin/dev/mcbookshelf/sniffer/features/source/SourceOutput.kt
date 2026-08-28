package dev.mcbookshelf.sniffer.features.source

import dev.mcbookshelf.sniffer.dispatch.Output

/**
 * Result of a source text request.
 *
 * @property content the whole source text of the function
 * @property mimeType the MIME type the client should read it as
 * @author theogiraudet
 */
data class SourceOutput(
    val content: String,
    val mimeType: String = "text/mcfunction",
) : Output
