package com.birblett.mixin.feature;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows for adjustment of crafter cooldown and toggling quasi power. See
 * {@link ConfigOptions#FEATURE_CRAFTER_COOLDOWN} and {@link ConfigOptions#FEATURE_CRAFTER_QUASI_POWER}.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

    @Shadow
    protected abstract void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random);

    @WrapOperation(method = "neighborChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;scheduleTick(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;I)V"))
    private void crafterDelay(Level world, BlockPos pos, Block block, int i, Operation<Void> original) {
        if (world instanceof ServerLevel serverWorld) {
            int cd = ConfigOptions.FEATURE_CRAFTER_COOLDOWN.val();
            if (cd == 0) {
                this.tick(world.getBlockState(pos), serverWorld, pos, world.getRandom());
            } else {
                original.call(world, pos, block, cd);
            }
        }
    }

    @ModifyExpressionValue(method = "neighborChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean allowQuasi(boolean b, @Local(argsOnly = true) Level world, @Local(ordinal = 0, argsOnly = true) BlockPos pos) {
        if (ConfigOptions.FEATURE_CRAFTER_QUASI_POWER.val()) {
            return b || world.hasNeighborSignal(pos.above());
        }
        return b;
    }

}
