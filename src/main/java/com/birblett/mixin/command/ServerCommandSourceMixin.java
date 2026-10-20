package com.birblett.mixin.command;

import com.birblett.accessor.command.CommandSourceModifier;
import com.birblett.impl.command.alias.language.Operator;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Allows for command aliases to override permission level checks on use and also enable/disable feedback
 */
@Mixin(CommandSourceStack.class)
public class ServerCommandSourceMixin implements CommandSourceModifier {

    @Unique
    private boolean overridePermissions = false;
    @Unique
    private boolean shutUp = false;
    @Unique
    private final HashMap<String, String> selectorMap = new HashMap<>();
    @Unique
    private final HashSet<ObjectiveCriteria> criteria = new HashSet<>();
    @Unique
    private Operator ret = null;

    @Override
    public void technicalToolbox$setPermissionOverride(boolean override) {
        this.overridePermissions = override;
    }

    @Override
    public void technicalToolbox$shutUp(boolean shutUp) {
        this.shutUp = shutUp;
    }

    @Override
    public void technicalToolbox$addSelector(String name, String value) {
        this.selectorMap.put(name, value);
    }

    @Override
    public String technicalToolbox$getSelectorArgument(String name) {
        return this.selectorMap.get(name);
    }

    @Override
    public void technicalToolbox$setReturnValue(Operator o) {
        this.ret = o;
    }

    @Override
    public Operator technicalToolbox$getReturnValue() {
        return this.ret;
    }

    @ModifyReturnValue(method = "permissions", at = @At("RETURN"))
    private PermissionSet overridePermissionLevelCheck(PermissionSet original) {
        return this.overridePermissions ? PermissionSet.ALL_PERMISSIONS : original;
    }

    @WrapOperation(method = "sendSuccess", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/CommandSource;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"))
    private void maybeShutUp(CommandSource instance, Component text, Operation<Void> original) {
        if (!this.shutUp) {
            original.call(instance, text);
        }
    }

}
