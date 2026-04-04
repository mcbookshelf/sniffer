package net.gunivers.sniffer.dap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.gunivers.sniffer.command.BreakPointCommand.isDebugging;

/**
 * Singleton class that maintains the state of the debugger.
 * Handles breakpoints, execution state, and debug events.
 *
 * @author theogiraudet
 */
public class DebuggerState {

    /** Logger for this class */
    private static final Logger LOGGER = LoggerFactory.getLogger("sniffer");
    
    /** Pattern to extract namespace and path from a datapack function mcpath path */
    private static final Pattern PATH_PATTERN = Pattern.compile("data/(?<namespace>.+)/function/(?<path>.+)\\.mcfunction");
    
    /** Standard reason string for breakpoint stop events */
    private static final String BREAKPOINT_REASON = "breakpoint";

    /** Default thread ID used for DAP communication */
    public static final int DEFAULT_THREAD_ID = 1;
    
    /** Thread-safe singleton instance with lazy initialization */
    private static volatile DebuggerState singleton;

    /** Current Minecraft server instance */
    private MinecraftServer server;
    /** Scope manager for tracking function call hierarchy */
    private final ScopeManager scopeManager = ScopeManager.get();
    
    /** Listeners for debugger stop events that receive breakpoint ID and reason */
    private final List<BiConsumer<Integer, String>> stopConsumers = new LinkedList<>();
    /** Listeners for debugger continue events */
    private final List<Runnable> continueRunnable = new LinkedList<>();
    /** Handlers to be called when the debugger is shutting down */
    private final List<Runnable> shutdownHandlers = new LinkedList<>();
    
    /** Container for all breakpoints in all functions */
    private final AllBreakpoints breakpoints = new AllBreakpoints();
    /** Counter for generating unique breakpoint IDs */
    private int breakpointNextId = 0;
    
    /** Thread identifier for DAP communication */
    public final int THREAD_ID = DEFAULT_THREAD_ID;
    
    /** Flag indicating whether the debugger has been shut down */
    private boolean isShutdown = false;

    public static CommandSourceStack currSource = null;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private DebuggerState() {
        LOGGER.debug("DebuggerState instance created");
    }

    /**
     * Gets the singleton instance of DebuggerState.
     * Uses double-checked locking for thread safety.
     * 
     * @return The DebuggerState singleton instance
     */
    public static DebuggerState get() {
        if (singleton == null) {
            synchronized (DebuggerState.class) {
                if (singleton == null) {
                    singleton = new DebuggerState();
                }
            }
        }
        return singleton;
    }

    /**
     * Record to represent a breakpoint with its ID and line number.
     */
    private record Breakpoint(int id, int line) {}

    /**
     * Class to store breakpoints for a single Minecraft function.
     */
    private static class FunctionBreakpoints {
        private final String functionPath;
        private final String functionMcPath;
        // Map of breakpoint line to Breakpoint object
        private final Map<Integer, Breakpoint> breakpoints = new HashMap<>();

        /**
         * Creates a new FunctionBreakpoints instance.
         *
         * @param functionMcPath The Minecraft path of the function (e.g. "namespace:path/to/function")
         * @param functionPath The filesystem path to the function
         */
        public FunctionBreakpoints(String functionMcPath, String functionPath) {
            this.functionMcPath = functionMcPath;
            this.functionPath = functionPath;
        }
        
        /**
         * Gets the filesystem path to the function.
         *
         * @return The filesystem path
         */
        public String getFunctionPath() {
            return functionPath;
        }
    }

    /**
     * Class to manage all breakpoints across different functions.
     */
    private class AllBreakpoints {
        private final Map<Path, FunctionBreakpoints> breakpoints = new HashMap<>();

        /**
         * Adds a breakpoint to a mcpath at the specified line.
         *
         * @param filePath The filesystem path to the mcpath
         * @param line The line number (0-indexed)
         * @return Optional containing the breakpoint ID if successful, empty otherwise
         */
        public Optional<Integer> addBreakpoint(String filePath, int line) {
            if (filePath == null) {
                LOGGER.warn("Attempted to add breakpoint with null mcpath path");
                return Optional.empty();
            }
            // Sometimes on Windows we have a path like `c:\foo\bar`, this instruction enables the conversion to `C:\foo\bar`
            filePath = Paths.get(filePath).toString();
            
            Optional<String> mcpathOpt = fileToMcPath(filePath);
            if (mcpathOpt.isPresent()) {
                String mcpath = mcpathOpt.get();
                var funBreakpoints = breakpoints.getOrDefault(Path.of(filePath), new FunctionBreakpoints(mcpath, filePath));
                breakpoints.put(Path.of(filePath), funBreakpoints);
                
                int uuid = breakpointNextId++;
                funBreakpoints.breakpoints.put(line, new Breakpoint(uuid, line));

                LOGGER.debug("Added breakpoint {} at {}:{}", uuid, mcpath, line);
                return Optional.of(uuid);
            }

            LOGGER.warn("Failed to add breakpoint at {}:{} - Could not convert to MC path", filePath, line);
            return Optional.empty();
        }

