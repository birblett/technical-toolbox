package com.birblett.impl.config;

import com.birblett.TechnicalToolbox;
import com.birblett.util.ServerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Handles writing and reading of configuration options, with methods called on server start and close
 */
public class ConfigManager {

    private static final String CONFIG_PATH = "toolbox.conf";

    public final LinkedHashMap<String, ConfigOption<?>> configMap = new LinkedHashMap<>();

    public ConfigManager() {
        for (ConfigOption<?> c : ConfigOption.OPTIONS) {
            this.configMap.put(c.getName(), c);
        }
    }

    public Collection<String> getAllConfigOptions() {
        return this.configMap.keySet();
    }

    /**
     * Called on server open, reads configurations
     * @param server host server
     */
    public void onServerOpen(MinecraftServer server) {
        this.readConfigs(server);
    }

    /**
     * Called on server close, writes configs back to storage.
     */
    public void onServerClose(MinecraftServer server) {
        this.writeConfigs(server);
    }

    /**
     * Loads configs from storage into memory.
     */
    public void readConfigs(MinecraftServer server) {
        try (BufferedReader bufferedReader = Files.newBufferedReader(ServerUtil.getToolboxPath(server, CONFIG_PATH))) {
            String line;
            int lineCount = 0;
            int options = 0;
            HashSet<ConfigOption<?>> configOptions = new HashSet<>(this.configMap.values());
            while ((line = bufferedReader.readLine()) != null) {
                lineCount++;
                String[] split = line.split(":", 2);
                if (split.length == 0) {
                    continue;
                }
                if (split.length != 2 && !split[0].isEmpty()) {
                    TechnicalToolbox.error("Improperly separated config option on line " +
                            lineCount + " ('"+ line + "')");
                    continue;
                }
                if (split.length == 2) {
                    String name = split[0].strip();
                    String value = split[1].strip();
                    if (!this.configMap.containsKey(name)) {
                        TechnicalToolbox.error("Option '" + name + "' does not exist");
                        continue;
                    }
                    Text out = configMap.get(name).setFromString(value, server);
                    configOptions.remove(configMap.get(name));
                    if (out != null) {
                        TechnicalToolbox.error(out.getContent().toString());
                        continue;
                    }
                    options++;
                }
            }
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
        catch (IOException e) {
            TechnicalToolbox.warn("Configuration file 'toolbox.conf' was not found, using defaults");
            if (ServerUtil.createDirectoryIfNotPresent(ServerUtil.getToolboxPath(server, "").toFile())) {
                try (BufferedWriter bufferedWriter = Files.newBufferedWriter(ServerUtil.getToolboxPath(server, CONFIG_PATH))) {
                    bufferedWriter.write("");
                } catch (IOException ex) {
                    TechnicalToolbox.error("Failed to generate configuration file `toolbox.conf`");
                }
            }
        }
    }

    /**
     * Writes configs to storage.
     */
    public void writeConfigs(MinecraftServer server) {
        if (ServerUtil.createDirectoryIfNotPresent(ServerUtil.getToolboxPath(server, "").toFile())) {
            try (BufferedWriter bufferedWriter = Files.newBufferedWriter(ServerUtil.getToolboxPath(server, CONFIG_PATH))) {
                int options = 0;
                for (ConfigOption<?> c : ConfigOption.OPTIONS) {
                    if (!ConfigOptions.CONFIG_WRITE_ONLY_CHANGES.val() || !c.getWriteable().equals(c.getDefaultValue())) {
                        bufferedWriter.write(c.getName() + ": " + c.getWriteable() + "\n");
                        if (c.hasLineBreak()) {
                            bufferedWriter.write("\n");
                        }
                        options++;
                    }
                }
                TechnicalToolbox.log("Wrote " + options + " configuration options to 'toolbox.conf'");
            }
            catch (IOException e) {
                TechnicalToolbox.error("Failed to write to file 'toolbox.conf', configurations will not be saved");
            }
        }
    }

}
