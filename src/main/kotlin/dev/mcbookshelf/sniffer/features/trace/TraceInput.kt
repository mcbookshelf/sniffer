package dev.mcbookshelf.sniffer.features.trace

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * @property start whether the trace is being opened, as opposed to closed
 * @property command the command being traced, as it was typed, `null` when the trace is being closed
 * @author theogiraudet
 */
data class TraceInput(val start: Boolean, val command: String? = null) : IInput
