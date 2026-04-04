package net.gunivers.sniffer.dap;

import com.mojang.brigadier.StringReader;
import kotlin.Pair;
import net.gunivers.sniffer.debugcmd.DebugData;
import net.gunivers.sniffer.debugcmd.ExprArgumentType;
import net.gunivers.sniffer.util.Result;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages variable conversion and representation for the debugger.
 * This class provides utility methods to convert Minecraft objects into debugger variables
 * that can be displayed in the debugging client.
 *
 * @author theogiraudet
 */
public abstract class VariableManager {

    /**
     * Converts a command source into a map of debugger variables.
     * This method extracts relevant information from the command source such as
     * the executor entity, position, and rotation.
     *
     * @param source The command source to convert
     * @param startIndex The starting index for variable IDs
     * @return A map of variable IDs to debugger variables
     */
    public static Map<Integer, DebuggerVariable> convertCommandSource(ExecutionCommandSource<?> source, int startIndex) {
        if(source instanceof CommandSourceStack commandSource) {
            //if executor is an entity
            var executorVariable = convertEntityVariables(commandSource.getEntity(), startIndex, true);
            var currentIndex = executorVariable.getSecond();

            var locId = currentIndex++;

            var posVariable = convertPos(commandSource.getPosition(), currentIndex, false);
            currentIndex = posVariable.getSecond();

            var rotVariable = convertRotation(commandSource.getRotation(), currentIndex, false);
            currentIndex = rotVariable.getSecond();

            var worldVariable = convertWorld(commandSource.getLevel(), currentIndex, false);

            var locationVariable = new DebuggerVariable(locId, "location", posVariable.getFirst().value(), List.of(posVariable.getFirst(), rotVariable.getFirst(), worldVariable), true);

            var result = new ArrayList<DebuggerVariable>(currentIndex);
            result.add(executorVariable.getFirst());
            result.add(locationVariable);

            return flattenToMap(result);
        } else {
            throw new IllegalStateException("AbstractServerCommandSource is not a ServerCommandSource but a " + source.getClass().getSimpleName());
        }
    }

    /**
     * Converts an entity into a debugger variable with its properties.
     * This method extracts entity information such as type, name, UUID, position, rotation, and dimension.
     *
     * @param entity The entity to convert, can be null (representing the server)
     * @param startIndex The starting index for variable IDs
     * @param isRoot Whether this variable is a root-level variable
     * @return A pair containing the created variable and the next available ID
     */
    @SuppressWarnings("SameParameterValue")
    private static Pair<DebuggerVariable, Integer> convertEntityVariables(@Nullable Entity entity, int startIndex, boolean isRoot) {
        if(entity == null) {
            return new Pair<>(
                    new DebuggerVariable(startIndex, "executor", "server", List.of(), isRoot),
                    ++startIndex
            );
        }

        var id = startIndex + 1;

        var objectType = new DebuggerVariable(id++, "type", typeToString(entity.getType()), List.of(), false);
        var objectName = new DebuggerVariable(id++, "name", entity.getName().getString(), List.of(), false);

        var objectUuid = new DebuggerVariable(id++, "uuid", entity.getStringUUID(), List.of(), false);

        var pos = convertPos(entity.position(), id, false);
        id = pos.getSecond();

        var rot = convertRotation(entity.getRotationVector(), id, false);
        id = rot.getSecond();

        var objectDimension = convertWorld(entity.level(), id++, false);

        var displayName = objectName.value() != null ? objectName.value() : objectType.value();

        var children = List.of(objectType, objectName, objectUuid, pos.getFirst(), rot.getFirst(), objectDimension);
        return new Pair<>(new DebuggerVariable(startIndex, "executor", displayName, children, isRoot), id);
    }

    /**
     * Converts an entity type to a readable string representation.
     *
     * @param type The entity type to convert
     * @return A string representation of the entity type
     */
    private static String typeToString(EntityType<?> type) {
        var str = type.toString(); // Formatted as translationKey
        return str.substring(str.lastIndexOf(".") + 1);
    }

