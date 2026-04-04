package net.gunivers.sniffer.command;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Manages the loading and storage of function text content for debugging purposes.
 * This class maintains a mapping between function identifiers and their source code lines.
 *
 * @author XiLaiTL
 *
 */
public class FunctionTextLoader {
    /** Map storing function identifiers and their corresponding source code lines */
    private static final Map<ResourceLocation, List<String>> FUNCTION_TEXT = new HashMap<>();

    /**
     * Returns an iterable collection of all registered function identifiers.
     * @return An iterable containing all function identifiers
     */
    public static Iterable<ResourceLocation> functionIds(){
        return FUNCTION_TEXT.keySet();
    }

    /**
     * Stores the source code lines for a given function identifier.
     * @param id The identifier of the function
     * @param lines The list of source code lines for the function
     */
    public static void put(ResourceLocation id,List<String> lines){
        FUNCTION_TEXT.put(id,new ArrayList<>(lines));
    }

    /**
     * Retrieves the source code lines for a given function identifier.
     * @param id The identifier of the function
     * @return The list of source code lines, or null if the function is not found
     */
    public static List<String> get(ResourceLocation id){
        return FUNCTION_TEXT.getOrDefault(id, List.of());
    }
}
