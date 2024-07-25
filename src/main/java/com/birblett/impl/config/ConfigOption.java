package com.birblett.impl.config;

import com.birblett.impl.command.CameraCommand;
import com.birblett.util.ServerUtil;
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

    public static final ConfigOption<Integer> CONFIG_VIEW_PERMISSION_LEVEL = intConfig(
            "configViewPermissionLevel", 0,
            "Permission level required to view configurations.",
            0, 4,
            "0", "4");
    public static final ConfigOption<Boolean> CONFIG_WRITE_ON_CHANGE = boolConfig(
            "configWriteOnChange", false,
            "If enabled, changing configurations will also write to storage.",
            "false", "true");
    public static final ConfigOption<Boolean> CONFIG_WRITE_ONLY_CHANGES = boolConfig(
            "configWriteOnlyChanges", true,
            "If enabled, only changed configurations will be written to storage.",
            true, "false", "true");
    public static final ConfigOption<Integer> ALIAS_DEFAULT_PERMISSION = intConfig(
            "aliasDefaultPermission", 0,
            "Default permission level required to execute aliases.",
            0, 4,
            "0", "4");
    public static final ConfigOption<String> ALIAS_DEFAULT_SEPARATOR = new ConfigOption<>(
            "aliasDefaultSeparator", ",",
            "Default argument separator for new aliases.",
            true, ",", "\" \"") {
        @Override
        public Text setFromString(String value) {
            if (!value.isEmpty()) {
                this.value = value;
                return null;
            };
            return TextUtils.formattable("Option aliasDefaultSeparator requires string of length > 0");
        }
    };
    public static final ConfigOption<Boolean> ALIAS_DEFAULT_SILENT = boolConfig(
            "aliasDefaultSilent", false,
            "Whether aliases default to sending feedback or not.",
            "true", "false");
    public static final ConfigOption<String> CAMERA_COMMAND = new ConfigOption<>(
            "cameraCommand", "cam",
            "Camera command string, usage /[cmd string].",
            "cam", "c", "cs") {
        @Override
        public Text setFromString(String value, MinecraftServer server) {
            String oldValue = this.value;
            Text s = this.setFromString(value);
            if (s == null && server != null) {
                ServerUtil.removeCommandByName(server, oldValue);
                CameraCommand.register(server.getCommandManager().getDispatcher());
            }
            return s;
        }

        @Override
        public Text setFromString(String value) {
            Pair<String, Text> out = getStringOption(this.getName(), value, "cam");
            this.value = out.getLeft();
            return out.getRight();
        }
    };
    public static final ConfigOption<Boolean> CAMERA_GENERATES_CHUNKS = boolConfig(
            "cameraGeneratesChunks", false,
            "Whether players in camera mode should generate chunks or not",
            "true", "false");
    public static final ConfigOption<Boolean> CAMERA_CAN_SPECTATE = boolConfig(
            "cameraCanSpectate", false,
            "Whether players in camera mode can spectate other players",
            "true", "false");
    public static final ConfigOption<Boolean> CAMERA_CAN_TELEPORT = boolConfig(
            "cameraCanTeleport", false,
            "Whether players in camera mode can teleport to other players",
            "true", "false");
    public static final ConfigOption<String> CAMERA_CONSOLE_LOGGING = new ConfigOption<>(
            "cameraConsoleLogging", "none",
            "The level of logging for camera mode usage by players. Accepts values \"none\", \"command\", " +
                    "\"spectate\".",
            "none", "command",
            "spectate") {
        @Override
        public Text setFromString(String value) {
            Pair<String, Text> out = getRestrictedStringOptions(this.getName(), value, "none",
                    this.commandSuggestions());
            this.value = out.getLeft();
            return out.getRight();
        }
    };
    public static final ConfigOption<Integer> CAMERA_PERMISSION_LEVEL = intConfig(
            "cameraPermissionLevel", 4,
            "Permission level required to use the camera command.",
            0, 4, true,
            "0", "4");
    public static final ConfigOption<Integer> FEATURE_COPPER_BULB_DELAY = intConfig(
            "featureCopperBulbDelay", 0,
            "Gameticks of copper bulb delay when powered",
            0, Integer.MAX_VALUE,
            "0", "1");
    public static final ConfigOption<Boolean> FEATURE_COPPER_BULB_NO_POWERED_UPDATES = boolConfig(
            "featureCopperBulbNoPoweredUpdates", false,
            "Prevents power/depower (specifically the powered state) of copper bulbs from sending block updates.",
            "true", "false");
    public static final ConfigOption<Integer> FEATURE_CRAFTER_COOLDOWN = intConfig(
            "featureCrafterCooldown", 4,
            "Gameticks of crafter cooldown, will be instant if set to 0.",
            0, Integer.MAX_VALUE,
            "0", "4");
    public static final ConfigOption<Boolean> FEATURE_CRAFTER_QUASI_POWER = boolConfig(
            "featureCrafterQuasiPower", false,
            "Whether crafters can be quasi-powered or not.", true,
            "true", "false");
    public static final ConfigOption<Float> FEATURE_SPEED_LIMIT = floatConfig(
            "featureSpeedLimit", 100.0f,
            "The velocity threshold after which the server corrects player velocity.",
            0.0f, Float.MAX_VALUE,
            "100.0", String.valueOf(Integer.MAX_VALUE));
    public static final ConfigOption<Float> FEATURE_SPEED_LIMIT_ELYTRA = floatConfig(
            "featureSpeedLimitElytra", 300.0f,
            "The elytra velocity threshold after which the server corrects player velocity.",
            0.0f, Float.MAX_VALUE,
            "300.0", String.valueOf(Integer.MAX_VALUE));
    public static final ConfigOption<Double> FEATURE_SPEED_LIMIT_VEHICLE = doubleConfig(
            "featureSpeedLimitVehicle", 100.0,
            "The riding velocity threshold after which the server corrects player velocity.",
            0.0, Double.MAX_VALUE, true,
            "100.0", String.valueOf(Integer.MAX_VALUE));
    public static final ConfigOption<Boolean> LEGACY_BAD_OMEN = boolConfig(
            "legacyBadOmen", false,
            "Whether pre-1.21 bad omen/raid mechanics should be used.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_END_CRYSTAL_COLLISION = boolConfig(
            "legacyEndCrystalCollision", false,
            "End crystals won't check for collision in their tick method.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_END_CRYSTAL_FIRE_DAMAGE = boolConfig(
            "legacyEndCrystalFireDamage", false,
            "Allow end crystals to take fire damage.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_END_PLATFORM = boolConfig(
            "legacyEndPlatform", false,
            "Re-enables pre-1.21 end portal logic.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_POI_PROPERTY_CHECK = boolConfig(
            "legacyDisablePoiPropertyCheck", false,
            "Disables portal POI HORIZONTAL_AXIS property check.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_PROTECTION_COMPATIBILITY = boolConfig(
            "legacyProtectionCompatibility", false,
            "Makes all protection types compatible with each other.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_TRAPDOOR_UPDATE_SKIPPING = boolConfig(
            "legacyTrapdoorUpdateSkipping", false,
            "Whether update skipping should be allowed.",
            "true", "false");

    private final String name;
    private final String desc;
    protected T value;
    private final String defaultValue;
    private final Collection<String> commandSuggestions;
    private final boolean hasLineBreak;

    ConfigOption(String name, T defaultValue, String desc, boolean hasLineBreak, String... suggestions) {
        this.hasLineBreak = hasLineBreak;
        this.name = name;
        this.desc = desc;
        this.value = defaultValue;
        this.defaultValue = defaultValue.toString();
        this.commandSuggestions = Arrays.asList(suggestions);
        OPTIONS.add(this);
    }

    ConfigOption(String name, T defaultValue, String desc, String... suggestions) {
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

    public static ConfigOption<Boolean> boolConfig(String name, boolean defaultValue, String desc, boolean hasLineBreak, String... suggestions) {
        return new ConfigOption<>(name, defaultValue, desc, hasLineBreak, suggestions) {
            @Override
            public Text setFromString(String value) {
                Pair<Boolean, Text> out = getBooleanOption(this.getName(), value, false);
                this.value = out.getLeft();
                return out.getRight();
            }
        };
    }

    public static ConfigOption<Boolean> boolConfig(String name, boolean defaultValue, String desc, String... suggestions) {
        return boolConfig(name, defaultValue, desc, false, suggestions);
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
