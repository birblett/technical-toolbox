package com.birblett.mixin.camera;


import com.birblett.lib.crafter.CameraInterface;
import com.birblett.util.config.ConfigOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents camera mode players from generating chunks if option is disabled
 */
@Mixin(ThreadedAnvilChunkStorage.class)
public class ThreadedAnvilChunkStorageMixin {

    @Inject(method = "doesNotGenerateChunks", at = @At("RETURN"), cancellable = true)
    protected void cameraGeneratesChunks(ServerPlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (((CameraInterface) player).isCamera() && !(Boolean) ConfigOptions.CAMERA_GENERATES_CHUNKS.value()) {
            cir.setReturnValue(true);
        }
    }
}

