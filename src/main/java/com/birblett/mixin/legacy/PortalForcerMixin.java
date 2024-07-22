package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOption;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.state.property.Properties;
import net.minecraft.world.dimension.PortalForcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bypasses conditional check on {@link Properties#HORIZONTAL_AXIS} if {@link ConfigOption#LEGACY_POI_PROPERTY_CHECK}
 * enabled
 */
@Mixin(PortalForcer.class)
public class PortalForcerMixin {

    @ModifyExpressionValue(method = "method_61028", at = @At(value = "INVOKE", target = "net/minecraft/block/BlockState.contains(Lnet/minecraft/state/property/Property;)Z"))
    private boolean ignoreProperty(boolean b) {
        return b || ConfigOption.LEGACY_POI_PROPERTY_CHECK.val();
    }


}
