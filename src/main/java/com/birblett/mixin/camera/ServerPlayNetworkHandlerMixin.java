package com.birblett.mixin.camera;

import com.birblett.impl.config.ConfigOptions;
import com.birblett.lib.camera.CameraInterface;
import com.birblett.util.TextUtils;
import net.minecraft.entity.Entity;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

/**
 * Handle some camera-related configured functionalities
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;

    /**
     * Forcibly switches player out of camera mode on disconnect
     */
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void disableCameraOnDisconnect(DisconnectionInfo reason, CallbackInfo ci) {
        if (((CameraInterface) this.player).isCamera()) {
            ((CameraInterface) this.player).swapCameraMode(false);
        }
    }

    /**
     * Handles whether player can teleport or not in camera mode, as well as relevant logging
     */
    @Inject(method = "onSpectatorTeleport", at = @At(target = "Lnet/minecraft/server/network/ServerPlayerEntity;teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V",
                    value = "INVOKE"), locals = LocalCapture.CAPTURE_FAILSOFT, cancellable = true)
    protected void disableCamTeleport(SpectatorTeleportC2SPacket packet, CallbackInfo ci, Iterator<ServerWorld> var2, ServerWorld serverWorld, Entity entity) {
        if (((CameraInterface) this.player).isCamera() && !ConfigOptions.CAMERA_CAN_TELEPORT.getBool()) {
            this.player.sendMessage(TextUtils.formattable("Teleportation is " +
                    "disabled in camera mode"), true);
            ci.cancel();
        }
        else if (((CameraInterface) this.player).isCamera() && ConfigOptions.CAMERA_CONSOLE_LOGGING.getString()
                .equals("spectate")
                && this.player.getServer() != null) {
            this.player.getServer().sendMessage(TextUtils.formattable("[Camera Mode] " + this.player
                    .getNameForScoreboard() + " teleported to " + entity.getNameForScoreboard()));
        }
    }

}
