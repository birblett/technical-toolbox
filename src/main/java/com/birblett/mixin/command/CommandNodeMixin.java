package com.birblett.mixin.command;

import com.birblett.lib.command.CommandNodeModifier;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/**
 * Interface to remove existing commands from the command tree.
 */
@Mixin(CommandNode.class)
public class CommandNodeMixin<S> implements CommandNodeModifier {

    @Shadow @Final private Map<String, CommandNode<S>> children;
    @Shadow @Final private Map<String, ArgumentCommandNode<S, ?>> arguments;
    @Shadow @Final private Map<String, LiteralCommandNode<S>> literals;

    @Override
    public void removeStringInstance(String s) {
        this.children.remove(s);
        this.literals.remove(s);
        this.arguments.remove(s);
    }

}
