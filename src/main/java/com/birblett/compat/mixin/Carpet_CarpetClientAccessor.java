package com.birblett.compat.mixin;

import com.birblett.compat.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@RequiresMod({"carpet", "1.4.147+v240613"})
@Pseudo
@Mixin(targets = "carpet.network.CarpetClient")
public interface Carpet_CarpetClientAccessor {

    @Invoker("disconnect")
    static void disconnect() {
        throw new AssertionError();
    };

}
