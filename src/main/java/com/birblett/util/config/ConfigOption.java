package com.birblett.util.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public interface ConfigOption<T> {

    T value();
    default Text setFromString(String value, MinecraftServer manager) {
        return this.setFromString(value);
    };
    Text setFromString(String value);
    default String getWriteable() {
        return this.value().toString();
    };

}
