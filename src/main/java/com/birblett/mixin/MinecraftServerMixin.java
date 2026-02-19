package com.birblett.mixin;

import com.birblett.TechnicalToolbox;
import com.birblett.util.ServerUtil;
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

    @Unique
    private boolean configurable = false;

    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void serverLoaded(CallbackInfo ci) {
        this.configurable = true;
        MinecraftServer server = (MinecraftServer) (Object) this;
        TechnicalToolbox.CONFIG_MANAGER.onServerOpen(server);
        TechnicalToolbox.ALIAS_MANAGER.onServerOpen(server);
        ServerUtil.refreshCommandTree(server);
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void serverStopped(CallbackInfo ci) {
        if (this.configurable) {
            MinecraftServer server = (MinecraftServer) (Object) this;
            TechnicalToolbox.CONFIG_MANAGER.onServerClose(server);
            TechnicalToolbox.ALIAS_MANAGER.onServerClose(server);
            this.configurable = false;
        }
    }

}
