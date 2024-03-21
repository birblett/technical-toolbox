package com.birblett.mixin;

import com.birblett.TechnicalToolbox;
import com.birblett.lib.NodeRemovalInterface;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(CommandNode.class)
public class CommandNodeMixin<S> implements NodeRemovalInterface {

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
