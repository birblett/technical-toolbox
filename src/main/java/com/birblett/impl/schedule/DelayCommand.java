package com.birblett.impl.schedule;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.command.CommandScheduler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Simple non-serialized command scheduler.
 */
public class DelayCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((CommandManager.literal(ConfigOption.DELAY_COMMAND.val())
                .then(CommandManager.argument("delay", LongArgumentType.longArg(1))
                        .then(CommandManager.argument("command", StringArgumentType.string())
                        .executes(context -> {
                            long delay = context.getArgument("delay", Long.class);
                            String command = context.getArgument("command", String.class);
                            ((CommandScheduler) context.getSource().getWorld()).addCommandEvent(command, delay);
                            return 0;
                        })
        ))));
    }

}
