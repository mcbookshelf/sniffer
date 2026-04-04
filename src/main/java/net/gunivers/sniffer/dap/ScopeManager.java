package net.gunivers.sniffer.dap;

import com.google.common.base.Suppliers;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/**
 * Manager for debug scopes in the debugger.
 * Handles scope lifecycle, variable access, and scope stacking during debugging.
 * Implemented as a singleton to ensure a single instance is used throughout the application.
 *
 * @author theogiraudet
 */
public class ScopeManager {

    private static ScopeManager singleton;
    private static final Map<String, RealPath> PATHS = new HashMap<>();

    /**
     * Private constructor for singleton pattern.
     */
    private ScopeManager() {}

    /**
     * Gets the singleton instance of the ScopeManager.
     * Thread-safe implementation with double-checked locking.
     * 
     * @return The ScopeManager singleton instance
     */
    public static ScopeManager get() {
        if (singleton == null) {
            synchronized (ScopeManager.class) {
                if (singleton == null) {
                    singleton = new ScopeManager();
                }
            }
        }
        return singleton;
    }

    private static int ID = 1;

    /**
     * Represents a debug scope during execution.
     * A scope contains information about the currently executing function,
     * its variables, and its position in the call stack.
     */
    public static class DebugScope {
        private final String function;
        private final RealPath path;
        private final ExecutionCommandSource<?> executor;
        private int line = -2;
        private final Supplier<Map<Integer, DebuggerVariable>> variables;
        private final int id = ID++;
        private final DebugScope parent;

        /**
         * Creates a new debug scope for a macro
         * 
         * @param parent The parent scope, or null if this is the root scope
         * @param function The function mcpath being executed
         * @param executor The command source executing the function
         * @param macroVariables The NBT compound containing macro variables
         */
        protected DebugScope(@Nullable DebugScope parent, String function, ExecutionCommandSource<?> executor,@Nullable CompoundTag macroVariables) {
            this.parent = parent;
            this.function = function;
            this.path = PATHS.get(function);
            this.executor = executor;
            this.variables = Suppliers.memoize(() -> {
                var qwq = VariableManager.convertCommandSource(executor, ID);
                var macro = VariableManager.convertNbtCompound("macro", macroVariables, ID + qwq.size(), true);
                qwq.putAll(macro);
                ID += qwq.size();
                return qwq;
            });
        }

        /**
         * Creates a new debug scope for a normal function
         *
         * @param parent The parent scope, or null if this is the root scope
         * @param function The function mcpath being executed
         * @param executor The command source executing the function
         */
        protected DebugScope(@Nullable DebugScope parent, String function, ExecutionCommandSource<?> executor) {
            this.parent = parent;
            this.function = function;
            this.path = PATHS.get(function);
            this.executor = executor;
            this.variables = Suppliers.memoize(() -> {
                var qwq = VariableManager.convertCommandSource(executor, ID);
                ID += qwq.size();
                return qwq;
            });
        }

        /**
         * Gets the function mcpath of this scope.
         * 
         * @return The function mcpath
         */
        public String getFunction() {
            return function;
        }

        /**
         * Gets the current line number in the function.
         * 
         * @return The line number, or -1 if not set
         */
        public int getLine() {
            return line;
        }

        /**
         * Gets the command source executing this scope.
         * 
         * @return The command source
         */
        public ExecutionCommandSource<?> getExecutor() {
            return executor;
        }

        /**
         * Gets the unique ID for this scope.
         * 
         * @return The scope ID
         */
        public int getId() {
            return id;
        }

        /**
         * Gets all variables in this scope.
         * 
         * @return A list of all variables in this scope
         */
        public List<DebuggerVariable> getVariables() {
            return List.copyOf(variables.get().values());
        }

        /**
         * Gets the function name of the caller (parent scope).
         * 
         * @return An Optional containing the caller function, or empty if this is the root scope
         */
        public Optional<String> getCallerFunction() {
            return Optional.ofNullable(parent).map(DebugScope::getFunction);
        }

        /**
         * Gets the line number in the caller where this function was called.
         * 
         * @return An Optional containing the caller line, or empty if this is the root scope
         */
        public Optional<Integer> getCallerLine() {
            return Optional.ofNullable(parent).map(DebugScope::getLine);
        }

        /**
         * Gets all root-level variables in this scope.
         * 
         * @return A list of root variables
         */
        public List<DebuggerVariable> getRootVariables() {
            return this.variables.get().values().stream().filter(DebuggerVariable::isRoot).toList();
        }

        /**
         * Sets the current line number for this scope.
         * 
         * @param currentLine The line number to set
         */
        public void setLine(int currentLine) {
            this.line = currentLine;
        }

        public Optional<RealPath> getPath() {
            return Optional.ofNullable(path);
        }
    }

