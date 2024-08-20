package com.birblett.accessor.command.stat;

import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;

public interface StatTracker {

    void technicalToolbox$UpdateSlot(ScoreboardDisplaySlot slot, ScoreboardObjective objective);
    boolean technicalToolbox$HasObjective(ScoreboardObjective objective);
    ScoreboardDisplaySlot technicalToolbox$GetObjectiveSlot(ScoreboardObjective objective);
}
