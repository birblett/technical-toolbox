package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityType.class)
public class EntityTypeMixin<T extends Entity> {

    @Shadow
    @Final
    public static EntityType<EndCrystal> END_CRYSTAL;

    @ModifyReturnValue(method = "fireImmune", at = @At("RETURN"))
    private boolean endCrystalFireImmunity(boolean b) {
        if (((Object) this).equals(END_CRYSTAL)) {
            return !ConfigOptions.LEGACY_END_CRYSTAL_FIRE_DAMAGE.val();
        }
        return b;
    }

}
