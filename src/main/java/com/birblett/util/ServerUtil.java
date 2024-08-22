package com.birblett.util;

import com.birblett.TechnicalToolbox;
import com.birblett.accessor.command.CommandNodeModifier;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.Path;

/**
 * Collection of server-related utilities
 */
public class ServerUtil {

    /**
     * @param path a relative path to a file
     * @return path to a file on within the current world folder
     */
    public static Path getWorldPath(MinecraftServer server, String path) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(path);
    }

    /**
     * @param path a relative path to a file
     * @return path to a file on within the current world folder's technical toolbox folder
     */
    public static Path getToolboxPath(MinecraftServer server, String path) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("technical_toolbox/" + path);
    }

    /**
     * @param path relative path to return
     * @return return .minecraft/[path] directory
     */
    public static Path getGlobalToolboxPath(MinecraftServer server, String path) {
        return server.getPath("technical_toolbox/" + path);
    }

    /**
     * Attempts to create directories corresponding to the provided filepath.
     * @return whether the directory was successfully created or not
     */
    public static boolean createDirectoryIfNotPresent(File directory) {
        if (!directory.isDirectory()){
            TechnicalToolbox.log("{} not found, creating an empty directory", StringUtils.capitalize(directory.getName()));
            if (!directory.mkdirs()) {
                TechnicalToolbox.warn("Failed to create directory, please report");
                return false;
            }
        }
        return true;
    }

    /**
     * Removes a command from a server given by the specified string
     * @param server target server
     * @param name target command
     */
    public static void removeCommandByName(MinecraftServer server, String name) {
        RootCommandNode<ServerCommandSource> r = server.getCommandManager().getDispatcher().getRoot();
        ((CommandNodeModifier) r).technicalToolbox$RemoveStringInstance(name);
        refreshCommandTree(server);
    }

    /**
     * Refreshes the server command tree.
     * @param server target server
     */
    public static void refreshCommandTree(MinecraftServer server) {
        server.send(new ServerTask(server.getTicks(), () -> {
            try {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    server.getCommandManager().sendCommandTree(player);
                }
            }
            catch (NullPointerException e) {
                TechnicalToolbox.error("Failed to update command tree, please report");
            }
        }));
    }

}
