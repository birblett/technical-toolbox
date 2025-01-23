package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Re-introduces old tripwire update logic if config option is enabled; see {@link ConfigOptions#LEGACY_TRIPWIRE_HOOK}
 */
@Mixin(TripwireHookBlock.class)
public abstract class TripwireHookBlockMixin {

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z", ordinal = 2))
    private static boolean legacyTripwireHook(boolean original) {
        return ConfigOptions.LEGACY_TRIPWIRE_HOOK.val() || original;
    }

}
