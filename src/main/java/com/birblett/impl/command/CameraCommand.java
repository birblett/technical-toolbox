package com.birblett.impl.command;

import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.command.camera.CameraInterface;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Camera command that swaps players in and out of camera mode. Not registered with other commands; is instead
 * de-registered and re-registered along with a command tree refresh in {@link ConfigOption#CAMERA_COMMAND}
 */
public class CameraCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((CommandManager.literal(ConfigOption.CAMERA_COMMAND.getWriteable())
                .requires(source -> source.hasPermissionLevel(ConfigOption.CAMERA_PERMISSION_LEVEL.val())))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player != null) {
                        String feedback = ((CameraInterface) player).swapCameraMode(true);
                        if (feedback != null) {
                            player.sendMessage(TextUtils.formattable(feedback), true);
                            Object logLevel = ConfigOption.CAMERA_CONSOLE_LOGGING.val();
                            if (logLevel.equals("command") || logLevel.equals("spectate")) {
                                context.getSource().getServer().sendMessage(TextUtils.formattable("[Camera Mode] " + player
                                        .getNameForScoreboard() + ": " + feedback));
                            }
                        }
                        return 1;
                    }
                    else {
                        context.getSource().sendFeedback(() -> TextUtils.formattable("/" + (CommandManager.literal(ConfigOption
                                        .CAMERA_COMMAND.val())) + " can only be executed by a player"),
                                false);
                        return 0;
                    }
                }));
    }

}
