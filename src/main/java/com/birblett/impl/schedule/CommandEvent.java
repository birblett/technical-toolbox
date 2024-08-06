package com.birblett.impl.schedule;

import com.birblett.lib.command.CommandSourceModifier;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

public record CommandEvent(String command, long tick, int priority, boolean silent) {

    public CommandEvent(String command, long tick) {
        this(command, tick, 1000, false);
    }

    /**
     * Executes on server with command permission level override enabled.
     * @param server Host server
     */
    public void execute(MinecraftServer server) {
        ServerCommandSource source = server.getCommandSource();
        CommandDispatcher<ServerCommandSource> dispatcher = server.getCommandManager().getDispatcher();
        ((CommandSourceModifier) source).technicalToolbox$shutUp(this.silent);
        try {
            dispatcher.execute(dispatcher.parse(this.command, source));
            ((CommandSourceModifier) source).technicalToolbox$shutUp(false);
        }
        catch (CommandSyntaxException e) {
            source.sendError(TextUtils.formattable(e.getMessage()));
            ((CommandSourceModifier) source).technicalToolbox$shutUp(false);
            return;
        }
        ((CommandSourceModifier) source).technicalToolbox$setPermissionOverride(false);
    }

}
