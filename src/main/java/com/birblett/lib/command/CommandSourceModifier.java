package com.birblett.lib.command;

public interface CommandSourceModifier {

    void technicalToolbox$setPermissionOverride(boolean override);
    void technicalToolbox$shutUp(boolean shutUp);
    void technicalToolbox$addSelector(String name, String value);
    String technicalToolbox$getSelectorArgument(String name);

}
