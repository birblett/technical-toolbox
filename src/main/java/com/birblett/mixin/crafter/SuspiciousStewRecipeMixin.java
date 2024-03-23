package com.birblett.mixin.crafter;

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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disgusting hardcoded recipes WHY was nbt crafting so hard to do
 */
@Mixin(SuspiciousStewRecipe.class)
public class SuspiciousStewRecipeMixin {

    @Unique private final Item[] crafterRecipe = new Item[]{
            Items.IRON_INGOT,     Items.IRON_INGOT,     Items.IRON_INGOT,
            Items.IRON_INGOT,     Items.CRAFTING_TABLE, Items.IRON_INGOT,
            Items.REDSTONE,       Items.DROPPER,     Items.REDSTONE,
    };

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean matchCrafter(boolean out, @Local RecipeInputInventory recipeInputInventory) {
        if (!out && recipeInputInventory.size() == 9) {
            NbtCompound nbt;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = recipeInputInventory.getStack(i);
                if (stack.getItem() != crafterRecipe[i]) {
                    return false;
                }
                else if (stack.getItem().equals(Items.DROPPER) && (nbt = stack.getNbt()) != null && nbt.
                        getInt("CustomModelData") == 1) {
                    return false;
                }
            }
            return true;
        }
        return out;
    }

    @Inject(method = "craft(Lnet/minecraft/inventory/RecipeInputInventory;Lnet/minecraft/registry/DynamicRegistryManager;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void craftCustom(RecipeInputInventory recipeInputInventory, DynamicRegistryManager dynamicRegistryManager, CallbackInfoReturnable<ItemStack> cir) {
        if (recipeInputInventory.size() > 0 && recipeInputInventory.getStack(0).isOf(Items.IRON_INGOT)) {
            ItemStack stack = Items.DROPPER.getDefaultStack();
            stack.getOrCreateNbt().putInt("CustomModelData", 13579);
            if ((Boolean) ConfigOptions.USE_TRANSLATABLE_TEXT.value()) {
                stack.setCustomName(TextUtils.translatable("container.crafter"));
            }
            else {
                stack.setCustomName(TextUtils.formattable("Crafter").setStyle(Style.EMPTY.withItalic(false)));
            }
            cir.setReturnValue(stack);
        }
    }

}
