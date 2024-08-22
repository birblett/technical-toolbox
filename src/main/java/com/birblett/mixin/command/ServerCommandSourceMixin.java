package com.birblett.mixin.command;

import com.birblett.impl.command.alias.language.Operator;
import com.birblett.accessor.command.CommandSourceModifier;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Allows for command aliases to override permission level checks on use and also enable/disable feedback
 */
@Mixin(ServerCommandSource.class)
public class ServerCommandSourceMixin implements CommandSourceModifier {

    @Unique private boolean overridePermissions = false;
    @Unique private boolean shutUp = false;
    @Unique private final HashMap<String, String> selectorMap = new HashMap<>();
    @Unique private final HashSet<ScoreboardCriterion> criteria = new HashSet<>();
    @Unique private Operator ret = null;

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

    @Override
    public void technicalToolbox$addCriterion(ScoreboardCriterion criterion) {
        this.criteria.add(criterion);
    }

    @Override
    public HashSet<ScoreboardCriterion> technicalToolbox$getCriteria(ScoreboardCriterion criterion) {
        return this.criteria;
    }

    @ModifyReturnValue(method = "hasPermissionLevel", at = @At("RETURN"))
    private boolean overridePermissionLevelCheck(boolean b) {
        return b || this.overridePermissions;
    }

    @WrapOperation(method = "sendFeedback", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/command/CommandOutput;sendMessage(Lnet/minecraft/text/Text;)V"))
    private void maybeShutUp(CommandOutput instance, Text text, Operation<Void> original) {
        if (!this.shutUp) {
            original.call(instance, text);
        }
    }

}
