package com.birblett.mixin.server;

import com.birblett.impl.command.server.server_manager.ServerManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Config and alias initialize and cleanup.
 */
@Environment(EnvType.SERVER)
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Unique
    private boolean configurable = false;

    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void serverLoaded(CallbackInfo ci) {
        this.configurable = true;
        ServerManager.onServerOpen();
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void serverStopped(CallbackInfo ci) {
        if (this.configurable) {
            ServerManager.onServerClose();
            this.configurable = false;
        }
    }

}
