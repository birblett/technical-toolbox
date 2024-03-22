package com.birblett.command;

import com.birblett.lib.crafter.CameraInterface;
import com.birblett.util.TextUtils;
import com.birblett.util.config.ConfigOptions;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Camera command that swaps players in and out of camera mode. Not registered with ToolboxCommand; is instead
 * de-registered and re-registered along with a command tree refresh in {@link ConfigOptions#CAMERA_COMMAND}
 */
public class CameraCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((CommandManager.literal(ConfigOptions.CAMERA_COMMAND.getWriteable())
                .requires(source -> source.hasPermissionLevel((Integer) ConfigOptions.CAMERA_PERMISSION_LEVEL.value())))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player != null) {
                        String feedback = ((CameraInterface) player).swapCameraMode(true);
                        if (feedback != null) {
                            player.sendMessage(TextUtils.formattable(feedback), true);
                            Object logLevel = ConfigOptions.CAMERA_CONSOLE_LOGGING.value();
                            if (logLevel.equals("command") || logLevel.equals("spectate")) {
                                context.getSource().getServer().sendMessage(TextUtils.formattable("[Camera Mode] " +
                                        player.getEntityName() + ": " + feedback));
                            }
                        }
                    }
                    else {
                        context.getSource().sendFeedback(() -> TextUtils.formattable("/" + (CommandManager.literal((String)
                                ConfigOptions.CAMERA_COMMAND.value())) + " can only be executed by a player"),
                                false);
                    }
                    return 1;
                }));
    }

}
