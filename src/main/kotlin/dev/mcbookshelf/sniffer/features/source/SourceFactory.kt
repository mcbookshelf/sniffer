package dev.mcbookshelf.sniffer.features.source

import org.eclipse.lsp4j.debug.Source

/**
 * Builds the [Source] an editor opens a function with.
 *
 * A function living inside a zip cannot be opened by path, so it is given a reference the editor
 * sends back on a `source` request.
 * The reference is allocated once per function and kept for the run, since the editor may ask
 * for the content long after the scope it came from is gone.
 *
 * @author theogiraudet
 */
object SourceFactory {

    private val zipReferences = HashMap<String, Int>()

    private var nextZipReference = 1

    fun toSource(identity: FunctionIdentity): Source = Source().apply {
        name = identity.minecraftPath
        val real = identity.realPath ?: return@apply
        path = real.path
        if (real.kind == RealPath.Kind.ZIP) {
            sourceReference = zipReferences.getOrPut(identity.minecraftPath) { nextZipReference++ }
        }
    }
}
