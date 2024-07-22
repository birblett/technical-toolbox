package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOption;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityType.class)
public class EntityTypeMixin<T extends Entity> {

    @Shadow @Final public static EntityType<EndCrystalEntity> END_CRYSTAL;

    @ModifyReturnValue(method = "isFireImmune", at = @At("RETURN"))
    private boolean endCrystalFireImmunity(boolean b) {
        if (((Object) this).equals(END_CRYSTAL)) {
            return !ConfigOption.LEGACY_END_CRYSTAL_FIRE_DAMAGE.val();
        }
        return b;
    }

}
