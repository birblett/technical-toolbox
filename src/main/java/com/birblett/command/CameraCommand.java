package com.birblett.command;

import com.birblett.util.config.ConfigOptions;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class CameraCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((CommandManager.literal(ConfigOptions.CAMERA_COMMAND.getWriteable())
                .requires(source -> source.hasPermissionLevel((Integer) ConfigOptions.CAMERA_PERMISSION_LEVEL.value())))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player != null) {
                        context.getSource().sendFeedback(() -> Text.of("Swapping to camera mode"), true);
                        context.getSource().getServer().sendMessage(Text.of("Player " + player.getName() + " swapped " +
                                "to camera mode"));
                    }
                    else {
                        context.getSource().sendFeedback(() -> Text.of("/" + (CommandManager.literal((String)
                                ConfigOptions.CAMERA_COMMAND.value())) + " can only be executed by a player"),
                                false);
                    }
                    return 1;
                }));

    }

}
