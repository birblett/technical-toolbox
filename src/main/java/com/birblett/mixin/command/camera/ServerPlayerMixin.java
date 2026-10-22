package com.birblett.mixin.command.camera;

import com.birblett.accessor.command.camera.CameraInterface;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.TextUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
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
@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements CameraInterface {

    @Unique
    private boolean isCamera = false;
    @Unique
    private String storedGameMode;
    @Unique
    private CompoundTag nbt = null;

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
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.getVehicle() != null && !this.isCamera) {
            return "Exit vehicles before entering camera mode";
        } else if (player.isSpectator() && !this.isCamera) {
            return "Player is already in spectator mode";
        } else if (!this.isCamera) {
            this.isCamera = true;
            this.storedGameMode = player.gameMode.getGameModeForPlayer().getSerializedName();
            this.nbt = new CompoundTag();
            // store dimension
            nbt.putString("dimension", player.level().dimension().identifier().toString());
            // store position
            nbt.store("pos", Vec3.CODEC, player.position());
            // store motion
            nbt.store("motion", Vec3.CODEC, player.getDeltaMovement());
            // store creative flight
            nbt.putBoolean("flying", player.getAbilities().flying);
            // store elytra flight
            nbt.putBoolean("gliding", player.isFallFlying());
            // store rotation
            nbt.putFloat("yaw", player.getYRot());
            nbt.putFloat("pitch", player.getXRot());
            // store fall distance
            nbt.putDouble("fall_distance", player.fallDistance);
            // store fire ticks
            nbt.putInt("fire", player.getRemainingFireTicks());
            // store air ticks
            nbt.putInt("air", player.getAirSupply());
            // store frozen ticks
            nbt.putInt("ticks_frozen", player.getTicksFrozen());
            // store sleeping pos
            player.getSleepingPos().ifPresent(pos -> nbt.store("sleeping_pos", BlockPos.CODEC, pos));
            // store status effects
            if (!player.getActiveEffects().isEmpty()) {
                nbt.store("active_effects", MobEffectInstance.CODEC.listOf(), List.copyOf(player.getActiveEffects()));
            }
            player.setGameMode(GameType.SPECTATOR);
            return "Swapping from " + this.storedGameMode + " to camera mode";
        } else {
            String out;
            if (this.nbt != null) {
                // restore world
                ServerLevel world = player.level();
                if (player.level().getServer() != null) {
                    world = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                            Identifier.parse(nbt.getStringOr("dimension", "overworld"))));
                }
                // restore position and rotation
                Optional<Vec3> pos = nbt.read("pos", Vec3.CODEC);
                Optional<Float> yaw = nbt.getFloat("yaw");
                Optional<Float> pitch = nbt.getFloat("pitch");
                if (pos.isPresent() && yaw.isPresent() && pitch.isPresent()) {
                    player.teleportTo(world, pos.get().x, pos.get().y, pos.get().z, Set.of(), yaw.get(), pitch.get(), false);
                }
                // restore creative flight
                nbt.getBoolean("flying").ifPresent(b -> player.getAbilities().flying = b);
                // restore elytra flight
                nbt.getBoolean("gliding").ifPresent(b -> {
                    if (b) {
                        player.startFallFlying();
                    }
                });
                // restore motion
                nbt.read("motion", Vec3.CODEC).ifPresent(player::setDeltaMovement);
                player.needsSync = true;
                // restore fall distance
                nbt.getFloat("fall_distance").ifPresent(f -> player.fallDistance = f);
                // restore fire ticks
                nbt.getInt("fire").ifPresent(player::setRemainingFireTicks);
                // restore air ticks
                nbt.getInt("air").ifPresent(player::setAirSupply);
                // restore frozen ticks
                nbt.getInt("ticks_frozen").ifPresent(player::setTicksFrozen);
                // restore sleeping pos
                nbt.read("sleeping_pos", BlockPos.CODEC).ifPresent(player::setSleepingPos);
                // restore status effects
                nbt.read("active_effects", MobEffectInstance.CODEC.listOf()).ifPresent(effects -> {
                    for (MobEffectInstance effect : effects) {
                        player.forceAddEffect(effect, null);
                    }
                });
                out = "Swapping from camera mode back to " + this.storedGameMode;
                this.isCamera = false;
            } else {
                out = "Swapping back to " + this.storedGameMode + " but can't restore playerdata - maybe corrupted?";
            }
            this.nbt = null;
            player.setGameMode(GameType.byName(this.storedGameMode, GameType.SURVIVAL));
            if (!sendMessage) {
                out = null;
            }
            return out;
        }
    }


    /**
     * Automatically disables camera mode if player directly switches gamemodes
     */
    @Inject(method = "setGameMode", at = @At("HEAD"))
    protected void disableCameraMode(GameType gameMode, CallbackInfoReturnable<Boolean> cir) {
        if (this.isCamera && gameMode != GameType.SPECTATOR) {
            ((ServerPlayer) (Object) this).sendSystemMessage(TextUtils.formattable("Swapped gamemodes directly, disabling camera mode"),
                    true);
            this.nbt = null;
            this.isCamera = false;
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void writeCameraNbt(ValueOutput view, CallbackInfo ci) {
        if (this.isCamera) {
            view.putBoolean("IsCamera", true);
            view.store("StoredNbt", CompoundTag.CODEC, this.nbt);
            view.putString("StoredGameMode", this.storedGameMode);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void readCameraNbt(ValueInput view, CallbackInfo ci) {
        if (view.getBooleanOr("IsCamera", false)) {
            this.isCamera = true;
            view.read("StoredNbt", CompoundTag.CODEC).ifPresent(nbt -> {
                this.storedGameMode = view.getStringOr("StoredGameMode", "survival");
                this.nbt = nbt;
            });
        }
    }

}
