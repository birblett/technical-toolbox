package com.birblett.mixin.recipe;

import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.TextUtils;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.SuspiciousStewRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Style;
import org.apache.commons.lang3.text.WordUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

/**
 * Disgusting hardcoded recipes WHY did it take til 1.21 for anything SIMILAR to nbt crafting to be added
 */
@Mixin(SuspiciousStewRecipe.class)
public class SuspiciousStewRecipeMixin {

    @Unique private int customRecipeType;
    @Unique private Item copperBulbType;
    @Unique private ItemStack temp;
    @Unique private static final Item[] CRAFTER_RECIPE = new Item[]{
            Items.IRON_INGOT,     Items.IRON_INGOT,     Items.IRON_INGOT,
            Items.IRON_INGOT,     Items.CRAFTING_TABLE, Items.IRON_INGOT,
            Items.REDSTONE,       Items.DROPPER,     Items.REDSTONE,
    };
    @Unique private static final List<Item> FULL_COPPER = Arrays.asList(Items.COPPER_BLOCK, Items.EXPOSED_COPPER,
            Items.WEATHERED_COPPER, Items.OXIDIZED_COPPER, Items.WAXED_COPPER_BLOCK, Items.WAXED_EXPOSED_COPPER,
            Items.WAXED_WEATHERED_COPPER, Items.WAXED_OXIDIZED_COPPER);

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean matchCrafter(boolean out, @Local RecipeInputInventory recipeInputInventory) {
        this.customRecipeType = 0;
        this.copperBulbType = null;
        if (!out) {
            boolean found = false;
            // crafter recipe
            if ((boolean) ConfigOptions.CRAFTER_ENABLED.value() && recipeInputInventory.size() == 9) {
                found = true;
                this.customRecipeType = 1;
                NbtCompound nbt;
                for (int i = 0; i < 9 && found; i++) {
                    ItemStack stack = recipeInputInventory.getStack(i);
                    if (stack.getItem() != SuspiciousStewRecipeMixin.CRAFTER_RECIPE[i]) {
                        found = false;
                        this.customRecipeType = 0;
                    }
                    else if (stack.getItem().equals(Items.DROPPER) && (nbt = stack.getNbt()) != null && nbt.
                            getInt("CustomModelData") == 13579) {
                        found = false;
                        this.customRecipeType = 0;
                    }
                }
            }
            if (found) {
                return true;
            }
            // copper bulb recipe
            if ((boolean) ConfigOptions.COPPER_BULB_ENABLED.value() && recipeInputInventory.size() == 9) {
                found = true;
                Item current = null;
                this.customRecipeType = 2;
                for (int i = 0; i < 9 && found; i++) {
                    ItemStack stack = recipeInputInventory.getStack(i);
                    switch (i) {
                        case 1, 3, 5 -> {
                            if (!SuspiciousStewRecipeMixin.FULL_COPPER.contains(stack.getItem()) || current != null &&
                                    current != stack.getItem()) {
                                found = false;
                                this.customRecipeType = 0;
                            }
                            this.copperBulbType = current = stack.getItem();
                        }
                        case 4 -> {
                            if (!stack.isOf(Items.BLAZE_ROD)) {
                                found = false;
                                this.customRecipeType = 0;
                            }
                        }
                        case 7 -> {
                            if (!stack.isOf(Items.REDSTONE)) {
                                found = false;
                                this.customRecipeType = 0;
                            }
                        }
                        default -> {
                            if (!stack.isEmpty()) {
                                found = false;
                                this.customRecipeType = 0;
                            }
                        }
                    }
                }
            }
            if (found) {
                return true;
            }
            // waxed bulb recipe
            this.customRecipeType = 3;
            boolean hasCopper = false;
            boolean hasWax = false;
            int count = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = recipeInputInventory.getStack(i);
                if (stack.isOf(Items.HONEYCOMB)) {
                    hasWax = true;
                    ++count;
                }
                else if (stack.isOf(Items.REDSTONE_LAMP) && stack.getNbt() != null && stack.getNbt()
                        .getInt("Oxidation") > 0) {
                    this.copperBulbType = (this.temp = stack).getItem();
                    hasCopper = true;
                    ++count;
                }
                if (count > 2) {
                    hasCopper = false;
                    break;
                }
            }
            if (hasCopper && hasWax) {
                return true;
            }
        }
        return out;
    }

    @Inject(method = "craft(Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/registry/DynamicRegistryManager;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void craftCustom(RecipeInputInventory recipeInputInventory, DynamicRegistryManager dynamicRegistryManager, CallbackInfoReturnable<ItemStack> cir) {
        switch (this.customRecipeType) {
            // crafter recipe
            case 1 -> {
                ItemStack stack = Items.DROPPER.getDefaultStack();
                stack.getOrCreateNbt().putInt("CustomModelData", 13579);
                if ((Boolean) ConfigOptions.USE_TRANSLATABLE_TEXT.value()) {
                    stack.setCustomName(TextUtils.translatable("container.crafter").setStyle(Style.EMPTY
                            .withItalic(false)));
                }
                else {
                    stack.setCustomName(TextUtils.formattable("Crafter").setStyle(Style.EMPTY.withItalic(false)));
                }
                cir.setReturnValue(stack);
            }
            // copper bulb recipe
            case 2 -> {
                ItemStack stack = Items.REDSTONE_LAMP.getDefaultStack();
                String type = this.copperBulbType.getTranslationKey().replaceFirst("_block", "");
                if ((Boolean) ConfigOptions.USE_TRANSLATABLE_TEXT.value()) {
                    stack.setCustomName(TextUtils.translatable(type + "_bulb").setStyle(Style.EMPTY
                            .withItalic(false)));
                }
                else {
                    stack.setCustomName(TextUtils.formattable(WordUtils.capitalizeFully(type.replaceFirst(
                            "block.minecraft.", "").replace('_', ' ')) + " Bulb")
                            .setStyle(Style.EMPTY.withItalic(false)));
                }
                NbtCompound nbt = stack.getOrCreateNbt();
                switch (type) {
                    case "block.minecraft.copper" -> {
                        nbt.putInt("CustomModelData", 1);
                        nbt.putInt("Oxidation", 1);
                    }
                    case "block.minecraft.exposed_copper" -> {
                        nbt.putInt("CustomModelData", 2);
                        nbt.putInt("Oxidation", 2);
                    }
                    case "block.minecraft.weathered_copper" -> {
                        nbt.putInt("CustomModelData", 3);
                        nbt.putInt("Oxidation", 3);
                    }
                    case "block.minecraft.oxidized_copper" -> {
                        nbt.putInt("CustomModelData", 4);
                        nbt.putInt("Oxidation", 4);
                    }
                    case "block.minecraft.waxed_copper" -> {
                        nbt.putInt("CustomModelData", 1);
                        nbt.putInt("Oxidation", 1);
                        nbt.putBoolean("Waxed", true);
                    }
                    case "block.minecraft.waxed_exposed_copper" -> {
                        nbt.putInt("CustomModelData", 2);
                        nbt.putInt("Oxidation", 2);
                        nbt.putBoolean("Waxed", true);
                    }
                    case "block.minecraft.waxed_weathered_copper" -> {
                        nbt.putInt("CustomModelData", 3);
                        nbt.putInt("Oxidation", 3);
                        nbt.putBoolean("Waxed", true);
                    }
                    case "block.minecraft.waxed_oxidized_copper" -> {
                        nbt.putInt("CustomModelData", 4);
                        nbt.putInt("Oxidation", 4);
                        nbt.putBoolean("Waxed", true);
                    }
                }
                cir.setReturnValue(stack);
            }
            // waxing copper bulb recipe
            case 3 -> {
                ItemStack stack = this.temp.copy();
                String name = this.temp.getName().getString();
                if ((Boolean) ConfigOptions.USE_TRANSLATABLE_TEXT.value()) {
                    stack.setCustomName(TextUtils.translatable("block.minecraft.waxed_" + name.toLowerCase()
                            .replaceAll(" ", "_")).setStyle(Style.EMPTY.withItalic(false)));
                }
                else {
                    stack.setCustomName(TextUtils.formattable("Waxed " + name).setStyle(Style.EMPTY.withItalic(false)));
                }
                NbtCompound nbt = stack.getOrCreateNbt();
                nbt.putBoolean("Waxed", true);
                cir.setReturnValue(stack);
            }
        }
        this.customRecipeType = 0;
    }

}
