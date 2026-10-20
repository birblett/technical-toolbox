package com.birblett.mixin.command.camera;

import com.birblett.accessor.command.camera.CameraInterface;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.TextUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

/**
 * Handle some camera-related configured functionalities
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    /**
     * Forcibly switches player out of camera mode on disconnect
     */
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void disableCameraOnDisconnect(DisconnectionDetails reason, CallbackInfo ci) {
        if (((CameraInterface) this.player).technicalToolbox$IsCamera()) {
            ((CameraInterface) this.player).technicalToolbox$SwapCameraMode(false);
        }
    }

    /**
     * Handles whether player can teleport or not in camera mode, as well as relevant logging
     */
    @Inject(method = "handleTeleportToEntityPacket", at = @At(target = "Lnet/minecraft/server/level/ServerPlayer;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
            value = "INVOKE"), locals = LocalCapture.CAPTURE_FAILSOFT, cancellable = true)
    protected void disableCamTeleport(ServerboundTeleportToEntityPacket packet, CallbackInfo ci, Iterator<ServerLevel> var2, ServerLevel serverWorld, Entity entity) {
        if (((CameraInterface) this.player).technicalToolbox$IsCamera() && !ConfigOptions.CAMERA_CAN_TELEPORT.val()) {
            this.player.displayClientMessage(TextUtils.formattable("Teleportation is disabled in camera mode"), true);
            ci.cancel();
        } else if (((CameraInterface) this.player).technicalToolbox$IsCamera() && ConfigOptions.CAMERA_CONSOLE_LOGGING.val().equals("spectate")) {
            this.player.level().getServer().sendSystemMessage(TextUtils.formattable("[Camera Mode] " + this.player.getScoreboardName() +
                    " teleported to " + entity.getScoreboardName()));
        }
    }

}
