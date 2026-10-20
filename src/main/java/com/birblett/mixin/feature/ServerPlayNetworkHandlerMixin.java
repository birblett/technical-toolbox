package com.birblett.mixin.feature;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Increase player velocity thresholds before correcting packets are sent. See {@link ConfigOptions#FEATURE_SPEED_LIMIT},
 * {@link ConfigOptions#FEATURE_SPEED_LIMIT_ELYTRA}, and {@link ConfigOptions#FEATURE_SPEED_LIMIT_VEHICLE}
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandlerMixin {

    /**
     * Override the regular speed limit, default 100.
     */
    @ModifyExpressionValue(method = "handleMovePlayer", at = @At(value = "CONSTANT", args = "floatValue=100.0f"))
    private float speedLimit(float f) {
        return ConfigOptions.FEATURE_SPEED_LIMIT.val();
    }

    /**
     * Override the elytra speed limit, default 300.
     */
    @ModifyExpressionValue(method = "handleMovePlayer", at = @At(value = "CONSTANT", args = "floatValue=300.0f"))
    private float elytraSpeedLimit(float f) {
        return ConfigOptions.FEATURE_SPEED_LIMIT_ELYTRA.val();
    }

    /**
     * Override the vehicle speed limit, default 100.
     */
    @ModifyExpressionValue(method = "handleMoveVehicle", at = @At(value = "CONSTANT", args = "doubleValue=100.0"))
    private double disableCameraOnDisconnect(double d) {
        return ConfigOptions.FEATURE_SPEED_LIMIT_VEHICLE.val();
    }

}
