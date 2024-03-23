package com.birblett.mixin.mechanic;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.state.State;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bypasses conditional check on {@link Properties#HORIZONTAL_AXIS} if
 * {@link ConfigOptions#MECHANIC_DISABLE_POI_PROPERTY_CHECK} enabled
 */
@Mixin(State.class)
public class StateMixin {

    @ModifyReturnValue(method = "contains", at = @At("RETURN"))
    private <T extends Comparable<T>> boolean get(boolean out, @Local Property<T> property) {
        if (property.equals(Properties.HORIZONTAL_AXIS) && (Boolean) ConfigOptions.MECHANIC_DISABLE_POI_PROPERTY_CHECK.value()) {
            return true;
        }
        return out;
    }

}
