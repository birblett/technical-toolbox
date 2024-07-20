package com.birblett.mixin.fix;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Velocity-related fixes for rubberbanding
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    /**
     * Override the regular speed limit, default 100.
     */
    @ModifyExpressionValue(method = "onPlayerMove", at = @At(value = "CONSTANT", args = "floatValue=100.0f"))
    private float speedLimit(float f) {
        return ConfigOptions.FIX_SPEED_LIMIT.getFloat();
    }

    /**
     * Override the elytra speed limit, default 300.
     */
    @ModifyExpressionValue(method = "onPlayerMove", at = @At(value = "CONSTANT", args = "floatValue=300.0f"))
    private float elytraSpeedLimit(float f) {
        return ConfigOptions.FIX_ELYTRA_SPEED_LIMIT.getFloat();
    }

    /**
     * Override the vehicle speed limit, default 100.
     */
    @ModifyExpressionValue(method = "onVehicleMove", at = @At(value = "CONSTANT", args = "doubleValue=100.0"))
    private double disableCameraOnDisconnect(double d) {
        return ConfigOptions.FIX_VEHICLE_SPEED_LIMIT.getDouble();
    }

}
