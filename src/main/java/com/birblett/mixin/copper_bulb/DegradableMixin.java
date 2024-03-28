package com.birblett.mixin.copper_bulb;

import com.birblett.util.Constant;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Degradable;
import net.minecraft.block.Oxidizable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Custom degradation logic for copper bulbs
 */
@Mixin(Degradable.class)
public interface DegradableMixin <T extends Enum<T>> {

    /**
     * Prevent regular redstone lamps and waxed bulbs from being factored into degradation calculation
     */
    @ModifyExpressionValue(method = "tryDegrade", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;equals(Ljava/lang/Object;)Z"))
    private boolean copperBulbState(boolean b, @Local BlockState state) {
        if (state.isOf(Blocks.REDSTONE_LAMP) && (state.get(Constant.OXIDATION) == 0 || state.get(Constant.WAXED))) {
            return true;
        }
        return b;
    }

    /**
     * Degradation outputs for different levels of oxidation
     */
    @ModifyExpressionValue(method = "tryDegrade", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Degradable;getDegradationLevel()Ljava/lang/Enum;"))
    private Enum copperBulbDegradationLevel(Enum e, @Local BlockState state) {
        if (state.isOf(Blocks.REDSTONE_LAMP)) {
            switch (state.get(Constant.OXIDATION)) {
                case 1 -> {
                    return Oxidizable.OxidationLevel.UNAFFECTED;
                }
                case 2 -> {
                    return Oxidizable.OxidationLevel.EXPOSED;
                }
                case 3 -> {
                    return Oxidizable.OxidationLevel.WEATHERED;
                }
                case 4 -> {
                    return Oxidizable.OxidationLevel.OXIDIZED;
                }
            }
        }
        return e;
    }

}
