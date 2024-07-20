package com.birblett.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;

import java.util.Collection;

/**
 * Various configuration related utilities and shorthands
 */
public class ConfigUtil {

    private static Text setFailGeneric(String name, String value) {
        return TextUtils.formattable("Failed to parse value " + value + " for option " + name);
    }

    public static Pair<Integer, Text> getIntOption(String name, String value, int defaultValue, int left, int right) {
        int tmp;
        try {
            tmp = Integer.parseInt(value);
            if (tmp < left || tmp > right) {
                MutableText t = TextUtils.formattable("Invalid input " + value + ": " + name + " only accepts values ");
                if (left != Integer.MIN_VALUE && right != Integer.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("in range [" + left + ", " + right
                            + "]")));
                }
                else if (left != Integer.MIN_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable(">= " + left)));
                }
                else if (right != Integer.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("<= " + right)));
                }
            }
        }
        catch (Exception e) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
        return new Pair<>(tmp, null);
    }

    public static Pair<Float, Text> getFloatOptions(String name, String value, float defaultValue, float left, float right) {
        float tmp;
        try {
            tmp = Float.parseFloat(value);
            if (tmp < left || tmp > right) {
                MutableText t = TextUtils.formattable("Invalid input " + value + ": " + name + " only accepts values ");
                if (left != Float.MIN_VALUE && right != Float.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("in range [" + left + ", " + right
                            + "]")));
                }
                else if (left != Float.MIN_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable(">= " + left)));
                }
                else if (right != Float.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("<= " + right)));
                }
            }
        }
        catch (Exception e) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
        return new Pair<>(tmp, null);
    }

    public static Pair<Double, Text> getDoubleOption(String name, String value, double defaultValue, double left, double right) {
        double tmp;
        try {
            tmp = Double.parseDouble(value);
            if (tmp < left || tmp > right) {
                MutableText t = TextUtils.formattable("Invalid input " + value + ": " + name + " only accepts values ");
                if (left != Double.MIN_VALUE && right != Double.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("in range [" + left + ", " + right
                            + "]")));
                }
                else if (left != Double.MIN_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable(">= " + left)));
                }
                else if (right != Double.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("<= " + right)));
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
        if (!(value.equals("false") || value.equals("true"))) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
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
            MutableText out = TextUtils.formattable("Invalid input \"" + value + "\" for option " + name + ": must " +
                    "be one of " + "[");
            int i = 0;
            for (String suggestion : suggestions) {
                out = out.append(TextUtils.formattable(suggestion));
                if (++i != suggestions.size()) {
                    out = out.append(TextUtils.formattable(","));
                }
            }
            return new Pair<>(defaultValue, out.append("]"));
        }
        return new Pair<>(value, null);
    }

}
