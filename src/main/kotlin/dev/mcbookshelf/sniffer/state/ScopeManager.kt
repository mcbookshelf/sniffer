package dev.mcbookshelf.sniffer.state

import dev.mcbookshelf.sniffer.domain.DebugScope
import net.minecraft.commands.ExecutionCommandSource
import net.minecraft.nbt.CompoundTag
import java.util.Optional
import kotlin.collections.ArrayDeque
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.reversed
import kotlin.collections.set

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

    private val stack = ArrayDeque<DebugScope>()
    private val scopesById = HashMap<Int, DebugScope>()
    private var _currentScope: DebugScope? = null

    /** The scope on top of the stack, as an [Optional] for Java interop. */
    val currentScope: Optional<DebugScope>
        get() = Optional.ofNullable(_currentScope)

    @JvmOverloads
    fun newScope(function: String, executor: ExecutionCommandSource<*>, macroVariables: CompoundTag? = null) {
        val node = registry.register { id ->
            VariableNode(id, "Function", function, isRoot = false) { reg ->
                VariableManager.buildRootVariables(executor, macroVariables, reg)
            }
        }
        val scope = DebugScope(
            _currentScope, function, executor, macroVariables,
            FunctionPathRegistry.getRealPath(function), node.id,
        )
        stack.addLast(scope)
        scopesById[scope.id] = scope
        _currentScope = scope
    }

    fun unscope() {
        val top = stack.removeLastOrNull() ?: return
        invalidate(top)
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
        for (scope in stack) invalidate(scope)
    }

    /** Drops the memoized variables of [scope], keeping its own node registered. */
    private fun invalidate(scope: DebugScope) {
        registry.get(scope.id)?.invalidate(registry)
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
