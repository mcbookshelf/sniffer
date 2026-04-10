package dev.mcbookshelf.sniffer.handlers

import dev.mcbookshelf.sniffer.state.FunctionTextLoader
import dev.mcbookshelf.sniffer.dispatch.Context
import dev.mcbookshelf.sniffer.dispatch.Handler
import dev.mcbookshelf.sniffer.dispatch.Output
import dev.mcbookshelf.sniffer.input.GetSourceInput
import dev.mcbookshelf.sniffer.output.SourceOutput
import net.minecraft.resources.Identifier

/**
 * Retrieves the source text of a function by its Minecraft identifier.
 */
class GetSourceHandler : Handler<GetSourceInput> {

    override val inputType = GetSourceInput::class

    override fun handle(input: GetSourceInput, ctx: Context): Output {
        val id = Identifier.tryParse(input.functionId)
        val lines = if (id != null) FunctionTextLoader.get(id) else emptyList()
        return SourceOutput(content = lines.joinToString("\n"))
    }
}
