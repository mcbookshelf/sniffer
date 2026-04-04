package net.gunivers.sniffer.dap;

import net.minecraft.nbt.*;

import java.util.*;

/**
 * A visitor implementation for NBT elements that converts them into debugger variables.
 * This class traverses NBT data structures and creates corresponding DebuggerVariable objects
 * that can be displayed in a debugging client.
 *
 * @author theogiraudet
 */
public class NbtElementVariableVisitor implements TagVisitor {

    private int index;
    private final Map<Integer, DebuggerVariable> variables = new HashMap<>();
    private DebuggerVariable returnVariable;
    private String currentName;
    private boolean isRoot;

    /**
     * Creates a new NBT element visitor.
     *
     * @param index The starting index for variable IDs
     * @param rootName The name of the root variable
     * @param isRoot Whether this variable is a root-level variable
     */
    public NbtElementVariableVisitor(int index, String rootName, boolean isRoot) {
        this.index = index;
        this.currentName = rootName;
        this.isRoot = isRoot;
    }

    /**
     * Gets the map of created variables.
     *
     * @return A map of variable IDs to debugger variables
     */
    public Map<Integer, DebuggerVariable> get() {
        return variables;
    }

    /**
     * Visits a string NBT element and converts it to a debugger variable.
     *
     * @param element The string NBT element
     */
    @Override
    public void visitString(StringTag element) {
        convertPrimitive(element);
    }

    /**
     * Visits a byte NBT element and converts it to a debugger variable.
     *
     * @param element The byte NBT element
     */
    @Override
    public void visitByte(ByteTag element) {
        convertPrimitive(element);
    }

    /**
     * Visits a short NBT element and converts it to a debugger variable.
     *
     * @param element The short NBT element
     */
    @Override
    public void visitShort(ShortTag element) {
        convertPrimitive(element);
    }

    /**
     * Visits an int NBT element and converts it to a debugger variable.
     *
     * @param element The int NBT element
     */
    @Override
    public void visitInt(IntTag element) {
        convertPrimitive(element);
    }

    /**
     * Visits a long NBT element and converts it to a debugger variable.
     *
     * @param element The long NBT element
     */
    @Override
    public void visitLong(LongTag element) {
        convertPrimitive(element);
    }

    /**
     * Visits a float NBT element and converts it to a debugger variable.
     *
     * @param element The float NBT element
     */
    @Override
    public void visitFloat(FloatTag element) {
        convertPrimitive(element);
    }

    /**
     * Visits a double NBT element and converts it to a debugger variable.
     *
     * @param element The double NBT element
     */
    @Override
    public void visitDouble(DoubleTag element) {
        convertPrimitive(element);
    }

    /**
     * Visits a byte array NBT element and converts it to a debugger variable.
     *
     * @param element The byte array NBT element
     */
    @Override
    public void visitByteArray(ByteArrayTag element) {
        convertList(element);
    }

    /**
     * Visits an int array NBT element and converts it to a debugger variable.
     *
     * @param element The int array NBT element
     */
    @Override
    public void visitIntArray(IntArrayTag element) {
        convertList(element);
    }

    /**
     * Visits a long array NBT element and converts it to a debugger variable.
     *
     * @param element The long array NBT element
     */
    @Override
    public void visitLongArray(LongArrayTag element) {
        convertList(element);
    }

    /**
     * Visits a list NBT element and converts it to a debugger variable.
     *
     * @param element The list NBT element
     */
    @Override
    public void visitList(ListTag element) {
        convertList(element);
    }

    /**
     * Visits a compound NBT element and converts it to a debugger variable.
     * This method recursively processes all child elements of the compound.
     *
     * @param compound The compound NBT element
     */
    @Override
    public void visitCompound(CompoundTag compound) {
        var children = new LinkedList<DebuggerVariable>();
        var compoundIndex = this.index++;
        var compoundVar = new DebuggerVariable(compoundIndex, this.currentName, compound.toString(), children, isRoot);
        isRoot = false;
        variables.put(compoundIndex, compoundVar);
        for(var key: compound.keySet()) {
            this.currentName = key;
            Objects.requireNonNull(compound.get(key)).accept(this);
            children.add(returnVariable);
        }
        returnVariable = compoundVar;
    }

    /**
     * Visits an end NBT element. Does nothing as end tags have no value.
     *
     * @param element The end NBT element
     */
    @Override
    public void visitEnd(EndTag element) {}

    /**
     * Converts an NBT list or array into debugger variables.
     * This method processes each element in the list and creates child variables.
     *
     * @param list The NBT list or array to convert
     */
    private void convertList(CollectionTag list) {
        var arrayIndex = index++;
        var array = new LinkedList<DebuggerVariable>();
        var name = currentName;
        var result = new DebuggerVariable(arrayIndex, name, list.toString(), array, false);
        variables.put(arrayIndex, result);
        for(int i = 0; i < list.size(); i++) {
            currentName = Integer.toString(index);
            list.get(i).accept(this);
            array.add(returnVariable);
        }
        index++;
        returnVariable = result;
    }

    /**
     * Converts a primitive NBT element into a debugger variable.
     * Used for simple types like numbers and strings.
     *
     * @param element The primitive NBT element to convert
     */
    private void convertPrimitive(Tag element) {
        var i = index++;
        returnVariable = new DebuggerVariable(i, currentName, element.toString(), List.of(), isRoot);
        variables.put(i, returnVariable);
    }
}
