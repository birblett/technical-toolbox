package com.birblett.mixin.feature;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Allows different protection enchantments to be combined. See {@link ConfigOption#LEGACY_PROTECTION_COMPATIBILITY}
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {

    @Shadow public abstract RegistryEntryList<Enchantment> exclusiveSet();

    @Inject(method = "canBeCombined", at = @At("HEAD"), cancellable = true)
    private static void allowProtectionCombination(RegistryEntry<Enchantment> first, RegistryEntry<Enchantment> second, CallbackInfoReturnable<Boolean> cir) {
        Optional<TagKey<Enchantment>> t;
        if (ConfigOption.LEGACY_PROTECTION_COMPATIBILITY.val() && first.value().exclusiveSet() == second.value()
                .exclusiveSet() && (t = first.value().exclusiveSet().getTagKey()).isPresent()) {
            cir.setReturnValue(true);
        }

    }

}
