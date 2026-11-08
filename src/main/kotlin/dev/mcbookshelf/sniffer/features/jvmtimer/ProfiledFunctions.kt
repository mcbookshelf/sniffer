package dev.mcbookshelf.sniffer.features.jvmtimer

/**
 * Tracks which function IDs were marked with `#!jvmtimer profile file`.
 * Filled at parse time by [FunctionParsingMixin], read at runtime by [CallFunctionMixin].
 *
 * A static set is used instead of checking the [CommandFunctionUniqueAccessors.profiled] flag
 * because macro functions return an anonymous [InstantiatedFunction] that does not carry the mixin.
 */
object ProfiledFunctions {
    private val ids: MutableSet<String> = mutableSetOf()

    @JvmStatic
    fun register(functionId: String) {
        ids.add(functionId)
    }

    @JvmStatic
    fun unregister(functionId: String) {
        ids.remove(functionId)
    }

    @JvmStatic
    fun isProfiled(functionId: String): Boolean = functionId in ids
}
