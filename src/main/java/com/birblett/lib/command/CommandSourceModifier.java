package com.birblett.lib.command;

import com.birblett.impl.command.alias.language.Operator;

public interface CommandSourceModifier {

    void technicalToolbox$setPermissionOverride(boolean override);
    void technicalToolbox$shutUp(boolean shutUp);
    void technicalToolbox$addSelector(String name, String value);
    String technicalToolbox$getSelectorArgument(String name);
    void technicalToolbox$setReturnValue(Operator o);
    Operator technicalToolbox$getReturnValue();

}