        /**
         * Clears all breakpoints for a mcpath.
         *
         * @param filePath The filesystem path to the mcpath
         */
        public void clearBreakpoints(String filePath) {
            if (filePath == null) {
                LOGGER.warn("Attempted to clear breakpoints with null mcpath path");
                return;
            }
            

            breakpoints.remove(Path.of(filePath));
            LOGGER.debug("Cleared all breakpoints for {}", filePath);
        }

        /**
         * Checks if a breakpoint exists at the specified location.
         *
         * @param mcpath The Minecraft path of the function
         * @param line The line number (0-indexed)
         * @return true if a breakpoint exists at the location, false otherwise
         */
        public boolean contains(String mcpath, int line) {
            if (mcpath == null) {
                return false;
            }

            var path = scopeManager.getPath(mcpath);
            return path.map(Path::of).map(breakpoints::get)
                    .map(funBreakpoints -> funBreakpoints.breakpoints.containsKey(line))
                    .orElse(false);
        }

        /**
         * Gets the ID of a breakpoint at the specified location.
         *
         * @param mcpath The Minecraft path of the function
         * @param line The line number (0-indexed)
         * @return Optional containing the breakpoint ID if it exists, empty otherwise
         */
        public Optional<Integer> getBreakpointId(String mcpath, int line) {
            if (mcpath == null) {
                return Optional.empty();
            }
            var filePath = scopeManager.getPath(mcpath).map(Path::of);
            return filePath.map(this.breakpoints::get)
                    .flatMap(bps -> Optional.ofNullable(bps.breakpoints.get(line)))
                    .map(Breakpoint::id);
        }
        
        /**
         * Gets the function breakpoints map.
         *
         * @return The map of function paths to FunctionBreakpoints
         */
        public Map<Path, FunctionBreakpoints> getBreakpointsMap() {
            return Collections.unmodifiableMap(breakpoints);
        }
    }

    /**
     * Checks if execution should stop at the specified location.
     *
     * @param mcpath The Minecraft path of the function
     * @param line The line number (0-indexed)
     * @return true if execution should stop, false otherwise
     */
    public boolean mustStop(String mcpath, int line) {
        // Only stop if there's a breakpoint at this location and we're not already stopped here
        return breakpoints.contains(mcpath, line) && !isAtCurrentPosition(mcpath, line);
    }
    
    /**
     * Checks if the specified location is the current execution position.
     *
     * @param file The Minecraft path of the function
     * @param line The line number
     * @return true if the location is the current position, false otherwise
     */
    private boolean isAtCurrentPosition(String file, int line) {
        var functionName = this.scopeManager.getCurrentScope().map(ScopeManager.DebugScope::getFunction).orElse("");
        var functionLine = this.scopeManager.getCurrentScope().map(ScopeManager.DebugScope::getLine).orElse(-1);
        return file.equals(functionName) && line == functionLine;
    }

    /**
     * Registers a breakpoint at the specified location.
     *
     * @param file The filesystem path to the mcpath
     * @param line The line number (0-indexed)
     * @return Optional containing the breakpoint ID if successful, empty otherwise
     */
    public Optional<Integer> registerBreakpoint(String file, int line) {
        return breakpoints.addBreakpoint(file, line);
    }

    /**
     * Sets the Minecraft server instance and registers server shutdown hooks.
     *
     * @param server The Minecraft server instance
     */
    public void setServer(@Nullable MinecraftServer server) {
        this.server = server;
        LOGGER.debug("Server reference set");
        
        // If we're replacing a server, make sure to clean up the old one
        if (server == null) {
            shutdown();
        }
    }

    /**
     * Gets the Minecraft server instance.
     *
     * @return The Minecraft server instance
     * @throws IllegalStateException if the server is not set
     */
    public @NotNull MinecraftServer getServer() {
        if (server == null) {
            LOGGER.error("Attempted to get server when it was not set");
            throw new IllegalStateException("Server not set");
        }
        return server;
    }
    
    /**
     * Gets the server command source.
     *
     * @return The server command source
     * @throws IllegalStateException if the server is not set
     */
    public @NotNull CommandSourceStack getCommandSource() {
        return getServer().createCommandSourceStack();
    }

    /**
     * Triggers a breakpoint at the current execution position.
     * Freezes the server tick manager and enables debugging mode.
     *
     * @param source The command source that triggered the breakpoint
     */
    public void triggerBreakpoint(@NotNull CommandSourceStack source) {
        try {
            // Freeze the server to pause execution
            source.getServer().tickRateManager().setFrozen(true);
            
            // Notify all stop consumers
            notifyStopConsumersForCurrentPosition();
            
            // Set debugging flag
            isDebugging = true;

            currSource = source;

            LOGGER.debug("Breakpoint triggered at {}:{}", this.scopeManager.getCurrentScope().get().getFunction(), this.scopeManager.getCurrentScope().get().getLine());
        } catch (Exception e) {
            LOGGER.error("Error triggering breakpoint", e);
        }
    }
    
