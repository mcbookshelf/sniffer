package dev.mcbookshelf.sniffer.features.callstack

import dev.mcbookshelf.sniffer.dispatch.IInput

/**
 * Throws away the call hierarchy, leaving breakpoints and stepping counters alone.
 * The observers are told, so whatever was following the control flow stops with it.
 *
 * @author theogiraudet
 */
data object ClearScopesInput : IInput
