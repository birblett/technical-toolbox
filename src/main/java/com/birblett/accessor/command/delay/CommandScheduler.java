package com.birblett.accessor.command.delay;

import com.birblett.impl.command.alias.AliasedCommand;
import com.birblett.impl.command.alias.language.Variable;
import com.birblett.impl.command.delay.CommandEvent;
import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import net.minecraft.commands.CommandSourceStack;

public interface CommandScheduler {

    boolean technicalToolbox$AddCommandEvent(String command, long delay, String id, int priority, boolean silent, CommandSourceStack source);

    boolean technicalToolbox$RemoveCommandEvent(String id);

    void technicalToolbox$addScheduledAlias(AliasedCommand command, long delay, CommandContext<CommandSourceStack> context, int instruction, LinkedHashMap<String, Variable> variableDefinitions);

    void technicalToolbox$killAll();

    HashMap<String, CommandEvent> technicalToolbox$GetCommandEventMap();

}
