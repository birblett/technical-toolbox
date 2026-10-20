package com.birblett.util;

import com.birblett.TechnicalToolbox;
import com.birblett.accessor.command.CommandNodeModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.Path;

/**
 * Collection of server-related utilities
 */
public class ServerUtil {

    /**
     * @return path to a file on within the current world folder
     */
    public static Path getWorldPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    /**
     * @return path to a file on within the current world folder's technical toolbox folder
     */
    public static Path getToolboxPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("technical_toolbox/");
    }

    /**
     * @return return .minecraft/config/technical_toolbox/ directory
     */
    public static Path getGlobalToolboxPath() {
        return getBasePath().resolve("config/technical_toolbox/");
    }

    public static Path getBasePath() {
        return Path.of("");
    }

    /**
     * Attempts to create directories corresponding to the provided filepath.
     *
     * @return whether the directory was successfully created or not
     */
    public static boolean createDirectoryIfNotPresent(File directory) {
        if (!directory.isDirectory()) {
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
     *
     * @param server target server
     * @param name   target command
     */
    public static void removeCommandByName(MinecraftServer server, String name) {
        RootCommandNode<CommandSourceStack> r = server.getCommands().getDispatcher().getRoot();
        ((CommandNodeModifier) r).technicalToolbox$RemoveStringInstance(name);
        refreshCommandTree(server);
    }

    /**
     * Refreshes the server command tree.
     *
     * @param server target server
     */
    public static void refreshCommandTree(MinecraftServer server) {
        server.schedule(new TickTask(server.getTickCount(), () -> {
            try {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    server.getCommands().sendCommands(player);
                }
            } catch (NullPointerException e) {
                TechnicalToolbox.error("Failed to update command tree, please report");
            }
        }));
    }

    /**
     * Check if command source has admin perms or not.
     */
    public static boolean hasAdminPerms(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    /**
     * Reverse lookup of legacy permission levels
     *
     * @param permission legacy permission level
     */
    public static boolean permissionLookup(int permission, CommandSourceStack source) {
        return switch (permission) {
            case 1 -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
            case 2 -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
            case 3 -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
            case 4 -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER);
            default -> true;
        };
    }

    public static void commandFeedback(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.nullToEmpty(message), true);
    }

    public static void commandFeedback(CommandContext<CommandSourceStack> context, MutableComponent message) {
        context.getSource().sendSuccess(() -> message, true);
    }

}
