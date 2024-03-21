package com.birblett.util.config;

import java.util.Collection;

public interface ConfigOption<T> {

    String getName();
    String getDesc();
    T value();
    String getDefaultValue();
    Collection<String> commandSuggestions();
    String setFromString(String value);
    String getWriteable();

}
