package com.birblett.mixin.command;

import com.birblett.TechnicalToolbox;
import com.birblett.accessor.command.CommandNodeModifier;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Interface to remove existing commands from the command tree.
 */
@Mixin(CommandNode.class)
public class CommandNodeMixin<S> implements CommandNodeModifier {

    @Shadow @Final private Map<String, CommandNode<S>> children;
    @Shadow @Final private Map<String, ArgumentCommandNode<S, ?>> arguments;
    @Shadow @Final private Map<String, LiteralCommandNode<S>> literals;

    @Override
    public void technicalToolbox$RemoveStringInstance(String s) {
        this.children.remove(s);
        this.literals.remove(s);
        this.arguments.remove(s);
    }

}
