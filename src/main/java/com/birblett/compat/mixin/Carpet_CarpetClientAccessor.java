package com.birblett.compat.mixin;

import com.birblett.compat.MixinPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@MixinPlugin.Requires("carpet")
@Pseudo
@Mixin(targets = "carpet.network.CarpetClient")
public interface Carpet_CarpetClientAccessor {

    @Invoker("disconnect")
    static void disconnect() {
        throw new AssertionError();
    };

}
