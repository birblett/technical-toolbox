package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOption;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.BadOmenStatusEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Instant activation of raids while having Bad Omen, while legacy raid mechanics are active. See
 * {@link ConfigOption#LEGACY_BAD_OMEN}
 */
@Mixin(BadOmenStatusEffect.class)
public class BadOmenStatusEffectMixin {

    @Inject(method = "applyUpdateEffect", at = @At("HEAD"), cancellable = true)
    private void instantRaidProc(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (ConfigOption.LEGACY_BAD_OMEN.val() && entity instanceof ServerPlayerEntity serverPlayerEntity &&
                !entity.isSpectator()) {
            ServerWorld serverWorld = serverPlayerEntity.getServerWorld();
            if (serverWorld.getDifficulty() == Difficulty.PEACEFUL) {
                return;
            }
            if (serverWorld.isNearOccupiedPointOfInterest(entity.getBlockPos())) {
                serverPlayerEntity.setStartRaidPos(serverPlayerEntity.getBlockPos());
                serverWorld.getRaidManager().startRaid(serverPlayerEntity, serverPlayerEntity.getStartRaidPos());
                cir.setReturnValue(false);
            }
        }
    }

}