    /**
     * Notifies all stop consumers about a breakpoint at the current position.
     */
    private void notifyStopConsumersForCurrentPosition() {
        var functionName = this.scopeManager.getCurrentScope().map(ScopeManager.DebugScope::getFunction).orElse("");
        var functionLine = this.scopeManager.getCurrentScope().map(ScopeManager.DebugScope::getLine).orElse(-1);
        int breakpointId = breakpoints.getBreakpointId(functionName, functionLine).orElse(-1);
        for (BiConsumer<Integer, String> consumer : stopConsumers) {
            try {
                consumer.accept(breakpointId, BREAKPOINT_REASON);
            } catch (Exception e) {
                LOGGER.warn("Error notifying stop consumer", e);
            }
        }
    }

    /**
     * Stops execution for a reason other than a breakpoint.
     *
     * @param reason The reason for stopping
     */
    public void stop(String reason) {
        final String stopReason = reason != null ? reason : "unknown";
        
        stopConsumers.forEach(consumer -> {
            try {
                consumer.accept(-1, stopReason);
            } catch (Exception e) {
                LOGGER.warn("Error in stop consumer", e);
            }
        });

        LOGGER.debug("Execution stopped: {}", stopReason);
    }

    /**
     * Continues execution after a breakpoint.
     * Notifies all continue handlers.
     */
    public void continueExec() {
        continueRunnable.forEach(runnable -> {
            try {
                runnable.run();
            } catch (Exception e) {
                LOGGER.warn("Error in continue handler", e);
            }
        });
        
        LOGGER.debug("Execution continued");
    }

    /**
     * Clears all breakpoints for a mcpath.
     *
     * @param file The filesystem path to the mcpath
     */
    public void clearBreakpoints(String file) {
        breakpoints.clearBreakpoints(file);
    }

    /**
     * Registers a handler to be called when execution stops.
     *
     * @param consumer The handler to call (receives breakpoint ID and reason)
     */
    public void onStop(BiConsumer<Integer, String> consumer) {
        if (consumer != null) {
            this.stopConsumers.add(consumer);
        }
    }

    /**
     * Registers a handler to be called when execution continues.
     *
     * @param runnable The handler to call
     */
    public void onContinue(Runnable runnable) {
        if (runnable != null) {
            this.continueRunnable.add(runnable);
        }
    }

    /**
     * Converts a filesystem path to a Minecraft function path.
     *
     * @param path The filesystem path
     * @return Optional containing the Minecraft path if conversion is successful, empty otherwise
     */
    private static Optional<String> fileToMcPath(String path) {
        if (path == null) {
            return Optional.empty();
        }
        
        String realPath = FilenameUtils.separatorsToUnix(path);
        Matcher matcher = PATH_PATTERN.matcher(realPath);
        
        if (matcher.find()) {
            var namespace = matcher.group("namespace");
            var rpath = matcher.group("path");
            
            if (rpath != null && namespace != null) {
                return Optional.of(namespace + ":" + rpath);
            }
        }
        
        return Optional.empty();
    }

    /**
     * Registers a handler to be called when the debugging session should be shut down.
     * 
     * @param handler The handler to call when shutting down
     */
    public void onShutdown(Runnable handler) {
        if (handler != null) {
            shutdownHandlers.add(handler);
        }
    }
    
    /**
     * Signals that Minecraft is shutting down or the world is being unloaded.
     * This will notify all registered shutdown handlers and clean up resources.
     */
    public void shutdown() {
        if (isShutdown) {
            return; // Prevent multiple shutdowns
        }
        
        LOGGER.info("Shutting down debugger state");
        try {
            // Notify all registered shutdown handlers
            for (Runnable handler : shutdownHandlers) {
                try {
                    handler.run();
                } catch (Exception e) {
                    LOGGER.warn("Error in shutdown handler", e);
                }
            }
            
            // Clear all breakpoints and handlers
            this.breakpoints.breakpoints.clear();
            stopConsumers.clear();
            continueRunnable.clear();
            shutdownHandlers.clear();
            this.scopeManager.clear();
            
            // Reset state
            isShutdown = true;
            
            LOGGER.info("Debugger state shutdown complete");
        } catch (Exception e) {
            LOGGER.error("Error during debugger shutdown", e);
        }
    }
    
    /**
     * Resets the debugger state to allow a new session.
     * This method is called when the server starts.
     */
    public void reset() {
        LOGGER.info("Resetting debugger state");
        
        // Reset shutdown state
        isShutdown = false;
        
        // Reset counters and collections
        breakpointNextId = 0;
        this.breakpoints.breakpoints.clear();
        stopConsumers.clear();
        continueRunnable.clear();
        shutdownHandlers.clear();
        
        // Reset position information
        this.scopeManager.clear();
        
        LOGGER.info("Debugger state reset complete");
    }
}
