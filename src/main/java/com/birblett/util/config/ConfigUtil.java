package com.birblett.util.config;

import com.birblett.TechnicalToolbox;
import com.birblett.util.TextUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Various configuration related utilities and shorthands
 */
public class ConfigUtil {

    public static Text setFailCustom(String substr1, String value, Formatting color, String substr2, String value2, Formatting color2) {
        return TextUtils.formattable(substr1).append(TextUtils.formattable(value).setStyle(Style.EMPTY.withColor(color)))
                .append(TextUtils.formattable(substr2)).append(TextUtils.formattable(value2)).setStyle(Style.EMPTY
                        .withColor(color2));
    }

    private static Text setFailGeneric(String name, String value) {
        return TextUtils.formattable("Failed to parse value ").append(TextUtils.formattable(value).setStyle(Style.EMPTY
                .withColor(Formatting.RED))).append(TextUtils.formattable(" for option " + name));
    }

    public static Pair<Integer, Text> getIntOption(String name, String value, int defaultValue, int left, int right) {
        int tmp;
        try {
            tmp = Integer.parseInt(value);
            if (tmp < left || tmp > right) {
                MutableText t = TextUtils.formattable("Invalid input ").append(TextUtils.formattable(value)
                        .setStyle(Style.EMPTY.withColor(Formatting.RED))).append(TextUtils.formattable(": " + name +
                        " only accepts values "));
                if (left != Integer.MIN_VALUE && right != Integer.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("in range [")).append(TextUtils
                                    .formattable("" + left).setStyle(Style.EMPTY.withColor(Formatting.GREEN)))
                                    .append(TextUtils.formattable(", ")).append(TextUtils.formattable("" + right)
                                    .setStyle(Style.EMPTY.withColor(Formatting.GREEN))).append(TextUtils.formattable("]")));
                }
                else if (left != Integer.MIN_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable(">= ")).append(TextUtils
                            .formattable("" + left).setStyle(Style.EMPTY.withColor(Formatting.GREEN))));
                }
                else if (right != Integer.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("<= ")).append(Text.literal("" + right)
                            .setStyle(Style.EMPTY.withColor(Formatting.GREEN))));
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

    public static Pair<String, Text> getRestrictedStringOptions(String name, String value, String defaultValue, Collection<String> suggestions) {
        if (!suggestions.contains(value)) {
            MutableText out = TextUtils.formattable("Invalid input ").append(TextUtils.formattable(value)
                    .setStyle(Style.EMPTY.withColor(Formatting.RED))).append(TextUtils.formattable(": must be one of " +
                    "["));
            int i = 0;
            for (String suggestion : suggestions) {
                out = out.append(TextUtils.formattable(suggestion).setStyle(Style.EMPTY.withColor(Formatting.GREEN)));
                if (++i != suggestions.size()) {
                    out = out.append(TextUtils.formattable(","));
                }
            }
            return new Pair<>(defaultValue, out.append("]"));
        }
        return new Pair<>(value, null);
    }

    public static Predicate<ItemStack> crafterDisabled() {
        //noinspection unchecked
        return (Predicate<ItemStack>) ConfigOptions.CRAFTER_DISABLED_SLOT_ITEMS.value();
    }

}
