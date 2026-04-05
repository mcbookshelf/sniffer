package dev.mcbookshelf.sniffer.state

import net.minecraft.commands.functions.InstantiatedFunction
import net.minecraft.nbt.CompoundTag
import java.util.WeakHashMap

/**
 * External store for macro arguments, keyed by [InstantiatedFunction] instance.
 *
 * When a [net.minecraft.commands.functions.MacroFunction] is instantiated,
 * [MacroInstantiationMixin] captures the `CompoundTag` arguments and stores
 * them here. The scope manager later retrieves them when building debug
 * scopes for variable inspection.
 *
 * Uses a [WeakHashMap] so entries are garbage-collected when the
 * [InstantiatedFunction] is no longer referenced (e.g. evicted from the
 * macro function's LRU cache).
 */
object MacroArgsStore {

    private val store = WeakHashMap<InstantiatedFunction<*>, CompoundTag>()

    @JvmStatic
    fun put(function: InstantiatedFunction<*>, args: CompoundTag) {
        store[function] = args
    }

    @JvmStatic
    fun get(function: InstantiatedFunction<*>): CompoundTag? =
        store[function]

    @JvmStatic
    fun remove(function: InstantiatedFunction<*>) {
        store.remove(function)
    }

    @JvmStatic
    fun clear() {
        store.clear()
    }
}