    /**
     * Converts a 3D position vector into a debugger variable.
     * This method creates a variable representing the position with x, y, and z components.
     *
     * @param vec The position vector to convert
     * @param id The starting ID for the variable
     * @param isRoot Whether this variable is a root-level variable
     * @return A pair containing the created variable and the next available ID
     */
    @SuppressWarnings("SameParameterValue")
    private static Pair<DebuggerVariable, Integer> convertPos(Vec3 vec, int id, boolean isRoot) {
        var posId = id++;
        var objectPosX = new DebuggerVariable(id++, "x", Double.toString(vec.x), List.of(), false);
        var objectPosY = new DebuggerVariable(id++, "y", Double.toString(vec.y), List.of(), false);
        var objectPosZ = new DebuggerVariable(id++, "z", Double.toString(vec.z), List.of(), false);
        var posStr = MessageFormat.format("[{0}, {1}, {2}]", vec.x, vec.y, vec.z);
        var objectPosition = new DebuggerVariable(posId, "position", posStr, List.of(objectPosX, objectPosY, objectPosZ), isRoot);
        return new Pair<>(objectPosition, id);
    }

    /**
     * Converts a 2D rotation vector into a debugger variable.
     * This method creates a variable representing the rotation with yaw and pitch components.
     *
     * @param vec The rotation vector to convert
     * @param id The starting ID for the variable
     * @param isRoot Whether this variable is a root-level variable
     * @return A pair containing the created variable and the next available ID
     */
    @SuppressWarnings("SameParameterValue")
    private static Pair<DebuggerVariable, Integer> convertRotation(Vec2 vec, int id, boolean isRoot) {
        var posId = id++;
        var objectPosX = new DebuggerVariable(id++, "yaw", Double.toString(vec.x), List.of(), false);
        var objectPosY = new DebuggerVariable(id++, "pitch", Double.toString(vec.y), List.of(), false);
        var posStr = MessageFormat.format("[{0}, {1}]", vec.x, vec.y);
        var objectPosition = new DebuggerVariable(posId, "rotation", posStr, List.of(objectPosX, objectPosY), isRoot);
        return new Pair<>(objectPosition, id);
    }

    /**
     * Converts a Minecraft world into a debugger variable.
     * This method creates a variable representing the world/dimension.
     *
     * @param world The world to convert
     * @param id The ID for the variable
     * @param isRoot Whether this variable is a root-level variable
     * @return The created debugger variable
     */
    @SuppressWarnings("SameParameterValue")
    private static DebuggerVariable convertWorld(Level world, int id, boolean isRoot) {
        return new DebuggerVariable(id, "world", world.dimensionType().effectsLocation().getPath(), List.of(), isRoot);
    }

    /**
     * Flattens a list of debugger variables into a map indexed by variable ID.
     * This method processes the hierarchical structure of variables and their children
     * into a flat map for easier lookup.
     *
     * @param variables The list of variables to flatten
     * @return A map of variable IDs to debugger variables
     */
    private static Map<Integer, DebuggerVariable> flattenToMap(List<DebuggerVariable> variables) {
        var map = new HashMap<Integer, DebuggerVariable>();
        variables.forEach(variable -> flattenToMap(variable, map));
        return map;
    }

    /**
     * Helper method to recursively flatten a variable and its children into a map.
     * This method adds the variable and all its children to the provided map.
     *
     * @param variable The variable to flatten
     * @param variables The map to add the flattened variables to
     */
    private static void flattenToMap(DebuggerVariable variable, Map<Integer, DebuggerVariable> variables) {
        variables.put(variable.id(), variable);
        variable.children().forEach(child -> flattenToMap(child, variables));
    }

    /**
     * Converts an NBT compound into a map of debugger variables.
     * This method uses a visitor pattern to traverse the NBT structure and create
     * corresponding debugger variables for each element.
     *
     * @param name The name of the root variable
     * @param compound The NBT compound to convert, may be null
     * @param startIndex The starting index for variable IDs
     * @param isRoot Whether this variable is a root-level variable
     * @return A map of variable IDs to debugger variables, or an empty map if compound is null
     */
    public static Map<Integer, DebuggerVariable> convertNbtCompound(String name, @Nullable CompoundTag compound, int startIndex, boolean isRoot) {
        if(compound != null) {
            var visitor = new NbtElementVariableVisitor(startIndex, name, isRoot);
            compound.accept(visitor);
            return visitor.get();
        }
        return Map.of();
    }

    public static Result<DebugData> evaluate(String expression){
        expression = expression.trim();
        try {
            return Result.success(new ExprArgumentType().parseArgumentWithoutBrackets(new StringReader(expression)));
        }catch (Exception ex){
            return Result.failure("Expression is invalid: " + ex.getMessage());
        }
    }

}
