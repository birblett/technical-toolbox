package com.birblett.impl.command.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

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
                TechnicalToolbox.log("Something went wrong with compiling alias {}", aliasedCommand.getAlias());
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
     * @return return .minecraft directory
     */
    private Path getMinecraftDirectory(MinecraftServer server) {
        return server.getPath("config/technical_toolbox");
    }

    /**
     * @return relative path to toolbox_aliases folder
     */
    private Path getDirectory(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("toolbox_aliases");
    }

    /**
     * @return relative path to toolbox_aliases folder
     */
    private Path getRecycleDirectory(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("toolbox_aliases/recycle");
    }

    /**
     * @param name alias name
     * @return qualified name of an alias file specified by the name
     */
    private Path getAliasPath(MinecraftServer server, String name) {
        return this.getDirectory(server).resolve(name + ".alias");
    }

    public void readAliases(MinecraftServer server) {
        File global = this.getMinecraftDirectory(server).toFile();
        int globalCount = 0;
        File[] globalDir = global.listFiles();
        if (globalDir != null) {
            for (File f : globalDir) {
                if (f.getPath().endsWith(".alias") && AliasedCommand.readFromFile(server, f.toPath(), true)) {
                    globalCount++;
                }
            }
            if (globalCount > 0) {
                TechnicalToolbox.log("Loaded " + globalCount + " global aliases");
            }
        }
        File directory = new File(this.getDirectory(server).toString());
        if (!directory.isDirectory()){
            return;
        }
        File[] files;
        if ((files = directory.listFiles()) != null) {
            int count = 0;
            for (File f : files) {
                String name = f.getName().substring(0, f.getName().length() - 6);
                if (f.getPath().endsWith(".alias") && !(AliasManager.ALIASES.containsKey(name) && AliasManager.ALIASES.get(name).global) &&
                        AliasedCommand.readFromFile(server, f.toPath(), false)) {
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
        File directory = new File(this.getDirectory(server).toString());
        File recycle = new File(this.getRecycleDirectory(server).toString());
        if (!(this.createDirectoryIfNotPresent(directory) && this.createDirectoryIfNotPresent(recycle))) {
            return;
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
                int removed = 0;
                while (files.length > ConfigOptions.ALIAS_RECYCLE_BIN_SIZE.val()) {
                    files = Arrays.copyOfRange(files, 1, files.length);
                    removed++;
                }
                if (removed > 0) {
                    TechnicalToolbox.log("Removed {} old alias{} from the recycle bin", removed, removed > 1 ? "es" : "");
                }
            }
        } catch (IOException e) {
            TechnicalToolbox.warn("Failed to clean alias directory, please report");
        }
        int count = 0;
        for (String key : AliasManager.ALIASES.keySet()) {
            if (!AliasManager.ALIASES.get(key).global) {
                Path path = this.getAliasPath(server, key);
                if (AliasManager.ALIASES.get(key).writeToFile(path)) {
                    count++;
                }
            }
        }
        TechnicalToolbox.log("Successfully saved " + count + " aliases");
    }

    private boolean createDirectoryIfNotPresent(File directory) {
        if (!directory.isDirectory()){
            TechnicalToolbox.log("{} not found, creating an empty directory", StringUtils.capitalize(directory.getName()));
            if (!directory.mkdir()) {
                TechnicalToolbox.warn("Failed to create directory, please report");
                return false;
            }
        }
        return true;
    }

}
