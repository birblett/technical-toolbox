package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOption;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Enables pre-1.21 end platform generation mechanics. See {@link ConfigOption#LEGACY_END_PLATFORM}
 */
@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {

    @WrapOperation(method = "createTeleportTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/feature/EndPlatformFeature;generate(Lnet/minecraft/world/ServerWorldAccess;Lnet/minecraft/util/math/BlockPos;Z)V"))
    private void legacyEndPlatformLogic(ServerWorldAccess world, BlockPos blockPos, boolean breakBlocks, Operation<Void> original) {
        if (ConfigOption.LEGACY_END_PLATFORM.val() ) {
            BlockPos spawnPos = ServerWorld.END_SPAWN_POS;
            int i = spawnPos.getX();
            int j = spawnPos.getY() - 2;
            int k = spawnPos.getZ();
            BlockPos.iterate(i - 2, j + 1, k - 2, i + 2, j + 3, k + 2).forEach(pos ->
                    world.toServerWorld().setBlockState(pos, Blocks.AIR.getDefaultState()));
            BlockPos.iterate(i - 2, j, k - 2, i + 2, j, k + 2).forEach(pos -> world
                    .toServerWorld().setBlockState(pos, Blocks.OBSIDIAN.getDefaultState()));
        }
        else {
            original.call(world, blockPos, breakBlocks);
        }
    }

}
