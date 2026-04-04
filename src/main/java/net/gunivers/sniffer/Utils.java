package net.gunivers.sniffer;


import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Utility class providing helper methods for the Datapack Debugger.
 * Contains various utility functions to work with Minecraft's internal classes
 * and handle encapsulation breaking where necessary.
 */
public class Utils {

    private static final String MESSAGE_PREFIX = "[Sniffer] ";

    public static Component addSnifferPrefix(Component text) {
        var header = Component.literal(MESSAGE_PREFIX).withStyle(ChatFormatting.AQUA);
        return header.append(text);
    }

    public static Component addSnifferPrefix(String text) {
        return addSnifferPrefix(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

}
