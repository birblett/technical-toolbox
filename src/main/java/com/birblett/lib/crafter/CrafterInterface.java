package com.birblett.lib.crafter;

import com.birblett.util.Constant;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Crafter interface for methods that need to be called in {@link com.birblett.mixin.crafter.DispenserBlockMixin} and
 * {@link com.birblett.impl.crafter.CrafterScreenHandler}
 */
public interface CrafterInterface extends RecipeInputInventory {

    boolean isCrafter();
    int craftingTicks();
    void setCraftingTicks(int i);
    boolean isSlotDisabled(int slot);
    boolean betterSlotExists(int count, ItemStack stack, int slot);
    int getComparatorOutput();
    DefaultedList<ItemStack> getInventory();
    List<ScreenHandler> getViewers();

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

    /**
     * Removes and stores marker items in an array
     * @return Array of marker items
     */
    default ItemStack[] removeMarkers() {
        ItemStack[] temp = new ItemStack[9];
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = this.getStack(slot);
            if (stack.isOf(Items.BARRIER)) {
                temp[slot] = stack;
                this.setStack(slot, ItemStack.EMPTY);
            }
        }
        return temp;
    }

    /**
     * Restores marker items from an array
     * @param temp should be output from removeMarkers
     */
    default void restoreMarkers(ItemStack[] temp) {
        for (int slot = 0; slot < 9; slot++) {
            if (temp[slot] != null) {
                this.setStack(slot, temp[slot]);
            }
        }
    }

}
