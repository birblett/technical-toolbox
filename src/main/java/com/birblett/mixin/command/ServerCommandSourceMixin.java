package com.birblett.mixin.command;

import com.birblett.lib.command.CommandSourceModifier;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows for command aliases to override permission level checks on use and also enable/disable feedback
 */
@Mixin(ServerCommandSource.class)
public class ServerCommandSourceMixin implements CommandSourceModifier {

    @Unique private boolean overridePermissions = false;
    @Unique private boolean shutUp = false;

    @Override
    public void technicalToolbox$setPermissionOverride(boolean override) {
        this.overridePermissions = override;
    }

    @ModifyReturnValue(method = "hasPermissionLevel", at = @At("RETURN"))
    private boolean overridePermissionLevelCheck(boolean b) {
        return b || this.overridePermissions;
    }

    @Override
    public void technicalToolbox$shutUp(boolean shutUp) {
        this.shutUp = shutUp;
    }

    @WrapOperation(method = "sendFeedback", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/command/CommandOutput;sendMessage(Lnet/minecraft/text/Text;)V"))
    private void maybeShutUp(CommandOutput instance, Text text, Operation<Void> original) {
        if (!this.shutUp) {
            original.call(instance, text);
        }
    }

}
