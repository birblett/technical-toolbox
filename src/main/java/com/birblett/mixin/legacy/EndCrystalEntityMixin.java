package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalEntity.class)
public class EndCrystalEntityMixin {

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/decoration/EndCrystalEntity;tickBlockCollision()V"), cancellable = true)
    private void oldEndCrystalLogic(CallbackInfo ci) {
        if (ConfigOptions.LEGACY_END_CRYSTAL_COLLISION.val()) {
            EndCrystalEntity entity = (EndCrystalEntity) (Object) this;
            if (entity.getEntityWorld() instanceof ServerWorld) {
                BlockPos blockPos = entity.getBlockPos();
                if (((ServerWorld) entity.getEntityWorld()).getEnderDragonFight() != null && entity.getEntityWorld().getBlockState(blockPos).isAir()) {
                    entity.getEntityWorld().setBlockState(blockPos, AbstractFireBlock.getState(entity.getEntityWorld(), blockPos));
                }
            }
            ci.cancel();
        }
    }

}
