package com.birblett.impl.command.delay;

import com.birblett.accessor.command.CommandSourceModifier;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

public record CommandEvent(String id, String command, long tick, int priority, boolean silent, ServerCommandSource source) {

    /**
     * Executes on server with command permission level override enabled.
     */
    public void execute(MinecraftServer server) {
        CommandDispatcher<ServerCommandSource> dispatcher = this.source.getServer().getCommandManager().getDispatcher();
        ((CommandSourceModifier) this.source).technicalToolbox$shutUp(this.silent);
        try {
            dispatcher.execute(dispatcher.parse(this.command, this.source));
            ((CommandSourceModifier) this.source).technicalToolbox$shutUp(false);
        }
        catch (CommandSyntaxException e) {
            this.source.sendError(TextUtils.formattable(e.getMessage()));
            ((CommandSourceModifier) this.source).technicalToolbox$shutUp(false);
            return;
        }
        ((CommandSourceModifier) this.source).technicalToolbox$setPermissionOverride(false);
    }

}
