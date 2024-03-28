package com.birblett.mixin.copper_bulb;

import com.birblett.util.Constant;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Oxidizable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Manual overrides for redstone lamp oxidation
 */
@Mixin(Oxidizable.class)
public interface OxidizableMixin {

    /**
     * Get decreased bulb oxidation for each level of oxidation above 1
     */
    @Inject(method = "getDecreasedOxidationState", at = @At("HEAD"), cancellable = true)
    private static void copperBulbDecreasedOxidationState(BlockState state, CallbackInfoReturnable<Optional<BlockState>> cir) {
        if (state.isOf(Blocks.REDSTONE_LAMP)) {
            Optional<BlockState> optionalState = Optional.empty();
            if (state.get(Constant.WAXED)) {
                cir.setReturnValue(optionalState);
                return;
            }
            int oxidation = state.get(Constant.OXIDATION);
            switch (oxidation) {
                case 2, 3, 4 -> optionalState = Optional.of(state.with(Constant.OXIDATION, oxidation - 1));
            }
            cir.setReturnValue(optionalState);
        }
    }

    /**
     * Get increased bulb oxidation for each level of oxidatio below 4
     */
    @Inject(method = "getDegradationResult", at = @At("HEAD"), cancellable = true)
    private void copperBulbDegradationResult(BlockState state, CallbackInfoReturnable<Optional<BlockState>> cir) {
        if (state.isOf(Blocks.REDSTONE_LAMP)) {
            Optional<BlockState> optionalState = Optional.empty();
            if (state.get(Constant.WAXED)) {
                cir.setReturnValue(optionalState);
                return;
            }
            int oxidation = state.get(Constant.OXIDATION);
            switch (oxidation) {
                case 1, 2, 3 -> optionalState = Optional.of(state.with(Constant.OXIDATION, oxidation + 1));
            }
            cir.setReturnValue(optionalState);
        }
    }

    /**
     * For lightning de-oxidation calculation
     */
    @Inject(method = "getUnaffectedOxidationState", at = @At("HEAD"), cancellable = true)
    private static void copperBulbUnaffectedOxidationState(BlockState state, CallbackInfoReturnable<BlockState> cir) {
        if (state.isOf(Blocks.REDSTONE_LAMP)) {
            if (state.get(Constant.WAXED)) {
                cir.setReturnValue(state);
                return;
            }
            switch (state.get(Constant.OXIDATION)) {
                case 2, 3, 4 -> state = state.with(Constant.OXIDATION, 1);
            }
            cir.setReturnValue(state);
        }
    }

}
