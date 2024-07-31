package com.birblett.mixin.feature;

import com.birblett.impl.config.ConfigOption;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Allows different protection enchantments to be combined. See {@link ConfigOption#LEGACY_PROTECTION_COMPATIBILITY}
 */
@Mixin(AnvilScreenHandler.class)
public class AnvilScreenHandlerMixin {

    @Unique private static final Identifier PROTECTION_ID = Identifier.of("exclusive_set/armor");

    @WrapOperation(method = "updateResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/Enchantment;canBeCombined(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
    private boolean allowProtectionCombination(RegistryEntry<Enchantment> first, RegistryEntry<Enchantment> second, Operation<Boolean> original) {
        Optional<TagKey<Enchantment>> t;
        if (ConfigOption.LEGACY_PROTECTION_COMPATIBILITY.val() && first.value().exclusiveSet() == second.value()
                .exclusiveSet() && (t = first.value().exclusiveSet().getTagKey()).isPresent() && t.get().id()
                .equals(PROTECTION_ID)) {
            return true;
        }
        return original.call(first, second);
    }

}
