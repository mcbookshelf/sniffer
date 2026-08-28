package dev.mcbookshelf.sniffer.features.source

import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import net.minecraft.resources.Identifier

/**
 * Retrieves the source text of a function by its Minecraft identifier.
 *
 * @author theogiraudet
 */
class GetSourceHandler : Handler<GetSourceInput> {

    override val inputType = GetSourceInput::class

    override fun handle(input: GetSourceInput, ctx: Context): Output {
        val id = Identifier.tryParse(input.functionId)
        val lines = if (id != null) FunctionTextLoader.get(id) else emptyList()
        return SourceOutput(content = lines.joinToString("\n"))
    }
}
