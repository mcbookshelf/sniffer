package dev.mcbookshelf.sniffer.input

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Clear stepping counters and restore the debug toggle, preserving
 * breakpoints and scopes.
 */
data object ResetSteppingInput : IInput
