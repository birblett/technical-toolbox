package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Enables pre-1.21 end platform generation mechanics. See {@link ConfigOptions#LEGACY_END_PLATFORM}
 */
@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {

    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/feature/EndPlatformFeature;createEndPlatform(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Z)V"))
    private void legacyEndPlatformLogic(ServerLevelAccessor world, BlockPos blockPos, boolean breakBlocks, Operation<Void> original) {
        if (ConfigOptions.LEGACY_END_PLATFORM.val()) {
            BlockPos spawnPos = ServerLevel.END_SPAWN_POINT;
            int i = spawnPos.getX();
            int j = spawnPos.getY() - 2;
            int k = spawnPos.getZ();
            for (BlockPos pos : BlockPos.betweenClosed(i - 2, j + 1, k - 2, i + 2, j + 3, k + 2)) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (BlockPos pos : BlockPos.betweenClosed(i - 2, j, k - 2, i + 2, j, k + 2)) {
                world.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else {
            original.call(world, blockPos, breakBlocks);
        }
    }

}
