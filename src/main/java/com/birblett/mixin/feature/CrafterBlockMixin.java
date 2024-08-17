package com.birblett.mixin.feature;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CrafterBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows for adjustment of crafter cooldown and toggling quasi power. See
 * {@link ConfigOptions#FEATURE_CRAFTER_COOLDOWN} and {@link ConfigOptions#FEATURE_CRAFTER_QUASI_POWER}.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

    @Shadow protected abstract void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random);

    @WrapOperation(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;scheduleBlockTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;I)V"))
    private void crafterDelay(World world, BlockPos pos, Block block, int i, Operation<Void> original) {
        if (world instanceof ServerWorld serverWorld) {
            int cd = ConfigOptions.FEATURE_CRAFTER_COOLDOWN.val();
            if (cd == 0) {
                this.scheduledTick(world.getBlockState(pos), serverWorld, pos, world.getRandom());
            } else {
                original.call(world, pos, block, cd);
            }
        }
    }

    @ModifyExpressionValue(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isReceivingRedstonePower(Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean allowQuasi(boolean b, @Local(argsOnly = true) World world, @Local(ordinal = 0, argsOnly = true) BlockPos pos) {
        if (ConfigOptions.FEATURE_CRAFTER_QUASI_POWER.val()) {
            return b || world.isReceivingRedstonePower(pos.up());
        }
        return b;
    }

}
