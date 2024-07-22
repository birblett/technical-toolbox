package com.birblett.mixin.camera;


import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.camera.CameraInterface;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents camera mode players from generating chunks if option is disabled
 */
@Mixin(ServerChunkLoadingManager.class)
public class ServerChunkLoadingManagerMixin {

    @Inject(method = "doesNotGenerateChunks", at = @At("RETURN"), cancellable = true)
    protected void cameraGeneratesChunks(ServerPlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (((CameraInterface) player).isCamera() && !ConfigOption.CAMERA_GENERATES_CHUNKS.val()) {
            cir.setReturnValue(true);
        }
    }
}

