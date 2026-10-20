package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.TripWireHookBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Re-introduces old tripwire update logic if config option is enabled; see {@link ConfigOptions#LEGACY_TRIPWIRE_HOOK}
 */
@Mixin(TripWireHookBlock.class)
public abstract class TripwireHookBlockMixin {

    @ModifyExpressionValue(method = "calculateState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 2))
    private static boolean legacyTripwireHook(boolean original) {
        return ConfigOptions.LEGACY_TRIPWIRE_HOOK.val() || original;
    }

}
