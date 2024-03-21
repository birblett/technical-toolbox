package com.birblett.util.config;

import net.minecraft.item.ItemStack;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

import java.util.function.Predicate;

public class ConfigUtil {

    public static Text setFailCustom(String substr1, String value, Formatting color, String substr2, String value2, Formatting color2) {
        return MutableText.of(TextContent.EMPTY).append(Text.of(substr1)).append(MutableText.of(new LiteralTextContent(value))
                .setStyle(Style.EMPTY.withColor(color))).append(Text.of(substr2)).append(MutableText.of(new
                LiteralTextContent(value2)).setStyle(Style.EMPTY.withColor(color2)));
    }

    private static Text setFailGeneric(String name, String value) {
        return MutableText.of(TextContent.EMPTY).append(Text.of("Failed to parse value '")).append(MutableText.of(new
                LiteralTextContent(value)).setStyle(Style.EMPTY.withColor(Formatting.RED))).append(Text.of("' for option " + name));
    }

    public static Pair<Integer, Text> getIntOption(String name, String value, int defaultValue, int left, int right) {
        int tmp;
        try {
            tmp = Integer.parseInt(value);
            if (tmp < left || tmp > right) {
                MutableText t = MutableText.of(new LiteralTextContent(name + " only accepts values "));
                if (left != Integer.MIN_VALUE && right != Integer.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(Text.of("in range [")).append(MutableText.of(new
                                    LiteralTextContent("" + left)).setStyle(Style.EMPTY.withColor(Formatting.RED)))
                                    .append(Text.of(", ")).append(MutableText.of(new LiteralTextContent("" + right))
                                    .setStyle(Style.EMPTY.withColor(Formatting.RED))).append(Text.of("]")));
                }
                else if (left != Integer.MIN_VALUE) {
                    return new Pair<>(defaultValue, t.append(Text.of(">= ")).append(MutableText.of(new
                            LiteralTextContent("" + left)).setStyle(Style.EMPTY.withColor(Formatting.RED))));
                }
                else if (right != Integer.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(Text.of("<= ")).append(MutableText.of(new
                            LiteralTextContent("" + right)).setStyle(Style.EMPTY.withColor(Formatting.RED))));
                }
            }
        }
        catch (Exception e) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
        return new Pair<>(tmp, null);
    }

    public static Pair<Boolean, Text> getBooleanOption(String name, String value, boolean defaultValue) {
        boolean tmp;
        try {
            tmp = Boolean.parseBoolean(value);
        }
        catch (Exception e) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
        return new Pair<>(tmp, null);
    }

    public static Pair<String, Text> getStringOption(String name, String value, String defaultValue) {
        if (value == null || value.length() == 0) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
        return new Pair<>(value, null);
    }

    public static Predicate<ItemStack> crafterDisabled() {
        //noinspection unchecked
        return (Predicate<ItemStack>) ConfigOptions.CRAFTER_DISABLED_SLOT_ITEMS.value();
    }

}
