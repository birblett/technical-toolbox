package com.birblett.impl.config;

import com.birblett.util.TextUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Configuration option class for handling common primitives.
 */
public class ConfigOption<T> {

    public static final List<ConfigOption<?>> OPTIONS = new ArrayList<>();
    private final String name;
    private final String desc;
    public T value;
    private final String defaultValue;
    private final Collection<String> commandSuggestions;
    private final boolean hasLineBreak;

    public ConfigOption(String name, T defaultValue, String desc, boolean hasLineBreak, String... suggestions) {
        this.hasLineBreak = hasLineBreak;
        this.name = name;
        this.desc = desc;
        this.value = defaultValue;
        this.defaultValue = defaultValue.toString();
        this.commandSuggestions = Arrays.asList(suggestions);
        OPTIONS.add(this);
    }

    public ConfigOption(String name, T defaultValue, String desc, String... suggestions) {
        this(name, defaultValue, desc, false, suggestions);
    }

    public String getName() {
        return this.name;
    }

    public String getDefaultValue() {
        return this.defaultValue;
    }

    public Text getText() {
        if (this.getWriteable().equals(this.defaultValue)) {
            return TextUtils.formattable(this.desc + "\nCurrent value (default): ").append(TextUtils.formattable(this
                            .getWriteable()).setStyle(Style.EMPTY.withColor(Formatting.GREEN))).append(TextUtils.formattable(")"));
        }
        return TextUtils.formattable(this.desc + "\nCurrent value: ").append(TextUtils.formattable(this.getWriteable())
                .setStyle(Style.EMPTY.withColor(Formatting.GREEN))).append(TextUtils.formattable(" (default: "))
                .append(TextUtils.formattable(this.defaultValue).setStyle(Style.EMPTY.withColor(Formatting.YELLOW)))
                .append(TextUtils.formattable(")"));
    }

    public Collection<String> commandSuggestions() {
        return this.commandSuggestions;
    }

    public boolean hasLineBreak() {
        return this.hasLineBreak;
    };

    public Text setFromString(String value, MinecraftServer manager) {
        return this.setFromString(value);
    };

    public T val() {
        return this.value;
    }

    public Text setFromString(String value) {
        return null;
    };

    public String getWriteable() {
        return String.valueOf(this.val());
    };

    public static ConfigOption<Boolean> boolConfig(String name, boolean defaultValue, String desc, boolean hasLineBreak) {
        return new ConfigOption<>(name, defaultValue, desc, hasLineBreak, "true", "false") {
            @Override
            public Text setFromString(String value) {
                Pair<Boolean, Text> out = getBooleanOption(this.getName(), value, false);
                this.value = out.getLeft();
                return out.getRight();
            }
        };
    }

    public static ConfigOption<Boolean> boolConfig(String name, boolean defaultValue, String desc) {
        return boolConfig(name, defaultValue, desc, false);
    }

    public static ConfigOption<Integer> intConfig(String name, int defaultValue, String desc, int min, int max, boolean hasLineBreak, String... suggestions) {
        return new ConfigOption<>(name, defaultValue, desc, hasLineBreak, suggestions) {
            @Override
            public Text setFromString(String value) {
                Pair<Integer, Text> out = getIntOption(this.getName(), value, defaultValue, min, max);
                this.value = out.getLeft();
                return out.getRight();
            }
        };
    }

    public static ConfigOption<Integer> intConfig(String name, int defaultValue, String desc, int min, int max, String... suggestions) {
        return intConfig(name, defaultValue, desc, min, max, false, suggestions);
    }

    public static ConfigOption<Long> longConfig(String name, long defaultValue, String desc, long min, long max, boolean hasLineBreak, String... suggestions) {
        return new ConfigOption<>(name, defaultValue, desc, hasLineBreak, suggestions) {
            @Override
            public Text setFromString(String value) {
                Pair<Long, Text> out = getLongOption(this.getName(), value, defaultValue, min, max);
                this.value = out.getLeft();
                return out.getRight();
            }
        };
    }

    public static ConfigOption<Long> longConfig(String name, long defaultValue, String desc, long min, long max, String... suggestions) {
        return longConfig(name, defaultValue, desc, min, max, false, suggestions);
    }

    public static ConfigOption<Float> floatConfig(String name, float defaultValue, String desc, float min, float max, boolean hasLineBreak, String... suggestions) {
        return new ConfigOption<>(name, defaultValue, desc, hasLineBreak, suggestions) {
            @Override
            public Text setFromString(String value) {
                Pair<Float, Text> out = getFloatOption(this.getName(), value, defaultValue, min, max);
                this.value = out.getLeft();
                return out.getRight();
            }
        };
    }

    public static ConfigOption<Float> floatConfig(String name, float defaultValue, String desc, float min, float max, String... suggestions) {
        return floatConfig(name, defaultValue, desc, min, max, false, suggestions);
    }

    public static ConfigOption<Double> doubleConfig(String name, double defaultValue, String desc, double min, double max, boolean hasLineBreak, String... suggestions) {
        return new ConfigOption<>(name, defaultValue, desc, hasLineBreak, suggestions) {
            @Override
            public Text setFromString(String value) {
                Pair<Double, Text> out = getDoubleOption(this.getName(), value, defaultValue, min, max);
                this.value = out.getLeft();
                return out.getRight();
            }
        };
    }

    public static ConfigOption<Double> doubleConfig(String name, double defaultValue, String desc, double min, double max, String... suggestions) {
        return doubleConfig(name, defaultValue, desc, min, max, false, suggestions);
    }

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

    public static Pair<Long, Text> getLongOption(String name, String value, long defaultValue, long left, long right) {
        long tmp;
        try {
            tmp = Long.parseLong(value);
            if (tmp < left || tmp > right) {
                MutableText t = TextUtils.formattable("Invalid input " + value + ": " + name + " only accepts values ");
                if (left != Long.MIN_VALUE && right != Long.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("in range [" + left + ", " + right
                            + "]")));
                }
                else if (left != Long.MIN_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable(">= " + left)));
                }
                else if (right != Long.MAX_VALUE) {
                    return new Pair<>(defaultValue, t.append(TextUtils.formattable("<= " + right)));
                }
            }
        }
        catch (Exception e) {
            return new Pair<>(defaultValue, setFailGeneric(name, value));
        }
        return new Pair<>(tmp, null);
    }

    public static Pair<Float, Text> getFloatOption(String name, String value, float defaultValue, float left, float right) {
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
