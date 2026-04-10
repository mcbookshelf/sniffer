package dev.mcbookshelf.sniffer.dispatch

/**
 * Marker interface for the result of handling an [IInput].
 *
 * Entrypoints are responsible for translating an [Output] back into
 * their native response format (DAP response, chat feedback, ...).
 *
 * Concrete outputs live in `dev.mcbookshelf.sniffer.output`.
 */
interface Output
