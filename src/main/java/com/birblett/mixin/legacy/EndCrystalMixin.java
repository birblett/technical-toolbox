package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystal.class)
public class EndCrystalMixin {

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;applyEffectsFromBlocks()V"), cancellable = true)
    private void oldEndCrystalLogic(CallbackInfo ci) {
        if (ConfigOptions.LEGACY_END_CRYSTAL_COLLISION.val()) {
            EndCrystal entity = (EndCrystal) (Object) this;
            if (entity.level() instanceof ServerLevel) {
                BlockPos blockPos = entity.blockPosition();
                if (((ServerLevel) entity.level()).getDragonFight() != null && entity.level().getBlockState(blockPos).isAir()) {
                    entity.level().setBlockAndUpdate(blockPos, BaseFireBlock.getState(entity.level(), blockPos));
                }
            }
            ci.cancel();
        }
    }

}
