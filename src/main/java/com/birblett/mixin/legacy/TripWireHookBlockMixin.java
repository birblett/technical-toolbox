package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.TripWireHookBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Re-introduces old tripwire update logic if config option is enabled; see {@link ConfigOptions#LEGACY_TRIPWIRE_HOOK}
 */
@Mixin(TripWireHookBlock.class)
public abstract class TripWireHookBlockMixin {

    @ModifyExpressionValue(method = "calculateState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;", ordinal = 2))
    private static Comparable<Boolean> legacyTripwireHook(Comparable<Boolean> original) {
        if (ConfigOptions.LEGACY_TRIPWIRE_HOOK.val()) {
            return true;
        }
        return original;
    }

}
