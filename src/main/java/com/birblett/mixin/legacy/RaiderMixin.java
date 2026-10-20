package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables raid captain ominous bottle drops when old raid mechanics are enabled and instead directly applies bad omen.
 * See {@link ConfigOptions#LEGACY_RAID}
 */
@Mixin(Raider.class)
public class RaiderMixin {

    @Inject(method = "isCaptain", at = @At("HEAD"), cancellable = true)
    private void disableCaptainPredicateForLoot(CallbackInfoReturnable<Boolean> cir) {
        if (ConfigOptions.LEGACY_RAID.val()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyExpressionValue(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raider;getCurrentRaid()Lnet/minecraft/world/entity/raid/Raid;"))
    private Raid legacyRaidCaptain(Raid raid, @Local(argsOnly = true) DamageSource source) {
        Raider self = (Raider) (Object) this;
        if (self.level() instanceof ServerLevel world && ConfigOptions.LEGACY_RAID.val() && self.isPatrolLeader() && raid == null
                && world.getRaidAt(self.blockPosition()) == null) {
            ItemStack itemStack = self.getItemBySlot(EquipmentSlot.HEAD);
            Player playerEntity = null;
            Entity e = source.getEntity();
            if (e instanceof Player) {
                playerEntity = (Player) e;
            } else if (e instanceof Wolf wolfEntity) {
                LivingEntity livingEntity = wolfEntity.getOwner();
                if (wolfEntity.isTame() && livingEntity instanceof Player) {
                    playerEntity = (Player) livingEntity;
                }
            }
            if (!itemStack.isEmpty() && ItemStack.matches(itemStack, Raid.getOminousBannerInstance(self.registryAccess()
                    .lookupOrThrow(Registries.BANNER_PATTERN))) && playerEntity != null) {
                MobEffectInstance statusEffectInstance = playerEntity.getEffect(MobEffects.BAD_OMEN);
                int i = 1;
                if (statusEffectInstance != null) {
                    i += statusEffectInstance.getAmplifier();
                    playerEntity.removeEffectNoUpdate(MobEffects.BAD_OMEN);
                } else {
                    --i;
                }
                i = Mth.clamp(i, 0, 4);
                MobEffectInstance statusEffectInstance2 = new MobEffectInstance(MobEffects.BAD_OMEN, 120000, i,
                        false, false, true);
                if (!world.getGameRules().get(GameRules.RAIDS)) {
                    playerEntity.addEffect(statusEffectInstance2);
                }
            }
        }
        return raid;
    }

}
