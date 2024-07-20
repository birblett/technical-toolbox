package com.birblett.impl.config;

import com.birblett.impl.command.CameraCommand;
import com.birblett.util.ConfigUtil;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

import java.util.Arrays;
import java.util.Collection;

/**
 * Enum containing all configurable options, honestly a mess but i'll deal with refactoring later if necessary
 */
public enum ConfigOptions implements ConfigOption<Object> {
    CONFIG_VIEW_PERMISSION_LEVEL("configViewPermissionLevel", "0", "Permission level required to" +
            " view configurations.", "0", "4") {
        private int value = 0;

        @Override
        public Integer value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Integer, Text> out = ConfigUtil.getIntOption(this.getName(), value, 4, 0, 4);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    CONFIG_WRITE_ON_CHANGE("configWriteOnChange", "false", "If enabled, changing configurations will" +
            " also write to storage.", "false", "true") {
        private boolean value = false;

        @Override
        public Boolean value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Boolean, Text> out = ConfigUtil.getBooleanOption(this.getName(), value, false);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    CONFIG_WRITE_ONLY_CHANGES("configWriteOnlyChanges", "true", "If enabled, only changed " +
            "configurations will be written to storage.", true, "false", "true") {
        private boolean value = true;

        @Override
        public Boolean value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Boolean, Text> out = ConfigUtil.getBooleanOption(this.getName(), value, false);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    ALIAS_DEFAULT_PERMISSION("aliasDefaultPermission", "0", "Default permission level required to" +
            " execute aliases.", "0", "4") {
        private int value = 0;

        @Override
        public Integer value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Integer, Text> out = ConfigUtil.getIntOption(this.getName(), value, 0, 0, 4);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    ALIAS_DEFAULT_SEPARATOR("aliasDefaultSeparator", ",", "Default argument separator for new " +
            "aliases.", true, ",", "\" \"") {
        private String value = ",";

        @Override
        public String value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            if (!value.isEmpty()) {
                this.value = value;
                return null;
            };
            return TextUtils.formattable("Option aliasDefaultSeparator requires string of length > 0");
        }
    },
    CAMERA_COMMAND("cameraCommand", "cam", "Camera command string, usage /[cmd string].",
            "cam", "c", "cs") {
        private String value = "cam";

        @Override
        public String value() {
            return this.value;
        }

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
            Pair<String, Text> out = ConfigUtil.getStringOption(this.getName(), value, "cam");
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    CAMERA_GENERATES_CHUNKS("cameraGeneratesChunks", "false", "Whether players in camera mode" +
            " should generate chunks or not", "true", "false") {
        private boolean value = false;

        @Override
        public Boolean value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Boolean, Text> out = ConfigUtil.getBooleanOption(this.getName(), value, false);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    CAMERA_CAN_SPECTATE("cameraCanSpectate", "false", "Whether players in camera mode can " +
            "spectate other players", "true", "false") {
        private boolean value = false;

        @Override
        public Boolean value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Boolean, Text> out = ConfigUtil.getBooleanOption(this.getName(), value, false);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    CAMERA_CAN_TELEPORT("cameraCanTeleport", "false", "Whether players in camera mode can " +
            "teleport to other players", "true", "false") {
        private boolean value = false;

        @Override
        public Boolean value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Boolean, Text> out = ConfigUtil.getBooleanOption(this.getName(), value, false);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    CAMERA_CONSOLE_LOGGING("cameraConsoleLogging", "none", "The level of logging for camera mode" +
            " usage by players. Accepts values \"none\", \"command\", \"spectate\".", "none", "command",
            "spectate") {
        private String value = "none";

        @Override
        public String value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<String, Text> out = ConfigUtil.getRestrictedStringOptions(this.getName(), value, "none",
                    this.commandSuggestions());
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    CAMERA_PERMISSION_LEVEL("cameraPermissionLevel", "4", "Permission level required to use the " +
            "camera command.", true, "0", "4") {
        private int value = 4;

        @Override
        public Integer value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Integer, Text> out = ConfigUtil.getIntOption(this.getName(), value, 4, 0, 4);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    FIX_SPEED_LIMIT("fixSpeedLimit", "100.0", "The velocity threshold after which the server " +
            "corrects player velocity.","100.0", String.valueOf(Integer.MAX_VALUE)) {
        private float value = 100;

        @Override
        public Float value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Float, Text> out = ConfigUtil.getFloatOptions(this.getName(), value, 100, 0, Float.MAX_VALUE);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    FIX_ELYTRA_SPEED_LIMIT("fixElytraSpeedLimit", "300.0", "The elytra velocity threshold after " +
            "which the server corrects player velocity.", "300.0", String.valueOf(Integer.MAX_VALUE)) {
        private float value = 300;

        @Override
        public Float value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Float, Text> out = ConfigUtil.getFloatOptions(this.getName(), value, 100, 0, Float.MAX_VALUE);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    FIX_VEHICLE_SPEED_LIMIT("fixVehicleSpeedLimit", "100.0", "The riding velocity threshold " +
            "after which the server corrects player velocity.","100.0", String.valueOf(Integer.MAX_VALUE)) {
        private double value = 100;

        @Override
        public Double value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Double, Text> out = ConfigUtil.getDoubleOption(this.getName(), value, 100, 0, Double.MAX_VALUE);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    MECHANIC_COPPER_BULB_DELAY("mechanicCopperBulbDelay", "0", "Gameticks of copper bulb delay " +
            "when powered", "0", "1") {
        private int value = 0;

        @Override
        public Integer value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Integer, Text> out = ConfigUtil.getIntOption(this.getName(), value, 4, 0, Integer.MAX_VALUE);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    MECHANIC_CRAFTER_COOLDOWN("mechanicCrafterCooldown", "4", "Gameticks of crafter cooldown, " +
            "will be instant if set to 0.", "0", "4") {
        private int value = 4;

        @Override
        public Integer value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Integer, Text> out = ConfigUtil.getIntOption(this.getName(), value, 4, 0, Integer.MAX_VALUE);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    MECHANIC_CRAFTER_QUASI_POWER("mechanicCrafterQuasiPower", "false", "Whether crafters can be " +
            "quasi-powered or not.", "true", "false") {
        private boolean value = false;

        @Override
        public Boolean value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Boolean, Text> out = ConfigUtil.getBooleanOption(this.getName(), value, false);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    MECHANIC_DISABLE_POI_PROPERTY_CHECK("mechanicDisablePoiPropertyCheck", "false", "Whether portal " +
            "POIs should perform a check for the HORIZONTAL_AXIS property.", "true", "false") {
        private boolean value = false;

        @Override
        public Boolean value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Boolean, Text> out = ConfigUtil.getBooleanOption(this.getName(), value, false);
            this.value = out.getLeft();
            return out.getRight();
        }
    },
    MECHANIC_UPDATE_SKIPPING("mechanicUpdateSkipping", "false", "Whether update skipping (for " +
            "1.20+ should be allowed.", "true", "false") {
        private boolean value = false;

        @Override
        public Boolean value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Pair<Boolean, Text> out = ConfigUtil.getBooleanOption(this.getName(), value, false);
            this.value = out.getLeft();
            return out.getRight();
        }
    };

    private final String name;
    private final String desc;
    private final String defaultValue;
    private final Collection<String> commandSuggestions;
    private final boolean hasLineBreak;

    ConfigOptions(String name, String defaultValue, String desc, boolean hasLineBreak, String... suggestions) {
        this.hasLineBreak = hasLineBreak;
        this.name = name;
        this.desc = desc;
        this.defaultValue = defaultValue;
        this.commandSuggestions = Arrays.asList(suggestions);
    }

    ConfigOptions(String name, String defaultValue, String desc, String... suggestions) {
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

}
