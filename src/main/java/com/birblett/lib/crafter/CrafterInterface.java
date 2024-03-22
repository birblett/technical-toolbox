package com.birblett.lib.crafter;

import com.birblett.util.Constant;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface CrafterInterface extends RecipeInputInventory {

    boolean isCrafter();
    int craftingTicks();
    void setCraftingTicks(int i);
    boolean isSlotDisabled(int slot);
    boolean betterSlotExists(int count, ItemStack stack, int slot);
    int getComparatorOutput();

    /**
     * tickCrafting directly ported from snapshots, handles updating crafter state 6gt after activation
     */
    static <E extends BlockEntity> void tickCrafting(World world, BlockPos blockPos, BlockState blockState, E blockEntity) {
        CrafterInterface c = (CrafterInterface) blockEntity;
        if (c.isCrafter() && c.craftingTicks() > 0) {
            int i = c.craftingTicks() - 1;
            if (i < 0) {
                return;
            }
            ((CrafterInterface) blockEntity).setCraftingTicks(i);
            if (i == 0) {
                world.setBlockState(blockPos, blockState.with(Constant.IS_CRAFTING, false), Block.NOTIFY_ALL);
            }
        }
    }

}
