package dev.mcbookshelf.sniffer.state

import com.google.common.base.Suppliers
import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import java.nio.file.Path
import java.util.Optional
import java.util.Stack
import java.util.function.Supplier

/**
 * Manages the debug scope stack (call hierarchy) and variable resolution.
 *
 * @author theogiraudet
 */
class ScopeManager private constructor() {

    /**
     * Represents a debug scope during execution.
     * A scope contains information about the currently executing function,
     * its variables, and its position in the call stack.
     */
    class DebugScope internal constructor(
        private val parent: DebugScope?,
        val function: String,
        val executor: ExecutionCommandSource<*>,
        val macroVariables: CompoundTag?
    ) {
        val path: RealPath? = FunctionPathRegistry.getRealPath(function)
        var line: Int = -2
        val id: Int = nextId++
        private val variables: Supplier<Map<Int, DebuggerVariable>> = Suppliers.memoize {
            val sourceVars = VariableManager.convertCommandSource(executor, nextId)
            if (macroVariables != null) {
                val macro = VariableManager.convertNbtCompound("macro", macroVariables, nextId + sourceVars.size, true)
                sourceVars.putAll(macro)
            }
            nextId += sourceVars.size
            sourceVars
        }

        fun getVariables(): List<DebuggerVariable> = variables.get().values.toList()

        val callerFunction: Optional<String>
            get() = Optional.ofNullable(parent).map { it.function }

        val callerLine: Optional<Int>
            get() = Optional.ofNullable(parent).map { it.line }

        val rootVariables: List<DebuggerVariable>
            get() = variables.get().values.filter { it.isRoot }.toList()

        fun getOptionalPath(): Optional<RealPath> = Optional.ofNullable(path)
    }

    private val debugScopeStack = Stack<DebugScope>()
    private var _currentScope: DebugScope? = null
    private val scopeIds = HashSet<Int>()

    /**
     * The currently active scope, wrapped in an Optional for Java interop.
     */
    val currentScope: Optional<DebugScope>
        get() = Optional.ofNullable(_currentScope)

    @Deprecated("Use FunctionPathRegistry.savePath directly", ReplaceWith("FunctionPathRegistry.savePath(path, id, kind)"))
    fun savePath(path: Path, id: Identifier, kind: RealPath.Kind) = FunctionPathRegistry.savePath(path, id, kind)

    @Deprecated("Use FunctionPathRegistry.getPath directly", ReplaceWith("FunctionPathRegistry.getPath(mcpath)"))
    fun getPath(mcpath: String): Optional<String> = FunctionPathRegistry.getPath(mcpath)

    @JvmOverloads
    fun newScope(function: String, executor: ExecutionCommandSource<*>, macroVariables: CompoundTag? = null) {
        val scope = DebugScope(_currentScope, function, executor, macroVariables)
        scopeIds.add(scope.id)
        debugScopeStack.push(scope)
        _currentScope = scope
    }

    fun unscope() {
        if (debugScopeStack.isEmpty()) return
        debugScopeStack.pop()
        scopeIds.remove(_currentScope!!.id)
        if (debugScopeStack.isEmpty()) {
            _currentScope = null
            // Execution finished — clear debugging state so the HUD icon disappears
            SteppingState.isDebugging = false
        } else {
            _currentScope = debugScopeStack.peek()
        }
    }

    fun count(): Int = debugScopeStack.size

    fun isEmpty(): Boolean = debugScopeStack.isEmpty()

    fun clear() {
        debugScopeStack.clear()
        scopeIds.clear()
        nextId = 1
    }

    @Deprecated("Use FunctionPathRegistry.clear directly", ReplaceWith("FunctionPathRegistry.clear()"))
    fun clearFunctionPaths() = FunctionPathRegistry.clear()

    fun getScope(id: Int): Optional<DebugScope> =
        debugScopeStack.stream().filter { it.id == id }.findFirst()

    private fun getRootVariables(scope: DebugScope): List<DebuggerVariable> =
        scope.rootVariables

    fun getVariables(id: Int): Optional<List<DebuggerVariable>> {
        if (id in scopeIds) {
            return debugScopeStack.stream().filter { it.id == id }.findFirst().map { getRootVariables(it) }
        }
        return debugScopeStack.stream()
            .flatMap { it.getVariables().stream() }
            .filter { it.id == id }
            .findFirst()
            .map { it.children }
    }

    val debugScopes: List<DebugScope>
        get() = debugScopeStack.reversed()

    companion object {
        private var nextId = 1

        @JvmStatic
        val instance: ScopeManager by lazy { ScopeManager() }

        @JvmStatic
        fun get(): ScopeManager = instance
    }
}
