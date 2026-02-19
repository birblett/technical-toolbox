package com.birblett.impl.config;

import com.birblett.TechnicalToolbox;
import com.birblett.util.ConfigUtil;
import com.birblett.util.ServerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Handles writing and reading of configuration options, with methods called on server start and close
 */
public class ConfigManager {

    public static final String CONFIG_PATH = "toolbox.conf";

    private final LinkedHashMap<String, ConfigOption<?>> configMap = new LinkedHashMap<>();

    public ConfigManager() {
        this.update();
    }

    public void update() {
        for (ConfigOption<?> c : ConfigOption.OPTIONS) {
            this.configMap.put(c.getName(), c);
        }
    }

    public ConfigOption<?> get(String key) {
        return this.configMap.get(key);
    }

    public Collection<String> getAllConfigOptions() {
        return this.configMap.keySet();
    }

    /**
     * Called on server open, reads configurations
     *
     * @param server host server
     */
    public void onServerOpen(MinecraftServer server) {
        this.readConfigs(ServerUtil.getToolboxPath(server), server);
    }

    /**
     * Called on server close, writes configs back to storage.
     */
    public void onServerClose(MinecraftServer server) {
        this.writeConfigs(ServerUtil.getToolboxPath(server));
    }

    /**
     * Loads configs from storage into memory.
     */
    public void readConfigs(Path basePath, @Nullable MinecraftServer server) {
        HashSet<ConfigOption<?>> configOptions = new HashSet<>(this.configMap.values());
        ConfigUtil.readConfigs(basePath, CONFIG_PATH,
                (name, value) -> {
                    if (!this.configMap.containsKey(name)) {
                        if (server != null) {
                            TechnicalToolbox.error("Option '" + name + "' does not exist");
                        }
                        return false;
                    }
                    Text out = configMap.get(name).setFromString(value, server);
                    configOptions.remove(configMap.get(name));
                    if (out != null) {
                        TechnicalToolbox.error(out.getContent().toString());
                        return false;
                    }
                    return true;
                },
                (options) -> {
                    if (server != null) {
                        TechnicalToolbox.log("Loaded " + options + " valid configuration options from " +
                                "'toolbox.conf'");
                        if (configMap.size() - options > 0) {
                            TechnicalToolbox.log((configMap.size() - options) + " configuration options were not " +
                                    "specified, using defaults");
                            for (ConfigOption<?> configOption : configOptions) {
                                configOption.setFromString(configOption.getDefaultValue(), server);
                            }
                        }
                    }
                });
    }

    /**
     * Writes configs to storage.
     */
    public void writeConfigs(Path basePath) {
        ConfigUtil.writeConfigs(basePath, CONFIG_PATH,
                ConfigOption.OPTIONS.stream().map((c) ->
                        !ConfigOptions.CONFIG_WRITE_ONLY_CHANGES.val() || !c.getWriteable().equals(c.getDefaultValue()) ?
                                c.getName() + " " + c.getWriteable() + (c.hasLineBreak() ? "\n" : "") : null));
    }

}
