package com.birblett.mixin.config;

import com.birblett.TechnicalToolbox;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Config and alias initialize and cleanup.
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Unique private boolean configurable = false;

    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void serverLoaded(CallbackInfo ci) {
        this.configurable = true;
        TechnicalToolbox.CONFIG_MANAGER.onServerOpen((MinecraftServer) (Object) this);
        TechnicalToolbox.ALIAS_MANAGER.onServerOpen((MinecraftServer) (Object) this);
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void serverStopped(CallbackInfo ci) {
        if ((MinecraftServer) (Object) this != null && this.configurable) {
            TechnicalToolbox.CONFIG_MANAGER.onServerClose((MinecraftServer) (Object) this);
            TechnicalToolbox.ALIAS_MANAGER.onServerClose((MinecraftServer) (Object) this);
            this.configurable = false;
        }
    }

}
