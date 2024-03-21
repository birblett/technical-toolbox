package com.birblett.util;

import com.birblett.TechnicalToolbox;
import com.birblett.lib.NodeRemovalInterface;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class ServerUtil {

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
                TechnicalToolbox.log("Failed to remove command '" + name + "', please report");
            }
        }));
    }

}
