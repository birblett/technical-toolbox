package com.birblett.compat.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import com.birblett.compat.MixinPlugin;
import com.birblett.impl.config.ConfigOption;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

/**
 * Handles Lithium compatibility when skipping POI state check. See {@link ConfigOption#LEGACY_POI_PROPERTY_CHECK}
 */
@MixinPlugin.Requires("lithium")
@Mixin(value = PointOfInterestStorage.class, priority = 2000)
public class Lithium_PointOfInterestStorageMixin {

    @TargetHandler(mixin = "me.jellysquid.mods.lithium.mixin.ai.poi.PointOfInterestStorageMixin", name = "lithium$findNearestForPortalLogic", prefix = "lithium")
    @ModifyVariable(method = "@MixinSquared:Handler", at = @At("HEAD"))
    private Predicate<PointOfInterest> overrideLithiumPOILogic(Predicate<PointOfInterest> p) {
        if (ConfigOption.LEGACY_POI_PROPERTY_CHECK.val()) {
            return poi -> true;
        }
        return p;
    }

}
