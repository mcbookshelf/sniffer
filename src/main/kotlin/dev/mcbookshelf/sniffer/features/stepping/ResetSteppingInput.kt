package dev.mcbookshelf.sniffer.features.stepping

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Clears the stepping counters, leaving breakpoints and scopes alone.
 *
 * @author theogiraudet
 */
data object ResetSteppingInput : IInput
