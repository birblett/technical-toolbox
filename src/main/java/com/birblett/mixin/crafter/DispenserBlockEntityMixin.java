package com.birblett.mixin.crafter;

import com.birblett.impl.crafter.CrafterScreenHandler;
import com.birblett.lib.crafter.CrafterInterface;
import com.birblett.util.Constant;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Extends dispenser block entities to be able to perform crafter logic, most ported from snapshots with extra tweaks
 * for compatibility. Implements SidedInventory for Lithium compatibility.
 */
@Mixin(DispenserBlockEntity.class)
public abstract class DispenserBlockEntityMixin extends LootableContainerBlockEntity implements CrafterInterface, SidedInventory {

    @Shadow private DefaultedList<ItemStack> inventory;
    @Unique private final int[] AVAILABLE_SLOTS = IntStream.range(0, 9).toArray();
    @Unique private int craftingTicks = 0;
    @Unique private final List<ScreenHandler> viewers = new ArrayList<>();

    protected DispenserBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
        this.inventory = DefaultedList.ofSize(9, ItemStack.EMPTY);
    }

    @Override
    public boolean isCrafter() {
        return this.world != null && this.world.getBlockState(this.pos).isOf(Blocks.DROPPER) && this.world.getBlockState(this.pos).get(Constant.IS_CRAFTER);
    }

    @Override
    public DefaultedList<ItemStack> getInventory() {
        return this.inventory;
    }

    @Override
    public List<ScreenHandler> getViewers() {
        return this.viewers;
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
    public int[] getAvailableSlots(Direction side) {
        return this.AVAILABLE_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (this.isCrafter()) {
            return !this.isSlotDisabled(slot);
        }
        return true;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        if (this.isCrafter()) {
            return !this.isSlotDisabled(slot);
        }
        return true;
    }

    @Override
    public void provideRecipeInputs(RecipeMatcher finder) {
        for (ItemStack itemStack : this.getInvStackList()) {
            if (!itemStack.isOf(Items.BARRIER)) {
                finder.addUnenchantedInput(itemStack);
            }
        }
    }

    @Override
    public boolean isSlotDisabled(int slot) {
        if (this.isCrafter()) {
            return this.getStack(slot).isOf(Items.BARRIER);
        }
        return false;
    }

    @Override
    public int craftingTicks() {
        return this.craftingTicks;
    }

    @Override
    public void setCraftingTicks(int i) {
        this.craftingTicks = i;
    }

    /**
     * Mainly for hoppers and droppers
     */
    @Override
    public boolean betterSlotExists(int count, ItemStack stack, int slot) {
        if (this.isCrafter()) {
            for (int i = slot; i < 9; ++i) {
                ItemStack itemStack;
                if (this.isSlotDisabled(i) || !(itemStack = this.getStack(i)).isEmpty() && (itemStack.getCount() >= count ||
                        !ItemStack.areItemsEqual(itemStack, stack))) continue;
                return true;
            }
        }
        return false;
    }

    /**
     * Overrides comparator logic, conditional check done in {@link DispenserBlockMixin#getCrafterComparatorOutput}
     */
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

    /**
     * From vanilla snapshots, checks if slot can be extracted from or inserted to
     */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (this.isCrafter()) {
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
        return super.isValid(slot, stack);
    }

    /**
     * Additionally updates all viewers, hopefully this can address multiplayer-related bugs in advance
     */
    @Override
    public void markDirty() {
        for (ScreenHandler handler : viewers) handler.onContentChanged(this);
        super.markDirty();
    }

    /**
     * Creates {@link CrafterScreenHandler}s, and also adds them to internal screenhandler tracking
     */
    @Inject(method = "createScreenHandler", at = @At("HEAD"), cancellable = true)
    private void createCraftingScreenHandler(int syncId, PlayerInventory playerInventory, CallbackInfoReturnable<ScreenHandler> cir) {
        if (this.isCrafter()) {
            ScreenHandler s = new CrafterScreenHandler(syncId, playerInventory, (DispenserBlockEntity) (Object) this);
            this.viewers.add(s);
            cir.setReturnValue(s);
        }
    }

}
