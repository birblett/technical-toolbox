package com.birblett.util;

import com.birblett.TechnicalToolbox;
import com.birblett.lib.NodeRemovalInterface;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Collection of server-related utilities
 */
public class ServerUtil {

    /**
     * Removes a command from a server given by the specified string
     * @param server target server
     * @param name target command
     */
    public static void removeCommandByName(MinecraftServer server, String name) {
        RootCommandNode<ServerCommandSource> r = server.getCommandManager().getDispatcher().getRoot();
        ((NodeRemovalInterface) r).removeStringInstance(name);
        server.send(new ServerTask(server.getTicks(), () -> {
            try {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    server.getCommandManager().sendCommandTree(player);
                }
            }
            catch (NullPointerException e) {
                TechnicalToolbox.log("Failed to update command tree, please report");
            }
        }));
    }

}