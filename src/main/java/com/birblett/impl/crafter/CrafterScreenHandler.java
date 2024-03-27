package com.birblett.impl.crafter;

import com.birblett.lib.crafter.CrafterInterface;
import com.birblett.mixin.crafter.CraftingInventoryAccess;
import com.birblett.util.TextUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * Creates a pseudo-crafting table gui for crafters, mostly copied from {@link net.minecraft.screen.CraftingScreenHandler}
 */
public class CrafterScreenHandler extends AbstractRecipeScreenHandler<RecipeInputInventory> {

    private final DispenserBlockEntity blockEntity;
    private final RecipeInputInventory input = new CraftingInventory(this, 3, 3);
    private final CraftingResultInventory result = new CraftingResultInventory();
    private final PlayerEntity player;

    /**
     * Main difference from regular crafting screen is that it holds a reference to the crafter inventory instead of
     * having its own like a normal crafting table
     */
    public CrafterScreenHandler(int syncId, PlayerInventory playerInventory, DispenserBlockEntity blockEntity) {
        super(ScreenHandlerType.CRAFTING, syncId);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
        // make the crafting inventory hold a reference to the crafter inventory instead
        ((CraftingInventoryAccess) this.input).setStacks(((CrafterInterface) this.blockEntity).getInventory());
        int i, j;
        this.addSlot(new CraftingResultSlot(playerInventory.player, this.input, this.result, 0, 124, 35));
        for (i = 0; i < 3; ++i) {
            for (j = 0; j < 3; ++j) {
                this.addSlot(new Slot(this.input, j + i * 3, 30 + j * 18, 17 + i * 18));
            }
        }
        for (i = 0; i < 3; ++i) {
            for (j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public int getCraftingResultSlotIndex() {
        return 0;
    }

    @Override
    public int getCraftingWidth() {
        return this.input.getWidth();
    }

    @Override
    public int getCraftingHeight() {
        return this.input.getHeight();
    }

    @Override
    public int getCraftingSlotCount() {
        return 10;
    }

    @Override
    public RecipeBookCategory getCategory() {
        return RecipeBookCategory.CRAFTING;
    }

    @Override
    public boolean canInsertIntoSlot(int index) {
        return index != this.getCraftingResultSlotIndex();
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return slot.inventory != this.result && super.canInsertIntoSlot(stack, slot);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.blockEntity.canPlayerUse(player);
    }

    @Override
    public boolean matches(Recipe<? super RecipeInputInventory> recipe) {
        return recipe.matches(this.input, this.player.getWorld());
    }

    @Override
    public void populateRecipeFinder(RecipeMatcher finder) {
        this.input.provideRecipeInputs(finder);
    }

    private Optional<CraftingRecipe> getCurrentRecipe() {
        World world = this.blockEntity.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        return world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, this.input, world);
    }

    /**
     * Force-update player screens with crafted items
     */
    @Override
    public void onContentChanged(Inventory inventory) {
        if (this.player instanceof ServerPlayerEntity && this.blockEntity.getWorld() != null) {
            ServerPlayNetworkHandler handler = ((ServerPlayerEntity) this.player).networkHandler;
            CraftingInventoryAccess c = (CraftingInventoryAccess) this.input;
            // same dumb hack from dispenserblockmixin, remove disabled slot items
            ItemStack[] temp = new ItemStack[9];
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = c.getStacks().get(slot);
                if (stack.isOf(Items.BARRIER)) {
                    temp[slot] = stack;
                    c.getStacks().set(slot, ItemStack.EMPTY);
                }
            }
            // get possible recipe
            Optional<CraftingRecipe> maybeRecipe = this.getCurrentRecipe();
            // restore disabled slot items
            for (int slot = 0; slot < 9; slot++) {
                if (temp[slot] != null) {
                    c.getStacks().set(slot, temp[slot]);
                }
            }
            this.result.setStack(0, maybeRecipe.isPresent() ? maybeRecipe.get().getOutput(this.player.getWorld()
                    .getRegistryManager()) : ItemStack.EMPTY);
            this.blockEntity.getWorld().updateComparators(this.blockEntity.getPos(), Blocks.DISPENSER);
            handler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.syncId, 0, 0, this.result.getStack(0)));
        }
    }

    @Override
    public void clearCraftingSlots() {
        this.input.clear();
        this.result.clear();
    }

    /**
     * Additionally removes this handler from its parent dispenser block entity
     */
    @Override
    public void onClosed(PlayerEntity player) {
        ((CrafterInterface) this.blockEntity).getViewers().remove(this);
        super.onClosed(player);
    }

    /**
     * Regular quickmove logic but with all cases related to the crafting slot itself removed
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot2 = this.slots.get(slot);
        if (slot2.hasStack()) {
            ItemStack itemStack2 = slot2.getStack();
            itemStack = itemStack2.copy();
            if (slot >= 10 && slot < 46 ? !this.insertItem(itemStack2, 1, 10, false) &&
                    (slot < 37 ? !this.insertItem(itemStack2, 37, 46, false) : !this.insertItem(itemStack2, 10, 37, false)) : !this.insertItem(itemStack2, 10, 46, false)) {
                return ItemStack.EMPTY;
            }
            if (itemStack2.isEmpty()) {
                slot2.setStack(ItemStack.EMPTY);
            }
            else {
                slot2.markDirty();
            }
            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot2.onTakeItem(player, itemStack2);
            if (slot == 0) {
                player.dropItem(itemStack2, false);
            }
        }
        return itemStack;
    }

    /**
     * Toggles a given slot between a disabled barrier item and air
     */
    public boolean toggleSlot(int slot) {
        ItemStack stack = this.blockEntity.getStack(slot);
        if (stack.isOf(Items.BARRIER)) {
            if (this.getCursorStack().isEmpty()) {
                this.blockEntity.setStack(slot, ItemStack.EMPTY);
                this.blockEntity.markDirty();
            }
            return true;
        }
        else if (this.getCursorStack().isEmpty() && stack.isEmpty()) {
            ItemStack disabled = Items.BARRIER.getDefaultStack().setCustomName(TextUtils
                    .formattable("Disabled").setStyle(Style.EMPTY.withItalic(false).withColor(Formatting.WHITE)));
            disabled.getOrCreateNbt().putInt("CustomModelData", 13579);
            this.blockEntity.setStack(slot, disabled);
            this.blockEntity.markDirty();
            return true;
        }
        return false;
    }

}