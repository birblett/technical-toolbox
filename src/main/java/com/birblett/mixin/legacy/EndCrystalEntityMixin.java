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

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/decoration/EndCrystalEntity;checkBlockCollision()V"), cancellable = true)
    private void oldEndCrystalLogic(CallbackInfo ci) {
        if (ConfigOptions.LEGACY_END_CRYSTAL_COLLISION.val()) {
            EndCrystalEntity entity = (EndCrystalEntity) (Object) this;
            if (entity.getWorld() instanceof ServerWorld) {
                BlockPos blockPos = entity.getBlockPos();
                if (((ServerWorld)entity.getWorld()).getEnderDragonFight() != null && entity.getWorld().getBlockState(blockPos).isAir()) {
                    entity.getWorld().setBlockState(blockPos, AbstractFireBlock.getState(entity.getWorld(), blockPos));
                }
            }
            ci.cancel();
        }
    }

}
