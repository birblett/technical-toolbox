package com.birblett.mixin.feature;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Allows different protection enchantments to be combined. See {@link ConfigOptions#LEGACY_PROTECTION_COMPATIBILITY}
 */
@Mixin(AnvilMenu.class)
public class AnvilScreenHandlerMixin {

    @Unique
    private static final Identifier PROTECTION_ID = Identifier.parse("exclusive_set/armor");

    @WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"))
    private boolean allowProtectionCombination(Holder<Enchantment> first, Holder<Enchantment> second, Operation<Boolean> original) {
        Optional<TagKey<Enchantment>> t;
        if (ConfigOptions.LEGACY_PROTECTION_COMPATIBILITY.val() && first.value().exclusiveSet() == second.value().exclusiveSet() && (t =
                first.value().exclusiveSet().unwrapKey()).isPresent() && t.get().location().equals(PROTECTION_ID)) {
            return true;
        }
        return original.call(first, second);
    }

}
