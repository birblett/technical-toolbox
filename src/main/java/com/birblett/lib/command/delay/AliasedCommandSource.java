package com.birblett.lib.command.delay;

public interface AliasedCommandSource {

    void technicalToolbox$SetOpt(String s, Object value);
    Object technicalToolbox$GetOpt(String s);
    void technicalToolbox$ResetOpt();
    void technicalToolbox$AddToInstructionCount(int i);
    int technicalToolbox$getInstructionCount();

}
