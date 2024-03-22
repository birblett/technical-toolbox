package com.birblett.util.config;

import com.birblett.command.CameraCommand;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

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
            " also write to file.", true, "false", "true") {
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
    CAMERA_COMMAND("cameraCommand", "cam", "Camera command string, usage /[cmd string]",
            "cam", "c", "cs") {
        private String value = "cam";

        @Override
        public String value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value, MinecraftServer server) {
            String oldValue = this.value;
            Text s = setFromString(value);
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
    CRAFTER_COOLDOWN("crafterCooldown", "4", "Gameticks of crafter block cooldown, will be " +
            "instant if set to 0.", "0", "4") {
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
    CRAFTER_DISABLED_SLOT_ITEMS("crafterDisabledSlotItems", "minecraft:wooden_shovel", "Items " +
            "to act as disabled slots in dropper crafters; accepts item id, specific custom names, or nbt tag(s) to " +
            "match.", "\"minecraft:wooden_shovel\"") {
        private String writeableOption = "items:wooden_shovel";
        private Predicate<ItemStack> value = stack -> stack.isOf(Items.WOODEN_SHOVEL);

        @Override
        public Predicate<ItemStack> value() {
            return this.value;
        }

        @Override
        public Text setFromString(String value) {
            Identifier identifier = Identifier.tryParse(value);
            if (identifier != null) {
                if (!Registries.ITEM.containsId(identifier)) {
                    return ConfigUtil.setFailCustom("Invalid item id ", value, Formatting.RED, " for option ",
                            this.getName(), null);
                }
                Item item = Registries.ITEM.get(identifier);
                this.value = stack -> stack.isOf(item);
                this.writeableOption = value;
                return null;
            }
            try {
                NbtCompound nbt = StringNbtReader.parse(value);
                this.value = stack -> {
                    NbtCompound compound = stack.getNbt();
                    if (compound != null) {
                        for (String key : nbt.getKeys()) {
                            if (!compound.contains(key)) {
                                return false;
                            }
                            NbtElement e1 = compound.get(key), e2 = nbt.get(key);
                            if (e1 == null || e2 == null || !e1.toString().equals(e2.toString())) {
                                return false;
                            }
                        }
                        return true;
                    }
                    return false;
                };
                this.writeableOption = value;
                return null;
            }
            catch (CommandSyntaxException e) {
                if (value.length() >= 3 && value.endsWith("\"") && value.startsWith("\"")) {
                    String s = value.substring(1, value.length() - 1);
                    this.value = stack -> stack.getName().getString().equals(s);
                    this.writeableOption = value;
                    return null;
                }
                return ConfigUtil.setFailCustom("Did not read valid item identifier, nbt, or custom name from value ",
                        value, Formatting.RED, " for option ", this.getName(), null);
            }
        }

        @Override
        public String getWriteable() {
            return this.writeableOption;
        }
    },
    CRAFTER_QUASI_POWER("crafterQuasiPower", "false", "Whether crafter droppers can be quasi-" +
            "powered or not.", true, "true", "false") {
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
            "1.20+ should be allowed", "true", "false") {
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
    }
    ;

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
