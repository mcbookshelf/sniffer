package dev.mcbookshelf.sniffer.state

import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.nbt.CompoundTag
import java.util.Optional

/**
 * Owns the debug scope stack (call hierarchy) and routes variable lookups
 * through the unified [VariableRegistry].
 *
 * Each [DebugScope] is itself registered as a [VariableNode] in the registry,
 * so the same `variables/{reference}` request resolves both scope-roots and
 * nested variables without any ID-range branching.
 *
 * @author theogiraudet
 */
class ScopeManager private constructor() {

    val registry: VariableRegistry = VariableRegistry()

    /**
     * Represents a debug scope during execution. The scope owns a root
     * [VariableNode] in [ScopeManager.registry]; that node's children are
     * the root variables (executor, location, macro) built lazily by
     * [VariableManager.buildRootVariables].
     */
    class DebugScope internal constructor(
        private val parent: DebugScope?,
        val function: String,
        val executor: ExecutionCommandSource<*>,
        val macroVariables: CompoundTag?,
        private val registry: VariableRegistry,
    ) {
        val path: RealPath? = FunctionPathRegistry.getRealPath(function)
        var line: Int = -2

        private val node: VariableNode = registry.register { id ->
            VariableNode(id, "Function", function, isRoot = false) { reg ->
                VariableManager.buildRootVariables(executor, macroVariables, reg)
            }
        }

        val id: Int get() = node.id

        fun rootVariables(): List<VariableNode> = node.children(registry)

        fun invalidate() = node.invalidate(registry)

        val callerFunction: Optional<String>
            get() = Optional.ofNullable(parent).map { it.function }

        val callerLine: Optional<Int>
            get() = Optional.ofNullable(parent).map { it.line }

        fun getOptionalPath(): Optional<RealPath> = Optional.ofNullable(path)
    }

    private val stack = ArrayDeque<DebugScope>()
    private val scopesById = HashMap<Int, DebugScope>()
    private var _currentScope: DebugScope? = null

    /**
     * The currently active scope, wrapped in an Optional for Java interop.
     */
    val currentScope: Optional<DebugScope>
        get() = Optional.ofNullable(_currentScope)

    @JvmOverloads
    fun newScope(function: String, executor: ExecutionCommandSource<*>, macroVariables: CompoundTag? = null) {
        val scope = DebugScope(_currentScope, function, executor, macroVariables, registry)
        stack.addLast(scope)
        scopesById[scope.id] = scope
        _currentScope = scope
    }

    fun unscope() {
        val top = stack.removeLastOrNull() ?: return
        top.invalidate()
        registry.drop(listOf(top.id))
        scopesById.remove(top.id)
        _currentScope = stack.lastOrNull()
        if (stack.isEmpty()) {
            // Execution finished — clear debugging state so the HUD icon disappears
            SteppingState.setDebugging(false)
        }
    }

    fun count(): Int = stack.size

    fun isEmpty(): Boolean = stack.isEmpty()

    fun clear() {
        stack.clear()
        scopesById.clear()
        _currentScope = null
        registry.clear()
    }

    fun getScope(id: Int): Optional<DebugScope> = Optional.ofNullable(scopesById[id])

    /**
     * Returns the children of the node referenced by [id] — either a scope's
     * root variables or a nested variable's children. Empty optional if [id]
     * is unknown.
     */
    fun getVariables(id: Int): Optional<List<VariableNode>> {
        val node = registry.get(id) ?: return Optional.empty()
        return Optional.of(node.children(registry))
    }

    /**
     * Drops memoized variable subtrees for every live scope so the next
     * DAP `variables` request rebuilds from current engine state. Called
     * by [UnboundDebugMixin] immediately before a pause.
     */
    fun refreshForPause() {
        for (scope in stack) scope.invalidate()
    }

    val debugScopes: List<DebugScope>
        get() = stack.reversed()

    companion object {
        @JvmStatic
        val instance: ScopeManager by lazy { ScopeManager() }

        @JvmStatic
        fun get(): ScopeManager = instance
    }
}
