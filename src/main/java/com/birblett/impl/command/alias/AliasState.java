package com.birblett.impl.command.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.command.alias.language.Variable;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

import java.util.LinkedHashMap;

public record AliasState(AliasedCommand command, long tick, CommandContext<ServerCommandSource> context, int instruction, LinkedHashMap<String, Variable> variableDefinitions) {

    public void execute() {
        this.command.executeScheduled(this.context, this.instruction, this.variableDefinitions);
    }

}