    /** Stack of debug scopes representing the call hierarchy */
    private final Stack<DebugScope> debugScopeStack = new Stack<>();
    /** The currently active scope */
    private DebugScope currentScope = null;
    /** Set of known scope IDs for quick lookups */
    private final Set<Integer> scopeIds = new HashSet<>();

    /**
     * Saves the physical file path for a function identified by its Minecraft identifier.
     * This mapping is essential for the debugger to locate the source files.
     * 
     * @param path The physical file path of the function
     * @param id The Minecraft identifier for the function
     */
    public void savePath(Path path, ResourceLocation id, RealPath.Kind kind) {
        var location = id.getNamespace() + ":" + id.getPath().substring("function/".length(), id.getPath().length() - ".mcfunction".length());
        PATHS.putIfAbsent(location, new RealPath(path.toAbsolutePath().toString(), kind));
    }

    /**
     * Retrieves the physical file path for a function identified by its Minecraft path.
     * 
     * @param mcpath The Minecraft path of the function (format: namespace:path)
     * @return An Optional containing the physical file path if found, or empty if no mapping exists
     */
    public Optional<String> getPath(String mcpath) {
        return Optional.ofNullable(PATHS.get(mcpath)).map(RealPath::path);
    }

    /**
     * Creates a new scope and pushes it onto the scope stack.
     * 
     * @param function The function mcpath
     * @param executor The command source executing the function
     */
    public void newScope(String function, ExecutionCommandSource<?> executor, CompoundTag macroVariables) {
        var scope = new DebugScope(this.currentScope, function, executor, macroVariables);
        this.scopeIds.add(scope.id);
        this.debugScopeStack.push(scope);
        this.currentScope = scope;
    }

    public void newScope(String function, ExecutionCommandSource<?> executor) {
        var scope = new DebugScope(this.currentScope, function, executor);
        this.scopeIds.add(scope.id);
        this.debugScopeStack.push(scope);
        this.currentScope = scope;
    }

    /**
     * Removes the current scope from the stack and sets the parent as current.
     * Call this when exiting a function.
     */
    public void unscope() {
        if(this.debugScopeStack.isEmpty()) {
            return;
        }
        debugScopeStack.pop();
        this.scopeIds.remove(currentScope.id);
        currentScope = debugScopeStack.isEmpty() ? null : debugScopeStack.peek();
    }

    /**
     * Gets the number of active scopes.
     * 
     * @return The number of scopes in the stack
     */
    public int count() {
        return this.debugScopeStack.size();
    }

    /**
     * If the scope stack is empty.
     */
    public boolean isEmpty(){
        return this.debugScopeStack.isEmpty();
    }

    /**
     * Clears all scopes and resets the state.
     */
    public void clear() {
        this.debugScopeStack.clear();
        this.scopeIds.clear();
        ID = 1;
    }

    /**
     * Clears the mapping between Minecraft function paths and physical file paths.
     * This should be called when datapacks are reloaded to prevent stale mappings.
     */
    public void clearFunctionPaths() {
        PATHS.clear();
    }

    /**
     * Retrieves a debug scope by its ID.
     * 
     * @param id The ID of the scope to find
     * @return An Optional containing the scope if found, or empty if no scope with the given ID exists
     */
    public Optional<DebugScope> getScope(int id) {
        return this.debugScopeStack.stream()
                .filter(scope -> scope.id == id)
                .findFirst();
    }

    /**
     * Gets all root-level variables from a given scope.
     * A root variable is one that should be displayed directly in the scope view.
     * 
     * @param scope The scope to get variables from
     * @return A list of root-level variables in this scope
     */
    private List<DebuggerVariable> getRootVariables(DebugScope scope) {
        return scope.variables.get().values().stream().filter(DebuggerVariable::isRoot).toList();
    }

    /**
     * Gets variables associated with a given ID.
     * If the ID matches a scope, returns root variables of that scope.
     * If the ID matches a variable, returns its children.
     * 
     * @param id The ID of the scope or variable
     * @return An Optional containing the list of variables, or empty if no match was found
     */
    public Optional<List<DebuggerVariable>> getVariables(int id) {
        if(this.scopeIds.contains(id)) {
            return debugScopeStack.stream().filter(scope -> scope.id == id).findFirst().map(this::getRootVariables);
        } else {
            return this.debugScopeStack.stream()
                    .flatMap(scope -> scope.getVariables().stream())
                    .filter(var -> var.id() == id)
                    .findFirst()
                    .map(DebuggerVariable::children);
        }
    }

    /**
     * Gets all active debug scopes in the call stack.
     * 
     * @return The list of all debug scopes
     */
    public List<DebugScope> getDebugScopes() {
        return this.debugScopeStack.reversed();
    }

    /**
     * Gets the currently active scope.
     * 
     * @return An Optional containing the current scope, or empty if no scope is active
     */
    public Optional<DebugScope> getCurrentScope() {
        return Optional.ofNullable(currentScope);
    }
}
