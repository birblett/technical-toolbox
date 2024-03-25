package com.birblett.mixin.commands;

import com.birblett.TechnicalToolbox;
import com.birblett.lib.command.CommandNodeModifier;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;
import java.util.Map;

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
