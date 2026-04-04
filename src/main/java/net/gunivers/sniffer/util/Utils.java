package net.gunivers.sniffer.util;

import com.google.common.base.Suppliers;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Utility class providing helper methods for the Datapack Debugger.
 * Contains various utility functions to work with Minecraft's internal classes
 * and handle encapsulation breaking where necessary.
 */
public class Utils {

    final static Logger LOGGER = LogUtils.getLogger();

    /**
     * Retrieves the identifier from an ExpandedMacro function.
     * This method uses EncapsulationBreaker to access the private field
     * that contains the function's identifier.
     *
     * @param function The ExpandedMacro function to get the ID from
     * @return The ResourceLocation of the function, or a fallback identifier if not found
     */
    public static ResourceLocation getId(PlainTextFunction<?> function) {
        return ReflectUtil.getT(function, "functionIdentifier", ResourceLocation.class).onFailure(LOGGER::error).getDataOrElse(ResourceLocation.parse("foo:bar"));
    }

    private static final String MESSAGE_PREFIX = "[Sniffer] ";

    public static Component addSnifferPrefix(Component text) {
        var header = Component.literal(MESSAGE_PREFIX).withStyle(ChatFormatting.AQUA);
        return header.append(text);
    }

    public static Component addSnifferPrefix(String text) {
        return addSnifferPrefix(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    public static <T> java.util.function.Supplier<T> lazy(com.google.common.base.Supplier<T> supplier) {
        return Suppliers.memoize(supplier);
    }

    public static <T> java.util.function.Supplier<T> lazy(T value) {
        return Suppliers.memoize(() -> value);
    }

}
