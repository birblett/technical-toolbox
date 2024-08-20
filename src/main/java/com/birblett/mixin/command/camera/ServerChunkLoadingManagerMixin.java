package com.birblett.mixin.command.camera;


import com.birblett.impl.config.ConfigOptions;
import com.birblett.accessor.command.camera.CameraInterface;
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
        if (((CameraInterface) player).technicalToolbox$IsCamera() && !ConfigOptions.CAMERA_GENERATES_CHUNKS.val()) {
            cir.setReturnValue(true);
        }
    }
}

