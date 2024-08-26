package com.birblett.mixin.command.stat;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.command.stat.TrackedStatManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.scoreboard.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Handles tracked stat removal and processes all objectives on world load to track if matching prefix.
 */
@Mixin(Scoreboard.class)
public class ScoreboardMixin {

    @Shadow @Final private Object2ObjectMap<String, ScoreboardObjective> objectives;

    @Inject(method = "removeObjective", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardDisplaySlot;values()[Lnet/minecraft/scoreboard/ScoreboardDisplaySlot;"))
    private void removeTracked(ScoreboardObjective objective, CallbackInfo ci) {
        TrackedStatManager.maybeRemoveScore(objective);
    }

    @Inject(method = "readNbt", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Scoreboard;getScores(Ljava/lang/String;)Lnet/minecraft/scoreboard/Scores;"))
    private void getAllScores(NbtList list, RegistryWrapper.WrapperLookup registries, CallbackInfo ci, @Local ScoreboardObjective scoreboardObjective) {
        TrackedStatManager.addTrackedObjective(scoreboardObjective);
        if (scoreboardObjective.getName().startsWith(TrackedStatManager.TRACKED_STAT_PREFIX)) {
            TrackedStatManager.TRACKED_STATS.add(scoreboardObjective);
        }
    }

    @WrapOperation(method = "method_1182", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Scoreboard;getOrCreateScore(Lnet/minecraft/scoreboard/ScoreHolder;Lnet/minecraft/scoreboard/ScoreboardObjective;Z)Lnet/minecraft/scoreboard/ScoreAccess;"))
    private ScoreAccess lo(Scoreboard instance, ScoreHolder scoreHolder, ScoreboardObjective objective, boolean forceWritable, Operation<ScoreAccess> original) {
        if (TrackedStatManager.TRACKED_STATS.contains(objective)) {
            return TrackedStatManager.whitelistIfEnabled(scoreHolder) ? original.call(instance, scoreHolder,
                    objective, forceWritable) :
                    null;
        }
        else {
            return original.call(instance, scoreHolder, objective, forceWritable);
        }
    }

    @WrapOperation(method = "method_1182", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private void lo(Consumer<?> instance, Object t, Operation<Void> original) {
        if (t != null) {
            original.call(instance, t);
        }
    }

}
