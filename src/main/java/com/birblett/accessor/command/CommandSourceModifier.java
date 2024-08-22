package com.birblett.accessor.command;

import com.birblett.impl.command.alias.language.Operator;
import net.minecraft.scoreboard.ScoreboardCriterion;

import java.util.HashSet;

public interface CommandSourceModifier {

    void technicalToolbox$setPermissionOverride(boolean override);
    void technicalToolbox$shutUp(boolean shutUp);
    void technicalToolbox$addSelector(String name, String value);
    String technicalToolbox$getSelectorArgument(String name);
    void technicalToolbox$setReturnValue(Operator o);
    Operator technicalToolbox$getReturnValue();
    void technicalToolbox$addCriterion(ScoreboardCriterion criterion);
    HashSet<ScoreboardCriterion> technicalToolbox$getCriteria(ScoreboardCriterion criterion);

}
