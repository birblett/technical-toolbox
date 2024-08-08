package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOption;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.raid.Raid;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables raid captain ominous bottle drops when old raid mechanics are enabled and instead directly applies bad omen.
 * See {@link com.birblett.impl.config.ConfigOption#LEGACY_BAD_OMEN}
 */
@Mixin(RaiderEntity.class)
public class RaiderEntityMixin {

    @Inject(method = "isCaptain", at = @At("HEAD"), cancellable = true)
    private void disableCaptainPredicate(CallbackInfoReturnable<Boolean> cir) {
        if (ConfigOption.LEGACY_BAD_OMEN.val()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyExpressionValue(method = "onDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/raid/RaiderEntity;getRaid()Lnet/minecraft/village/raid/Raid;"))
    private Raid handleRaidCaptain(Raid raid, @Local(argsOnly = true) DamageSource source) {
        RaiderEntity self = (RaiderEntity) (Object) this;
        if (ConfigOption.LEGACY_BAD_OMEN.val() && self.isPatrolLeader() && raid == null && ((ServerWorld)
                (self.getWorld())).getRaidAt(self.getBlockPos()) == null) {
            ItemStack itemStack = self.getEquippedStack(EquipmentSlot.HEAD);
            PlayerEntity playerEntity = null;
            Entity e = source.getAttacker();
            if (e instanceof PlayerEntity) {
                playerEntity = (PlayerEntity) e;
            }
            else if (e instanceof WolfEntity wolfEntity) {
                LivingEntity livingEntity = wolfEntity.getOwner();
                if (wolfEntity.isTamed() && livingEntity instanceof PlayerEntity) {
                    playerEntity = (PlayerEntity)livingEntity;
                }
            }
            if (!itemStack.isEmpty() && ItemStack.areEqual(itemStack, Raid.getOminousBanner(self.getRegistryManager()
                    .getWrapperOrThrow(RegistryKeys.BANNER_PATTERN))) && playerEntity != null) {
                StatusEffectInstance statusEffectInstance = playerEntity.getStatusEffect(StatusEffects.BAD_OMEN);
                int i = 1;
                if (statusEffectInstance != null) {
                    i += statusEffectInstance.getAmplifier();
                    playerEntity.removeStatusEffectInternal(StatusEffects.BAD_OMEN);
                } else {
                    --i;
                }
                i = MathHelper.clamp(i, 0, 4);
                StatusEffectInstance statusEffectInstance2 = new StatusEffectInstance(StatusEffects.BAD_OMEN, 120000, i,
                        false, false, true);
                if (!self.getWorld().getGameRules().getBoolean(GameRules.DISABLE_RAIDS)) {
                    playerEntity.addStatusEffect(statusEffectInstance2);
                }
            }
        }
        return raid;
    }

}
