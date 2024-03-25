package com.birblett.mixin.commands;

import com.birblett.lib.command.CommandSourceModifier;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows for command aliases to override permission level checks on use
 */
@Mixin(ServerCommandSource.class)
public class ServerCommandSourceMixin implements CommandSourceModifier {

    @Unique private boolean overridePermissions = false;

    @Override
    public void setPermissionOverride(boolean override) {
        this.overridePermissions = override;
    }

    @ModifyReturnValue(method = "hasPermissionLevel", at = @At("RETURN"))
    private boolean overridePermissionLevelCheck(boolean b) {
        return b || this.overridePermissions;
    }

}
