package com.birblett.mixin.command.stat;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.command.stat.TrackedStatManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.scoreboard.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/scoreboard/Scoreboard$1")
public class ScoreboardInnerMixin {

    @Shadow @Final ScoreboardObjective field_47546;
    @Shadow @Final ScoreHolder field_47547;
    @Shadow @Final Scoreboard field_47548;

    @WrapOperation(method = "setScore", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardScore;setScore(I)V"))
    private void scoreSetHook(ScoreboardScore scoreboardScore, int score, Operation<Void> original) {
        if (this.field_47548 instanceof ServerScoreboard scoreboard) {
            TrackedStatManager.informListeners(scoreboard, this.field_47547, this.field_47546.getCriterion(),
                    score - scoreboardScore.getScore());
        }
        original.call(scoreboardScore, score);
    }

}
