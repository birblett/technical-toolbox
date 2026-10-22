package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bypasses conditional check on {@link BlockStateProperties#HORIZONTAL_AXIS} if {@link ConfigOptions#LEGACY_POI_PROPERTY_CHECK}
 * enabled
 */
@Mixin(PortalForcer.class)
public class PortalForcerMixin {

    @ModifyExpressionValue(method = "lambda$findClosestPortalPosition$1(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "INVOKE", target = "net/minecraft/world/level/block/state/BlockState.hasProperty(Lnet/minecraft/world/level/block/state/properties/Property;)Z"))
    private boolean ignoreProperty(boolean b) {
        return b || ConfigOptions.LEGACY_POI_PROPERTY_CHECK.val();
    }


}
