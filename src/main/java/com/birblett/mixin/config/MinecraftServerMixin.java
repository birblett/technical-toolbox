package com.birblett.mixin.config;

import com.birblett.TechnicalToolbox;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void serverLoaded(CallbackInfo ci) {
        TechnicalToolbox.CONFIG_MANAGER.onServerOpen((MinecraftServer) (Object) this);
        TechnicalToolbox.ALIAS_MANAGER.onServerOpen((MinecraftServer) (Object) this);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void serverStopped(CallbackInfo ci) {
        TechnicalToolbox.CONFIG_MANAGER.onServerClose();
        TechnicalToolbox.ALIAS_MANAGER.onServerClose();
    }

}
