package dev.mcbookshelf.sniffer.state

import net.minecraft.commands.functions.InstantiatedFunction
import net.minecraft.nbt.CompoundTag
import java.util.WeakHashMap

/**
 * Holds the arguments a macro was instantiated with, keyed by the resulting [InstantiatedFunction].
 * `MacroInstantiationMixin` fills it, and a debug scope reads it to expose those arguments as variables.
 *
 * The [WeakHashMap] lets an entry go as soon as Minecraft evicts the function from its cache.
 *
 * @author theogiraudet
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
