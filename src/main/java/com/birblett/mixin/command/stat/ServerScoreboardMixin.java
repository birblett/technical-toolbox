package com.birblett.mixin.command.stat;

import com.birblett.accessor.command.stat.StatTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Set;

/**
 * Mixins to prevent critical crashes when scoreboard updates are sent to the client. Why can't you just throw properly mojang???
 */
@Mixin(ServerScoreboard.class)
public class ServerScoreboardMixin {

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Set<ScoreboardObjective> objectives;

    @WrapOperation(method = "addScoreboardObjective", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void noResendToSubscribedPlayer(ServerPlayNetworkHandler instance, Packet<?> packet, Operation<Void> original, @Local ServerPlayerEntity player) {
        StatTracker tracker = (StatTracker) player;
        Scoreboard scoreboard = (ServerScoreboard) (Object) this;
        if (packet instanceof ScoreboardObjectiveUpdateS2CPacket scoreboardPacket &&
                !tracker.technicalToolbox$HasObjective(scoreboard.getNullableObjective(scoreboardPacket.getName()))) {
            original.call(instance, packet);
        }

    }

    @Inject(method = "updateRemovedObjective", at = @At("HEAD"))
    private void sendTrackedObjectiveRemovalPackets(ScoreboardObjective objective, CallbackInfo ci) {
        for (ServerPlayerEntity player : this.server.getPlayerManager().getPlayerList()) {
            StatTracker tracker = (StatTracker) player;
            ScoreboardDisplaySlot slot = tracker.technicalToolbox$GetObjectiveSlot(objective);
            if (slot != null) {
                tracker.technicalToolbox$UpdateSlot(slot, null);
                player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(slot, null));
                if (!this.objectives.contains(objective)) {
                    player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, 1));
                }
            }
        }
    }

    @Inject(method = "updateScore", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ServerScoreboard;runUpdateListeners()V"))
    private void updateSubscribedScores(ScoreHolder scoreHolder, ScoreboardObjective objective, ScoreboardScore score, CallbackInfo ci) {
        for (ServerPlayerEntity player : this.server.getPlayerManager().getPlayerList()) {
            if (((StatTracker) player).technicalToolbox$HasDisplayedObjective(objective)) {
                player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(scoreHolder.getNameForScoreboard(), objective.getName(),
                        score.getScore(), Optional.ofNullable(score.getDisplayText()), Optional.ofNullable(score.getNumberFormat())));
            }
        }
    }

}
