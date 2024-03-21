package com.birblett.util.config;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Pair;

import java.util.function.Predicate;

public class ConfigHelper {

    public static Pair<Integer, String> getIntOption(String name, String value, int defaultValue) {
        int tmp;
        try {
            tmp = Integer.parseInt(value);
            if (tmp < 0) {
                return new Pair<>(defaultValue, name + " only accepts values >= 0");
            }
        }
        catch (Exception e) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
        return new Pair<>(tmp, setSuccess(name, value));
    }

    public static Pair<Boolean, String> getBooleanOption(String name, String value, boolean defaultValue) {
        boolean tmp;
        try {
            tmp = Boolean.parseBoolean(value);
        }
        catch (Exception e) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
        return new Pair<>(tmp, setSuccess(name, value));
    }

    private static String setFailGeneric(String name, String value) {
        return "Failed to parse value '" + value + "' for option " + name;
    }

    private static String setSuccess(String name, String value) {
        return null;
    }

    public static Predicate<ItemStack> crafterDisabled() {
        //noinspection unchecked
        return (Predicate<ItemStack>) ConfigOptions.CRAFTER_DISABLED_SLOT_ITEMS.value();
    }

}
