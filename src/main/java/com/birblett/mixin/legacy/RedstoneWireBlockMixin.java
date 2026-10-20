package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Re-introduces update skipping if config option is enabled; see {@link ConfigOptions#LEGACY_TRAPDOOR_UPDATE_SKIPPING}
 */
@Mixin(RedStoneWireBlock.class)
public class RedstoneWireBlockMixin {

    @ModifyExpressionValue(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;"))
    private Block returnAirIfSkipping(Block b) {
        return ConfigOptions.LEGACY_TRAPDOOR_UPDATE_SKIPPING.val() ? Blocks.AIR : b;
    }

    @ModifyExpressionValue(method = "updateShape", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;canSurviveOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean returnFalseIfSkipping(boolean b) {
        return ConfigOptions.LEGACY_TRAPDOOR_UPDATE_SKIPPING.val() || b;
    }

}
