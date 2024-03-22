package com.birblett.mixin.mechanic;

import com.birblett.util.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Re-introduces update skipping if config option is enabled
 */
@Mixin(RedstoneWireBlock.class)
public class RedstoneWireBlockMixin {

    @ModifyExpressionValue(method = "Lnet/minecraft/block/RedstoneWireBlock;getRenderConnectionType(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Z)Lnet/minecraft/block/enums/WireConnection;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getBlock()Lnet/minecraft/block/Block;"))
    private Block returnAirIfSkipping(Block b) {
        return (Boolean) ConfigOptions.MECHANIC_UPDATE_SKIPPING.value() ? Blocks.AIR : b;
    }

}
