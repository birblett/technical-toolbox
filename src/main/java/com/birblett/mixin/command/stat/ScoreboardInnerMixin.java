package com.birblett.mixin.command.stat;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.command.stat.TrackedStatManager;
import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Stat change hook for compound stats.
 */
@Mixin(targets = "net/minecraft/scoreboard/Scoreboard$1")
public class ScoreboardInnerMixin {

    @Shadow @Final ScoreboardObjective field_47546;
    @Shadow @Final ScoreHolder field_47547;
    @Shadow @Final Scoreboard field_47548;

    @WrapOperation(method = "setScore", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardScore;setScore(I)V"))
    private void scoreSetHook(ScoreboardScore scoreboardScore, int score, Operation<Void> original) {
        if (TrackedStatManager.whitelistIfEnabled(this.field_47547)) {
            if (this.field_47548 instanceof ServerScoreboard scoreboard) {
                TrackedStatManager.informListeners(scoreboard, this.field_47547, this.field_47546.getCriterion(),
                        score - scoreboardScore.getScore(), score);
            }
            original.call(scoreboardScore, score);
        }
    }

}
