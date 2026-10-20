package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.BadOmenMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Instant activation of raids while having Bad Omen, while legacy raid mechanics are active. See
 * {@link ConfigOptions#LEGACY_RAID}
 */
@Mixin(BadOmenMobEffect.class)
public class BadOmenMobEffectMixin {

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void instantRaidProc(ServerLevel world, LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (ConfigOptions.LEGACY_RAID.val() && entity instanceof ServerPlayer serverPlayerEntity &&
                !entity.isSpectator()) {
            if (world.getDifficulty() == Difficulty.PEACEFUL) {
                return;
            }
            if (world.isVillage(entity.blockPosition())) {
                serverPlayerEntity.setRaidOmenPosition(serverPlayerEntity.blockPosition());
                world.getRaids().createOrExtendRaid(serverPlayerEntity, serverPlayerEntity.getRaidOmenPosition());
                cir.setReturnValue(false);
            }
        }
    }

}
