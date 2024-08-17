package com.birblett.impl.command.alias.language;

import com.mojang.brigadier.arguments.ArgumentType;

import java.util.function.Function;

/**
 * The basic variable for use during program execution. Holds a definition and a value.
 *
 * @param type
 * @param value
 */
public record Variable(Definition type, Object value) {

    /**
     * Defines variable type, primarily for use with arguments.
     * @param argc number of arguments required
     * @param argumentTypeProvider provides an argument type to be passed to an argument builder
     * @param clazz class used to retrieve arguments
     */
    public record Entry<T>(int argc, Function<String[], ArgumentType<T>> argumentTypeProvider, Class<T> clazz) {}

    /**
     * Defines various traits related to variables, such as its name, type, and expected arguments.
     */
    public static class Definition {

        public final String name;
        public final String typeName;
        public final Entry<?> type;
        public final String[] args;

        public Definition(String name, String type, String[] args) {
            this.name = name;
            this.typeName = type;
            this.type = AliasConstants.ARGUMENT_TYPES.getOrDefault(type, AliasConstants.ARGUMENT_TYPES.get("string"));
            this.args = args;
        }

        public ArgumentType<?> getArgumentType() {
            return this.type.argumentTypeProvider().apply(this.args);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

    }

}
