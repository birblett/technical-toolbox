package com.birblett.mixin;

import com.birblett.TechnicalToolbox;
import com.birblett.lib.CrafterInterface;
import com.birblett.util.config.ConfigHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Extends dispenser block entities to be able to perform crafter logic, most ported from snapshots with minor tweaks
 * for compatibility
 */
@Mixin(DispenserBlockEntity.class)
public abstract class DispenserBlockEntityMixin extends LootableContainerBlockEntity implements CrafterInterface {

    @Shadow private DefaultedList<ItemStack> inventory;

    protected DispenserBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public List<ItemStack> getInputStacks() {
        return this.inventory;
    }

    @Override
    public void provideRecipeInputs(RecipeMatcher finder) {
        for (ItemStack itemStack : this.getInvStackList()) {
            if (ConfigHelper.crafterDisabled().test(itemStack)) {
                finder.addUnenchantedInput(itemStack);
            }
        }
    }

    @Override
    public boolean isSlotDisabled(int slot) {
        if (slot >= 0 && slot < 9) {
            return ConfigHelper.crafterDisabled().test(this.getStack(slot));
        }
        return false;
    }

    @Override
    public boolean betterSlotExists(int count, ItemStack stack, int slot) {
        for (int i = slot + 1; i < 9; ++i) {
            ItemStack itemStack;
            if (this.isSlotDisabled(i) || !(itemStack = this.getStack(i)).isEmpty() && (itemStack.getCount() >= count ||
                    !ItemStack.areItemsEqual(itemStack, stack))) continue;
            return true;
        }
        return false;
    }

    @Override
    public int getComparatorOutput() {
        int i = 0;
        for (int j = 0; j < this.size(); ++j) {
            ItemStack itemStack = this.getStack(j);
            if (itemStack.isEmpty() && !this.isSlotDisabled(j)) continue;
            ++i;
        }
        return i;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (this.isSlotDisabled(slot)) {
            return false;
        }
        ItemStack itemStack = this.getInputStacks().get(slot);
        int i = itemStack.getCount();
        if (i >= itemStack.getMaxCount()) {
            return false;
        }
        if (itemStack.isEmpty()) {
            return true;
        }
        return !this.betterSlotExists(i, itemStack, slot);
    }

}
