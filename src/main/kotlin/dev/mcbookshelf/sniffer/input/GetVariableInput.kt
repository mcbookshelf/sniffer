package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

data class GetVariableInput(val key: String) : IInput
