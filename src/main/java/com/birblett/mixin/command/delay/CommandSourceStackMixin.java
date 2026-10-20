package com.birblett.mixin.command.delay;

import com.birblett.accessor.command.delay.AliasedCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import net.minecraft.commands.CommandSourceStack;

@Mixin(CommandSourceStack.class)
public class CommandSourceStackMixin implements AliasedCommandSource {

    @Unique
    private final HashMap<String, Object> commandOptions = new HashMap<>();
    @Unique
    private int instructionCount = 0;
    @Unique
    private int recursionCount = 0;
    @Unique
    private Object returns = null;

    @Override
    public void technicalToolbox$SetOpt(String s, Object value) {
        this.commandOptions.put(s, value);
    }

    @Override
    public Object technicalToolbox$GetOpt(String s) {
        return this.commandOptions.get(s);
    }

    @Override
    public void technicalToolbox$ResetOpt() {
        this.commandOptions.clear();
    }

    @Override
    public void technicalToolbox$AddToInstructionCount(int i) {
        this.instructionCount += i;
    }

    @Override
    public int technicalToolbox$getInstructionCount() {
        return this.instructionCount;
    }

    @Override
    public void technicalToolbox$AddToRecursionDepth(int i) {
        this.recursionCount += i;
    }

    @Override
    public int technicalToolbox$getRecursionCount() {
        return this.recursionCount;
    }

}
