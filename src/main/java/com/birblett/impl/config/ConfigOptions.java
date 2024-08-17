package com.birblett.impl.config;

import com.birblett.impl.command.CameraCommand;
import com.birblett.impl.command.delay.DelayCommand;
import com.birblett.util.ServerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;

public class ConfigOptions {

    public static final ConfigOption<Integer> CONFIG_VIEW_PERMISSION_LEVEL = ConfigOption.intConfig(
            "configViewPermissionLevel", 0,
            "Permission level required to view configurations.",
            0, 4,
            "0", "4");
    public static final ConfigOption<Boolean> CONFIG_WRITE_ON_CHANGE = ConfigOption.boolConfig(
            "configWriteOnChange", false,
            "If enabled, changing configurations will also write to storage.",
            "false", "true");
    public static final ConfigOption<Boolean> CONFIG_WRITE_ONLY_CHANGES = ConfigOption.boolConfig(
            "configWriteOnlyChanges", true,
            "If enabled, only changed configurations will be written to storage.",
            true, "false", "true");
    public static final ConfigOption<Integer> ALIAS_DEFAULT_PERMISSION = ConfigOption.intConfig(
            "aliasDefaultPermission", 0,
            "Default permission level required to execute aliases.",
            0, 4,
            "0", "4");
    public static final ConfigOption<Boolean> ALIAS_DEFAULT_SILENT = ConfigOption.boolConfig(
            "aliasDefaultSilent", false,
            "Whether aliases default to sending feedback or not.",
            "true", "false");
    public static final ConfigOption<Integer> ALIAS_INSTRUCTION_LIMIT = ConfigOption.intConfig(
            "aliasInstructionLimit", 50000,
            "Maximum number of instructions (not lines) that an alias can execute. " +
                    "Also accounts for alias recursion. Set to -1 for no limit.",
            -1, Integer.MAX_VALUE, "-1", "20");
    public static final ConfigOption<Integer> ALIAS_MAX_RECURSION_DEPTH = ConfigOption.intConfig(
            "aliasMaxRecursionDepth", 500,
            "Maximum number of recursive calls. Setting too high may result in " +
                    "stack overflow for some recursive programs.",
            0, Integer.MAX_VALUE, "500");
    public static final ConfigOption<Boolean> ALIAS_MODIFY_COMPILE = ConfigOption.boolConfig(
            "aliasCompileOnModification", true,
            "Whether aliases should be compiled whenever they are modified. When false, " +
                    "they can instead be compiled with /alias compile.",
            "true", "false");
    public static final ConfigOption<Integer> ALIAS_RECYCLE_BIN_SIZE = ConfigOption.intConfig(
            "aliasRecycleBinSize", 20,
            "Size of the recycle bin for old or unused alias files. Older files are " +
                    "recycled after hitting the limit. Set to -1 for no limit.",
            -1, 20,
            "0", "-1", "20");
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
    public static final ConfigOption<Boolean> CAMERA_GENERATES_CHUNKS = ConfigOption.boolConfig(
            "cameraGeneratesChunks", false,
            "Whether players in camera mode should generate chunks or not",
            "true", "false");
    public static final ConfigOption<Boolean> CAMERA_CAN_SPECTATE = ConfigOption.boolConfig(
            "cameraCanSpectate", false,
            "Whether players in camera mode can spectate other players",
            "true", "false");
    public static final ConfigOption<Boolean> CAMERA_CAN_TELEPORT = ConfigOption.boolConfig(
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
    public static final ConfigOption<Integer> CAMERA_PERMISSION_LEVEL = ConfigOption.intConfig(
            "cameraPermissionLevel", 4,
            "Permission level required to use the camera command.",
            0, 4, true,
            "0", "4");
    public static final ConfigOption<String> DELAY_COMMAND = new ConfigOption<>(
            "delayCommand", "delay",
            "Command scheduling command string, usage /[cmd string] <delay> <command>.",
            "delay", "sch") {
        @Override
        public Text setFromString(String value, MinecraftServer server) {
            String oldValue = this.value;
            Text s = this.setFromString(value);
            if (s == null && server != null) {
                ServerUtil.removeCommandByName(server, oldValue);
                DelayCommand.register(server.getCommandManager().getDispatcher());
            }
            return s;
        }

        @Override
        public Text setFromString(String value) {
            Pair<String, Text> out = getStringOption(this.getName(), value, "delay");
            this.value = out.getLeft();
            return out.getRight();
        }
    };
    public static final ConfigOption<Integer> FEATURE_COPPER_BULB_DELAY = ConfigOption.intConfig(
            "featureCopperBulbDelay", 0,
            "Gameticks of copper bulb delay when powered",
            0, Integer.MAX_VALUE,
            "0", "1");
    public static final ConfigOption<Boolean> FEATURE_COPPER_BULB_NO_POWERED_UPDATES = ConfigOption.boolConfig(
            "featureCopperBulbNoPoweredUpdates", false,
            "Prevents power/depower (specifically the powered state) of copper bulbs from sending block updates.",
            "true", "false");
    public static final ConfigOption<Integer> FEATURE_CRAFTER_COOLDOWN = ConfigOption.intConfig(
            "featureCrafterCooldown", 4,
            "Gameticks of crafter cooldown, will be instant if set to 0.",
            0, Integer.MAX_VALUE,
            "0", "4");
    public static final ConfigOption<Boolean> FEATURE_CRAFTER_QUASI_POWER = ConfigOption.boolConfig(
            "featureCrafterQuasiPower", false,
            "Whether crafters can be quasi-powered or not.", true,
            "true", "false");
    public static final ConfigOption<Float> FEATURE_SPEED_LIMIT = ConfigOption.floatConfig(
            "featureSpeedLimit", 100.0f,
            "The velocity threshold after which the server corrects player velocity.",
            0.0f, Float.MAX_VALUE,
            "100.0", String.valueOf(Integer.MAX_VALUE));
    public static final ConfigOption<Float> FEATURE_SPEED_LIMIT_ELYTRA = ConfigOption.floatConfig(
            "featureSpeedLimitElytra", 300.0f,
            "The elytra velocity threshold after which the server corrects player velocity.",
            0.0f, Float.MAX_VALUE,
            "300.0", String.valueOf(Integer.MAX_VALUE));
    public static final ConfigOption<Double> FEATURE_SPEED_LIMIT_VEHICLE = ConfigOption.doubleConfig(
            "featureSpeedLimitVehicle", 100.0,
            "The riding velocity threshold after which the server corrects player velocity.",
            0.0, Double.MAX_VALUE, true,
            "100.0", String.valueOf(Integer.MAX_VALUE));
    public static final ConfigOption<Boolean> LEGACY_BAD_OMEN = ConfigOption.boolConfig(
            "legacyBadOmen", false,
            "Whether pre-1.21 bad omen/raid mechanics should be used.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_END_CRYSTAL_COLLISION = ConfigOption.boolConfig(
            "legacyEndCrystalCollision", false,
            "End crystals won't check for collision in their tick method.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_END_CRYSTAL_FIRE_DAMAGE = ConfigOption.boolConfig(
            "legacyEndCrystalFireDamage", false,
            "Allow end crystals to take fire damage.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_END_PLATFORM = ConfigOption.boolConfig(
            "legacyEndPlatform", false,
            "Re-enables pre-1.21 end portal logic.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_POI_PROPERTY_CHECK = ConfigOption.boolConfig(
            "legacyDisablePoiPropertyCheck", false,
            "Disables portal POI HORIZONTAL_AXIS property check.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_PROTECTION_COMPATIBILITY = ConfigOption.boolConfig(
            "legacyProtectionCompatibility", false,
            "Makes all protection types compatible with each other.",
            "true", "false");
    public static final ConfigOption<Boolean> LEGACY_TRAPDOOR_UPDATE_SKIPPING = ConfigOption.boolConfig(
            "legacyTrapdoorUpdateSkipping", false,
            "Whether update skipping should be allowed.",
            "true", "false");

}
