package com.birblett.compat.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import com.birblett.compat.MixinPlugin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@MixinPlugin.Requires({"carpet", "1.4.147+v240613"})
@Mixin(value = MinecraftClient.class, priority = 2000)
public class Carpet_MinecraftMixin {

    @TargetHandler(mixin = "carpet.mixins.MinecraftMixin", name = "onCloseGame", prefix = "handler")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lcarpet/network/CarpetClient;disconnect()V"))
    private void preventCarpetDisconnectCrash(Operation<Void> operation,  Screen screen, CallbackInfo ci) {
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    private void callDisconnect(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        Carpet_CarpetClientAccessor.disconnect();
    }

}
