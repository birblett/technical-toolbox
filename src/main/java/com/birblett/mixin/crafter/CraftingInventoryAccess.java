package com.birblett.mixin.crafter;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Allows for modifying internal stack of CraftingInventory
 */
@Mixin(CraftingInventory.class)
public interface CraftingInventoryAccess {

    @Mutable @Accessor("stacks")
    void setStacks(DefaultedList<ItemStack> stacks);

    @Accessor("stacks")
    DefaultedList<ItemStack> getStacks();

}
