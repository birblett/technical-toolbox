package com.birblett.impl.command.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.ServerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles writing and reading of configuration options, with methods called on server start and close
 */
public class AliasManager {

    public static final Map<String, AliasedCommand> ALIASES = new HashMap<>();
    public static final String GLOBAL_PATH = "config/aliases";
    public static final String ALIAS_PATH = "aliases";
    public static final String RECYCLE_PATH = "aliases/recycle";

    public AliasManager() {
    }

    /**
     * Called on server open, sets server and reads aliases into memory.
     * @param server the server being opened
     */
    public void onServerOpen(MinecraftServer server) {
        for (String key : AliasManager.ALIASES.keySet()) {
            AliasManager.ALIASES.get(key).deregister(server, true);
        }
        AliasManager.ALIASES.clear();
        this.readAliases(server);
        for (AliasedCommand aliasedCommand : AliasManager.ALIASES.values()) {
            try {
                aliasedCommand.register(server.getCommandSource().getDispatcher());
            }
            catch (Exception e) {
                TechnicalToolbox.error("Something went wrong with compiling alias {}", aliasedCommand.getAlias());
            }
        }
    }

    /**
     * Called on server close, writes aliases to file and deregisters them.
     */
    public void onServerClose(MinecraftServer server) {
        this.writeAliases(server);
        for (Object key : AliasManager.ALIASES.keySet().toArray()) {
            AliasManager.ALIASES.get((String) key).deregister(server, true);
        }
        AliasManager.ALIASES.clear();
    }

    /**
     * Read all aliases from storage and compile + register them.
     */
    public void readAliases(MinecraftServer server) {
        File global = ServerUtil.getGlobalToolboxPath(server, ALIAS_PATH).toFile();
        ServerUtil.createDirectoryIfNotPresent(global);
        int globalCount = 0;
        File[] globalDir = global.listFiles();
        if (globalDir != null) {
            for (File f : globalDir) {
                if (f.getPath().endsWith(".alias") && AliasedCommand.readFromFile(f.toPath(), true)) {
                    globalCount++;
                }
            }
            if (globalCount > 0) {
                TechnicalToolbox.log("Loaded " + globalCount + " global aliases");
            }
        }
        File directory = ServerUtil.getToolboxPath(server, ALIAS_PATH).toFile();
        if (!ServerUtil.createDirectoryIfNotPresent(directory)){
            TechnicalToolbox.error("Failed to create {} directory, aliases will not be saved", ALIAS_PATH);
            return;
        }
        File[] files;
        if ((files = directory.listFiles()) != null) {
            int count = 0;
            for (File f : files) {
                String name = f.getName().substring(0, f.getName().length() - 6);
                if (f.getPath().endsWith(".alias") && !(AliasManager.ALIASES.containsKey(name) && AliasManager.ALIASES.get(name).global) &&
                        AliasedCommand.readFromFile(f.toPath(), false)) {
                    count++;
                }
            }
            TechnicalToolbox.log("Loaded " + count + " aliases");
        }
        else {
            TechnicalToolbox.warn("Couldn't list files for alias directory, skipping alias loading step");
        }
    }

    /**
     * Writes all aliases to storage.
     */
    public void writeAliases(MinecraftServer server) {
        File directory = ServerUtil.getToolboxPath(server, ALIAS_PATH).toFile();
        File recycle = ServerUtil.getToolboxPath(server, RECYCLE_PATH).toFile();
        if (!ServerUtil.createDirectoryIfNotPresent(directory)) {
            TechnicalToolbox.error("Failed to create {} directory, aliases will not be saved", directory);
        }
        if (!ServerUtil.createDirectoryIfNotPresent(recycle)) {
            TechnicalToolbox.error("Failed to create {} directory, aliases will not be saved", recycle);
        }
        try {
            int removedCount = 0;
            for (File file : FileUtils.listFiles(directory, new String[]{"alias"}, false)) {
                String name = file.getName().substring(0, file.getName().length() - 6);
                if (!AliasManager.ALIASES.containsKey(name) || AliasManager.ALIASES.get(name).global) {
                    removedCount++;
                    Files.move(file.toPath(), recycle.toPath().resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (removedCount > 0) {
                TechnicalToolbox.warn("{} alias files not registered in server, moved to trash", removedCount);
            }
            File[] files = recycle.listFiles((dir, name) -> name.endsWith(".alias"));
            if (ConfigOptions.ALIAS_RECYCLE_BIN_SIZE.val() != -1 && files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                int removed = files.length - ConfigOptions.ALIAS_RECYCLE_BIN_SIZE.val();
                for (int i = 0; i < removed; i++) {
                    Files.deleteIfExists(files[i].toPath());
                }
                if (removed > 0) {
                    TechnicalToolbox.log("Removed {} old alias{} from the recycle bin", removed, removed > 1 ? "es" : "");
                }
            }
        } catch (IOException e) {
            TechnicalToolbox.error("Failed to clean alias directory, please report");
        }
        int count = 0;
        for (String key : AliasManager.ALIASES.keySet()) {
            if (!AliasManager.ALIASES.get(key).global) {
                Path path = ServerUtil.getToolboxPath(server, ALIAS_PATH + "/" + key + ".alias");
                if (AliasManager.ALIASES.get(key).writeToFile(path)) {
                    count++;
                }
            }
        }
        TechnicalToolbox.log("Successfully saved " + count + " aliases");
    }

}
