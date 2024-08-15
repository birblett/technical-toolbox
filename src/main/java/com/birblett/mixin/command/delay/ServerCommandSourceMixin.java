package com.birblett.mixin.command.delay;

import com.birblett.lib.command.delay.CommandOption;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;

@Mixin(ServerCommandSource.class)
public class ServerCommandSourceMixin implements CommandOption {

    @Unique private final HashMap<String, Object> commandOptions = new HashMap<>();

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

}
