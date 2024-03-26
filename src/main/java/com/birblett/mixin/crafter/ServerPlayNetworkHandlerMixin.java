package com.birblett.mixin.crafter;

import com.birblett.impl.crafter.CrafterScreenHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modifies certain crafting-related event behavior while using the crafter GUI
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;

    /**
     * Removes disabled slots when crafting book is used
     */
    @Inject(method = "onCraftRequest", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getRecipeManager()Lnet/minecraft/recipe/RecipeManager;"))
    private void cancelCraftRequest(CraftRequestC2SPacket packet, CallbackInfo ci) {
        if (this.player.currentScreenHandler instanceof CrafterScreenHandler c) {
            DefaultedList<ItemStack> stacks = c.getStacks();
            for (int i = 0; i < 10; i ++) {
                if (stacks.get(i).isOf(Items.BARRIER)) {
                    c.setStackInSlot(i, 0, ItemStack.EMPTY);
                }
            }
        }
    }

    /**
     * Prevents manual crafting in the crafter and also handles slot toggling
     */
    @WrapOperation(method = "onClickSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/ScreenHandler;onSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void cancelCraft(ScreenHandler screenHandler, int i, int button, SlotActionType action, PlayerEntity player, Operation<Void> original) {
        if (screenHandler instanceof CrafterScreenHandler c) {
            if (i == 0) {
                return;
            }
            else if (i > 0 && i < 10 && c.toggleSlot(i - 1)) {
                return;
            }
        }
        original.call(screenHandler, i, button, action, player);
    }

}
