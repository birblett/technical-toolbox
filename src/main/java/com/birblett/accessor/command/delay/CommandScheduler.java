package com.birblett.accessor.command.delay;

import com.birblett.impl.command.alias.AliasedCommand;
import com.birblett.impl.command.alias.language.Variable;
import com.birblett.impl.command.delay.CommandEvent;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

import java.util.HashMap;
import java.util.LinkedHashMap;

public interface CommandScheduler {

    boolean technicalToolbox$AddCommandEvent(String command, long delay, String id, int priority, boolean silent, ServerCommandSource source);

    boolean technicalToolbox$RemoveCommandEvent(String id);

    void technicalToolbox$addScheduledAlias(AliasedCommand command, long delay, CommandContext<ServerCommandSource> context, int instruction, LinkedHashMap<String, Variable> variableDefinitions);

    HashMap<String, CommandEvent> technicalToolbox$GetCommandEventMap();

}
