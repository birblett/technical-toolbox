package com.birblett.util.config;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ConfigOption<T> {

    String getName();
    String getDesc();
    T value();
    String getDefaultValue();
    Collection<String> commandSuggestions();
    default String setFromString(String value, @Nullable CommandContext<ServerCommandSource> manager) {
        return this.setFromString(value);
    };
    default String setFromString(String value, MinecraftServer manager) {
        return this.setFromString(value);
    };
    String setFromString(String value);
    default String getWriteable() {
        return this.value().toString();
    };

}
