package dev.mcbookshelf.sniffer.features.callstack

import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.nbt.CompoundTag
import java.util.Optional
import dev.mcbookshelf.sniffer.features.source.RealPath

/**
 * One entry of the call hierarchy, holding the function being run and the state to inspect it.
 *
 * @param parent the scope that called this one, `null` at the bottom of the stack
 * @param function the `namespace:path` of the running function
 * @param executor the source the function runs as
 * @param macroVariables the arguments the function was instantiated with, `null` when it is not a macro
 * @param path the on disk source of [function], `null` when it could not be resolved
 * @param id the reference the DAP client uses to ask for the variables of this scope
 *
 * @author Alumopper
 * @author theogiraudet
 */
class DebugScope internal constructor(
    private val parent: DebugScope?,
    val function: String,
    val executor: ExecutionCommandSource<*>,
    val macroVariables: CompoundTag?,
    val path: RealPath?,
    val id: Int,
) {
    var line: Int = -2

    val callerFunction: Optional<String>
        get() = Optional.ofNullable(parent).map { it.function }

    val callerLine: Optional<Int>
        get() = Optional.ofNullable(parent).map { it.line }

    fun getOptionalPath(): Optional<RealPath> = Optional.ofNullable(path)
}
