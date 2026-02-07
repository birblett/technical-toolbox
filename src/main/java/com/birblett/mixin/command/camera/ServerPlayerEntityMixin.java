package com.birblett.mixin.command.camera;

import com.birblett.accessor.command.camera.CameraInterface;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.TextUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Allows player to swap to and from camera mode, and also handles some configured functionalities
 */
@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements CameraInterface {

    @Unique
    private boolean isCamera = false;
    @Unique
    private String storedGameMode;
    @Unique
    private NbtCompound nbt = null;

    @Override
    public boolean technicalToolbox$IsCamera() {
        return this.isCamera;
    }

    /**
     * Swaps player into or out of camera mode. All relevant data is stored as temporary NBT data and is restored when
     * swapping back.
     *
     * @param sendMessage whether it should output a status message or not
     * @return a status message to send to the player and server (if option enabled)
     */
    @Override
    public String technicalToolbox$SwapCameraMode(boolean sendMessage) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.getVehicle() != null && !this.isCamera) {
            return "Exit vehicles before entering camera mode";
        } else if (player.isSpectator() && !this.isCamera) {
            return "Player is already in spectator mode";
        } else if (!this.isCamera) {
            this.isCamera = true;
            this.storedGameMode = player.interactionManager.getGameMode().asString();
            this.nbt = new NbtCompound();
            // store dimension
            nbt.putString("dimension", player.getEntityWorld().getRegistryKey().getValue().toString());
            // store position
            nbt.put("pos", Vec3d.CODEC, player.getEntityPos());
            // store motion
            nbt.put("motion", Vec3d.CODEC, player.getVelocity());
            // store creative flight
            nbt.putBoolean("flying", player.getAbilities().flying);
            // store elytra flight
            nbt.putBoolean("gliding", player.isGliding());
            // store rotation
            nbt.putFloat("yaw", player.getYaw());
            nbt.putFloat("pitch", player.getPitch());
            // store fall distance
            nbt.putDouble("fall_distance", player.fallDistance);
            // store fire ticks
            nbt.putInt("fire", player.getFireTicks());
            // store air ticks
            nbt.putInt("air", player.getAir());
            // store frozen ticks
            nbt.putInt("ticks_frozen", player.getFrozenTicks());
            // store sleeping pos
            player.getSleepingPosition().ifPresent(pos -> nbt.put("sleeping_pos", BlockPos.CODEC, pos));
            // store status effects
            if (!player.getStatusEffects().isEmpty()) {
                nbt.put("active_effects", StatusEffectInstance.CODEC.listOf(), List.copyOf(player.getStatusEffects()));
            }
            player.changeGameMode(GameMode.SPECTATOR);
            return "Swapping from " + this.storedGameMode + " to camera mode";
        } else {
            String out;
            if (this.nbt != null) {
                // restore world
                ServerWorld world = player.getEntityWorld();
                if (player.getEntityWorld().getServer() != null) {
                    world = player.getEntityWorld().getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,
                            Identifier.of(nbt.getString("dimension", "overworld"))));
                }
                // restore position and rotation
                Optional<Vec3d> pos = nbt.get("pos", Vec3d.CODEC);
                Optional<Float> yaw = nbt.getFloat("yaw");
                Optional<Float> pitch = nbt.getFloat("pitch");
                if (pos.isPresent() && yaw.isPresent() && pitch.isPresent()) {
                    player.teleport(world, pos.get().x, pos.get().y, pos.get().z, Set.of(), yaw.get(), pitch.get(), false);
                }
                // restore creative flight
                nbt.getBoolean("flying").ifPresent(b -> player.getAbilities().flying = b);
                // restore elytra flight
                nbt.getBoolean("gliding").ifPresent(b -> {
                    if (b) {
                        player.startGliding();
                    }
                });
                // restore motion
                nbt.get("motion", Vec3d.CODEC).ifPresent(player::setVelocity);
                player.velocityDirty = true;
                // restore fall distance
                nbt.getFloat("fall_distance").ifPresent(f -> player.fallDistance = f);
                // restore fire ticks
                nbt.getInt("fire").ifPresent(player::setFireTicks);
                // restore air ticks
                nbt.getInt("air").ifPresent(player::setAir);
                // restore frozen ticks
                nbt.getInt("ticks_frozen").ifPresent(player::setFrozenTicks);
                // restore sleeping pos
                nbt.get("sleeping_pos", BlockPos.CODEC).ifPresent(player::setSleepingPosition);
                // restore status effects
                nbt.get("active_effects", StatusEffectInstance.CODEC.listOf()).ifPresent(effects -> {
                    for (StatusEffectInstance effect : effects) {
                        player.setStatusEffect(effect, null);
                    }
                });
                out = "Swapping from camera mode back to " + this.storedGameMode;
                this.isCamera = false;
            } else {
                out = "Swapping back to " + this.storedGameMode + " but can't restore playerdata - maybe corrupted?";
            }
            this.nbt = null;
            player.changeGameMode(GameMode.byId(this.storedGameMode, GameMode.SURVIVAL));
            if (!sendMessage) {
                out = null;
            }
            return out;
        }
    }

    /**
     * Handles disabling entity spectating in camera mode and related logging
     */
    @Inject(method = "attack", at = @At(target = "Lnet/minecraft/server/network/ServerPlayerEntity;setCameraEntity(Lnet/minecraft/entity/Entity;)V",
            value = "INVOKE"), cancellable = true)
    protected void cameraSpectating(Entity target, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (this.isCamera && !ConfigOptions.CAMERA_CAN_SPECTATE.val()) {
            player.sendMessage(TextUtils.formattable("Entity spectating is currently disabled in camera mode"), true);
            ci.cancel();
        } else if (this.isCamera && ConfigOptions.CAMERA_CONSOLE_LOGGING.val().equals("spectate") && player.getEntityWorld().getServer()
                != null) {
            player.getEntityWorld().getServer().sendMessage(TextUtils.formattable("[Camera Mode] " + player.getNameForScoreboard() +
                    " is spectating " + target.getNameForScoreboard()));
        }
    }


    /**
     * Automatically disables camera mode if player directly switches gamemodes
     */
    @Inject(method = "changeGameMode", at = @At("HEAD"))
    protected void disableCameraMode(GameMode gameMode, CallbackInfoReturnable<Boolean> cir) {
        if (this.isCamera && gameMode != GameMode.SPECTATOR) {
            ((ServerPlayerEntity) (Object) this).sendMessage(TextUtils.formattable("Swapped gamemodes directly, disabling camera mode"),
                    true);
            this.nbt = null;
            this.isCamera = false;
        }
    }

    @Inject(method = "writeCustomData", at = @At("HEAD"))
    private void writeCameraNbt(WriteView view, CallbackInfo ci) {
        if (this.isCamera) {
            view.putBoolean("IsCamera", true);
            view.put("StoredNbt", NbtCompound.CODEC, this.nbt);
            view.putString("StoredGameMode", this.storedGameMode);
        }
    }

    @Inject(method = "readCustomData", at = @At("HEAD"))
    private void readCameraNbt(ReadView view, CallbackInfo ci) {
        if (view.getBoolean("IsCamera", false)) {
            this.isCamera = true;
            view.read("StoredNbt", NbtCompound.CODEC).ifPresent(nbt -> {
                this.storedGameMode = view.getString("StoredGameMode", "survival");
                this.nbt = nbt;
            });
        }
    }

}
