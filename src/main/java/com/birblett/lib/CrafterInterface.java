package com.birblett.lib;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;

public interface CrafterInterface extends RecipeInputInventory {

    boolean isSlotDisabled(int slot);
    boolean betterSlotExists(int count, ItemStack stack, int slot);
    int getComparatorOutput();

}
