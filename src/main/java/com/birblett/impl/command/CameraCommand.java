package com.birblett.impl.command;

import com.birblett.accessor.command.camera.CameraInterface;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Camera command that swaps players in and out of camera mode. Not registered with other commands; is instead
 * de-registered and re-registered along with a command tree refresh in {@link ConfigOptions#CAMERA_COMMAND}
 */
public class CameraCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((Commands.literal(ConfigOptions.CAMERA_COMMAND.getWriteable())
                .requires(source -> ServerUtil.permissionLookup(ConfigOptions.CAMERA_PERMISSION_LEVEL.val(), source) && source.isPlayer()))
                .executes(CameraCommand::setCameraMode));
    }

    private static int setCameraMode(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            String feedback = ((CameraInterface) player).technicalToolbox$SwapCameraMode(true);
            if (feedback != null) {
                player.displayClientMessage(TextUtils.formattable(feedback), true);
                Object logLevel = ConfigOptions.CAMERA_CONSOLE_LOGGING.val();
                if (logLevel.equals("command") || logLevel.equals("spectate")) {
                    context.getSource().getServer().sendSystemMessage(TextUtils.formattable("[Camera Mode] " + player.getScoreboardName()
                            + ": " + feedback));
                }
            }
            return 1;
        }
        return 0;
    }

}
