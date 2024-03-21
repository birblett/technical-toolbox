package com.birblett.util.config;

import com.birblett.TechnicalToolbox;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;

public class ConfigManager {

    private MinecraftServer server = null;
    public final HashMap<String, ConfigOptions> configMap = new HashMap<>();

    public ConfigManager() {
        for (ConfigOptions c : ConfigOptions.values()) {
            this.configMap.put(c.getName(), c);
        }
    }

    public Collection<String> getAllConfigOptions() {
        return this.configMap.keySet();
    }

    public void onServerOpen(MinecraftServer server) {
        this.server = server;
        readConfigsFromFile();
    }

    public void onServerClose() {
        this.writeConfigsToFile();
    }

    private Path getFile() {
        return server.getSavePath(WorldSavePath.ROOT).resolve("toolbox.conf");
    }

    public void readConfigsFromFile() {
        try (BufferedReader bufferedReader = Files.newBufferedReader(this.getFile())) {
            String line;
            int lineCount = 0;
            int options = 0;
            while ((line = bufferedReader.readLine()) != null) {
                lineCount++;
                String[] split = line.split(":", 2);
                if (split.length == 0) {
                    continue;
                }
                if (split.length != 2) {
                    TechnicalToolbox.log("Improperly separated config option on line " + lineCount + " ('"+ line + "')");
                    continue;
                }
                String name = split[0].strip();
                String value = split[1].strip();
                if (!configMap.containsKey(name)) {
                    TechnicalToolbox.log("Option '" + name + "' does not exist");
                    continue;
                }
                String out = configMap.get(name).setFromString(value);
                if (out != null) {
                    TechnicalToolbox.log(out);
                    continue;
                }
                options++;
            }
            TechnicalToolbox.log("Read " + options + " valid configuration options from 'toolbox.conf'");
        }
        catch (IOException e) {
            TechnicalToolbox.warn("Configuration file 'toolbox.conf' was not found, generating defaults");
            writeConfigsToFile();
        }
    }

    public void writeConfigsToFile() {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(this.getFile())) {
            int options = 0;
            for (ConfigOptions c : ConfigOptions.values()) {
                 bufferedWriter.write(c.getName() + ": " + c.getWriteable() + "\n");
                 options++;
            }
            TechnicalToolbox.log("Wrote " + options + " configuration options to 'toolbox.conf'");
        }
        catch (IOException e) {
            TechnicalToolbox.error("Failed to write to file 'toolbox.conf', configurations will not be saved");
        }
    }

}
