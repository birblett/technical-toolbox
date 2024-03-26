package com.birblett.impl.alias;

import com.birblett.TechnicalToolbox;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles writing and reading of configuration options, with methods called on server start and close
 */
public class AliasManager {

    public static final Map<String, AliasedCommand> ALIASES = new HashMap<>();
    private MinecraftServer server = null;

    public AliasManager() {
    }

    /**
     * Called on server open, sets server and reads aliases into memory.
     * @param server the server being opened
     */
    public void onServerOpen(MinecraftServer server) {
        this.server = server;
        for (String key : AliasManager.ALIASES.keySet()) {
            AliasManager.ALIASES.get(key).deregister(this.server);
        }
        AliasManager.ALIASES.clear();
        this.readAliases();
    }

    /**
     * Called on server close, writes aliases to file and deregisters them.
     */
    public void onServerClose() {
        this.writeAliases();
        for (Object key : AliasManager.ALIASES.keySet().toArray()) {
            AliasManager.ALIASES.get((String) key).deregister(this.server);
        }
        AliasManager.ALIASES.clear();
    }

    /**
     * @return relative path to toolbox_aliases folder
     */
    private Path getDirectory() {
        return this.server.getSavePath(WorldSavePath.ROOT).resolve("toolbox_aliases");
    }

    /**
     * @param name alias name
     * @return qualified name of an alias file specified by the name
     */
    private Path getAliasPath(String name) {
        return this.getDirectory().resolve(name + ".alias");
    }

    public void readAliases() {
        File directory = new File(this.getDirectory().toString());
        if (!directory.isDirectory()){
            return;
        }
        File[] files;
        if ((files = directory.listFiles()) != null) {
            int count = 0;
            for (File f : files) {
                if (f.getPath().endsWith(".alias") && AliasedCommand.readFromFile(this.server, f.toPath())) {
                    count++;
                }
            }
            TechnicalToolbox.log("Successfully loaded " + count + " aliases");
        }
        else {
            TechnicalToolbox.warn("Couldn't list files for alias directory, skipping alias loading step");
        }
    }

    /**
     * Writes all aliases to disk.
     */
    public void writeAliases() {
        File directory = new File(this.getDirectory().toString());
        if (!directory.isDirectory()){
            TechnicalToolbox.log("Aliases directory not found, creating an empty alias directory");
            if (!directory.mkdir()) {
                TechnicalToolbox.warn("Failed to create alias directory, please report");
                return;
            }
        }
        try {
            FileUtils.cleanDirectory(directory);
        } catch (IOException e) {
            TechnicalToolbox.warn("Failed to clean alias directory, please report");
        }
        int count = 0;
        for (String key : AliasManager.ALIASES.keySet()) {
            Path path = this.getAliasPath(key);
            if (AliasManager.ALIASES.get(key).writeToFile(path)) {
                count++;
            }
        }
        TechnicalToolbox.log("Successfully saved " + count + " aliases");
    }

}
