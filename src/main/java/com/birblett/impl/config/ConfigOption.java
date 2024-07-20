package com.birblett.impl.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public interface ConfigOption<T> {

    T value();
    default Text setFromString(String value, MinecraftServer manager) {
        return this.setFromString(value);
    };
    Text setFromString(String value);
    default String getWriteable() {
        return String.valueOf(this.value());
    };
    default String getString() {
        return this.value().toString();
    }
    default int getInt() {
        return (int) this.value();
    }
    default float getFloat() {
        return (float) this.value();
    }
    default double getDouble() {
        return (double) this.value();
    }
    default boolean getBool() {
        return (boolean) this.value();
    }

}
