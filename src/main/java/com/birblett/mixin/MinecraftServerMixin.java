package com.birblett.mixin;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.command.stat.TrackedStatManager;
import com.birblett.util.ServerUtil;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Config and alias initialize and cleanup.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Shadow @Final private ServerScoreboard scoreboard;
    @Unique private boolean configurable = false;

    @Inject(method = "loadWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;prepareStartRegion(Lnet/minecraft/server/WorldGenerationProgressListener;)V"))
    private void serverLoaded(CallbackInfo ci) {
        this.configurable = true;
        MinecraftServer server = (MinecraftServer) (Object) this;
        TechnicalToolbox.CONFIG_MANAGER.onServerOpen(server);
        TechnicalToolbox.ALIAS_MANAGER.onServerOpen(server);
        TrackedStatManager.TRACKED_COMPOUNDS.clear();
        TrackedStatManager.TRACKED_STATS.clear();
        TrackedStatManager.loadTrackedStats(server, ServerUtil.getGlobalToolboxPath(server, ""), true);
        TrackedStatManager.loadTrackedStats(server, ServerUtil.getToolboxPath(server, ""), false);
        for (ScoreboardObjective objective : scoreboard.getObjectives()) {
            if (objective.getName().startsWith(TrackedStatManager.COMPOUND_STAT_PREFIX) && TrackedStatManager.getCompoundStat(
                    objective.getName().replaceFirst(TrackedStatManager.COMPOUND_STAT_REGEX, "")) == null) {
                scoreboard.removeObjective(objective);
            }
        }
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void serverStopped(CallbackInfo ci) {
        if (this.configurable) {
            MinecraftServer server = (MinecraftServer) (Object) this;
            TechnicalToolbox.CONFIG_MANAGER.onServerClose(server);
            TechnicalToolbox.ALIAS_MANAGER.onServerClose(server);
            TrackedStatManager.saveTrackedStats(server);
            this.configurable = false;
        }
    }

}
