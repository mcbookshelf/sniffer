package dev.mcbookshelf.sniffer.state

import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.nbt.CompoundTag
import java.util.Optional

/**
 * Owns the stack of debug scopes, which is the call hierarchy, and routes variable lookups to [VariableRegistry].
 *
 * A [DebugScope] is itself registered as a [VariableNode],
 * so one `variables` request resolves scope roots and nested variables alike.
 *
 * @author Alumopper
 * @author theogiraudet
 */
class ScopeManager private constructor() {

    val registry: VariableRegistry = VariableRegistry()

    /**
     * One entry of the call hierarchy, holding the function being run and the state to inspect it.
     * Its [VariableNode] has the executor, the location and the macro arguments as lazily built children.
     *
     * @param parent the scope that called this one, `null` at the bottom of the stack
     * @param function the `namespace:path` of the running function
     * @param executor the source the function runs as
     * @param macroVariables the arguments the function was instantiated with, `null` when it is not a macro
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

    /** The scope on top of the stack, as an [Optional] for Java interop. */
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
            // Execution is over, so the HUD icon has to go away.
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
     * @return the children of the node [id] refers to, empty if that node is unknown
     */
    fun getVariables(id: Int): Optional<List<VariableNode>> {
        val node = registry.get(id) ?: return Optional.empty()
        return Optional.of(node.children(registry))
    }

    /**
     * Drops the memoized variables of every live scope, so the next `variables` request reads the current game state.
     * Called right before a pause.
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
