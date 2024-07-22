package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOption;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Re-introduces update skipping if config option is enabled; see {@link ConfigOption#LEGACY_UPDATE_SKIPPING}
 */
@Mixin(RedstoneWireBlock.class)
public class RedstoneWireBlockMixin {

    @ModifyExpressionValue(method = "getRenderConnectionType(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Z)Lnet/minecraft/block/enums/WireConnection;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getBlock()Lnet/minecraft/block/Block;"))
    private Block returnAirIfSkipping(Block b) {
        return ConfigOption.LEGACY_UPDATE_SKIPPING.val() ? Blocks.AIR : b;
    }

}
