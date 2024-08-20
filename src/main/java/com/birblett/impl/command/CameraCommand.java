package com.birblett.impl.command;

import com.birblett.impl.config.ConfigOptions;
import com.birblett.accessor.command.camera.CameraInterface;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Camera command that swaps players in and out of camera mode. Not registered with other commands; is instead
 * de-registered and re-registered along with a command tree refresh in {@link ConfigOptions#CAMERA_COMMAND}
 */
public class CameraCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((CommandManager.literal(ConfigOptions.CAMERA_COMMAND.getWriteable())
                .requires(source -> source.hasPermissionLevel(ConfigOptions.CAMERA_PERMISSION_LEVEL.val()) && source.isExecutedByPlayer()))
                .executes(CameraCommand::setCameraMode));
    }

    private static int setCameraMode(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            String feedback = ((CameraInterface) player).technicalToolbox$SwapCameraMode(true);
            if (feedback != null) {
                player.sendMessage(TextUtils.formattable(feedback), true);
                Object logLevel = ConfigOptions.CAMERA_CONSOLE_LOGGING.val();
                if (logLevel.equals("command") || logLevel.equals("spectate")) {
                    context.getSource().getServer().sendMessage(TextUtils.formattable("[Camera Mode] " + player.getNameForScoreboard()
                            + ": " + feedback));
                }
            }
            return 1;
        }
        return 0;
    }

}
