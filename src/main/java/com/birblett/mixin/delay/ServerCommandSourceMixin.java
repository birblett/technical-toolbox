package com.birblett.mixin.delay;

import com.birblett.lib.delay.CommandOption;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;

@Mixin(ServerCommandSource.class)
public class ServerCommandSourceMixin implements CommandOption {

    @Unique private final HashMap<String, Object> commandOptions = new HashMap<>();

    @Override
    public void setOpt(String s, Object value) {
        this.commandOptions.put(s, value);
    }

    @Override
    public Object getOpt(String s) {
        return this.commandOptions.get(s);
    }

    @Override
    public void resetOpt() {
        this.commandOptions.clear();
    }

}
