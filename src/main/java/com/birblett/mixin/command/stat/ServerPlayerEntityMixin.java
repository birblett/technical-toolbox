package com.birblett.mixin.command.stat;

import com.birblett.TechnicalToolbox;
import com.birblett.accessor.command.stat.StatTracker;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements StatTracker {

    @Unique private final HashMap<ScoreboardDisplaySlot, ScoreboardObjective> trackedStats = new HashMap<>();

    @Override
    public void technicalToolbox$UpdateSlot(ScoreboardDisplaySlot slot, ScoreboardObjective objective) {
        this.trackedStats.put(slot, objective);
    }

    @Override
    public boolean technicalToolbox$HasObjective(ScoreboardObjective objective) {
        return objective != null && this.trackedStats.containsValue(objective);
    }

    @Override
    public ScoreboardDisplaySlot technicalToolbox$GetObjectiveSlot(ScoreboardObjective objective) {
        for (ScoreboardDisplaySlot slot : this.trackedStats.keySet()) {
            if (this.trackedStats.get(slot).equals(objective)) {
                return slot;
            }
        }
        return null;
    }

    @Inject(method = "increaseStat", at = @At("HEAD"))
    private void statIncreaseHook(Stat<?> stat, int amount, CallbackInfo ci) {
        if (!"minecraft:total_world_time".equals(stat.getValue().toString())) {
            //TechnicalToolbox.log("{}", stat.getName());
        }
    }
}
