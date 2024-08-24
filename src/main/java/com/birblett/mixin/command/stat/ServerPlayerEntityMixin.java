package com.birblett.mixin.command.stat;

import com.birblett.accessor.command.stat.StatTracker;
import com.birblett.impl.command.stat.TrackedStatManager;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Stat increase hook for players, and also tracks individual player stat tracking preferences.
 */
@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements StatTracker {

    @Unique private final HashMap<ScoreboardDisplaySlot, ScoreboardObjective> trackedStats = new HashMap<>();
    @Unique private final Set<ScoreboardObjective> initializedStats = new HashSet<>();

    @Override
    public void technicalToolbox$UpdateObjective(ScoreboardObjective objective) {
        this.initializedStats.add(objective);
    }

    @Override
    public void technicalToolbox$UpdateSlot(ScoreboardDisplaySlot slot, ScoreboardObjective objective) {
        this.trackedStats.put(slot, objective);
        this.initializedStats.add(objective);
    }

    @Override
    public boolean technicalToolbox$StopTracking(ScoreboardDisplaySlot slot) {
        if (this.trackedStats.remove(slot) != null) {
            ((ServerPlayerEntity) (Object) this).networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(slot, null));
            return true;
        }
        return false;
    }

    @Override
    public boolean technicalToolbox$HasObjective(ScoreboardObjective objective) {
        return objective != null && this.initializedStats.contains(objective);
    }

    @Override
    public boolean technicalToolbox$HasDisplayedObjective(ScoreboardObjective objective) {
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
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.getScoreboard() instanceof ServerScoreboard scoreboard) {
            TrackedStatManager.informListeners(scoreboard, player, stat, amount, player.getStatHandler().getStat(stat));
        }
    }
}
