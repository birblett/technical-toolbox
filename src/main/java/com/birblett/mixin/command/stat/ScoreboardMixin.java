package com.birblett.mixin.command.stat;

import com.birblett.impl.command.stat.TrackedStatManager;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Scoreboard.class)
public class ScoreboardMixin {

    @Inject(method = "readNbt", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/Scoreboard;getScores(Ljava/lang/String;)Lnet/minecraft/scoreboard/Scores;"))
    private void getAllScores(NbtList list, RegistryWrapper.WrapperLookup registries, CallbackInfo ci, @Local ScoreboardObjective scoreboardObjective) {
        TrackedStatManager.addTrackedObjective(scoreboardObjective);
    }

}
