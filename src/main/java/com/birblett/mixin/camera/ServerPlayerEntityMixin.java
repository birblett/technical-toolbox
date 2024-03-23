package com.birblett.mixin.camera;

import com.birblett.impl.config.ConfigOptions;
import com.birblett.lib.crafter.CameraInterface;
import com.birblett.util.TextUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows player to swap to and from camera mode, and also handles some configured functionalities
 */
@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements CameraInterface {

    @Unique private boolean isCamera = false;
    @Unique private String storedGameMode;
    @Unique private NbtCompound storedNbt = null;

    @Unique private NbtList toDoubleNbtList(double ... values) {
        NbtList nbtList = new NbtList();
        for (double d : values) {
            nbtList.add(NbtDouble.of(d));
        }
        return nbtList;
    }

    @Unique private NbtList toIntNbtList(int ... values) {
        NbtList nbtList = new NbtList();
        for (int i : values) {
            nbtList.add(NbtInt.of(i));
        }
        return nbtList;
    }

    @Override
    public boolean isCamera() {
        return this.isCamera;
    }

    /**
     * Swaps player into or out of camera mode. All relevant data is stored as temporary NBT data and is restored when
     * swapping back.
     * @param sendMessage whether it should output a status message or not
     * @return a status message to send to the player and server (if option enabled)
     */
    @Override
    public String swapCameraMode(boolean sendMessage) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.getVehicle() != null && !this.isCamera) {
            return "Exit vehicles before entering camera mode";
        }
        else if (player.isSpectator() && !this.isCamera) {
            return "Player is already in spectator mode";
        }
        else if (!this.isCamera) {
            this.isCamera = true;
            this.storedGameMode = player.interactionManager.getGameMode().asString();
            NbtCompound nbt = (this.storedNbt = new NbtCompound());
            // store dimension
            nbt.putString("Dimension", player.getServerWorld().getRegistryKey().getValue().toString());
            // store position
            nbt.put("Pos", this.toDoubleNbtList(player.getX(), player.getY(), player.getZ()));
            // store motion
            Vec3d vec3d = player.getVelocity();
            nbt.put("Motion", this.toDoubleNbtList(vec3d.x, vec3d.y, vec3d.z));
            // store creative flight
            nbt.putBoolean("Flying", player.getAbilities().flying);
            // store elytra flight
            nbt.putBoolean("FallFlying", player.isFallFlying());
            // store rotation
            nbt.putFloat("Yaw", player.getYaw());
            nbt.putFloat("Pitch", player.getPitch());
            // store fall distance
            nbt.putFloat("FallDistance", player.fallDistance);
            // store fire ticks
            nbt.putInt("Fire", player.getFireTicks());
            // store air ticks
            nbt.putInt("Air", player.getAir());
            // store frozen ticks
            nbt.putInt("TicksFrozen", player.getFrozenTicks());
            // store sleeping pos
            player.getSleepingPosition().ifPresent(pos -> nbt.put("SleepingPos", this.toIntNbtList(pos.getX(),
                    pos.getY(), pos.getZ())));
            // store status effects
            if (!player.getStatusEffects().isEmpty()) {
                NbtList nbtList = new NbtList();
                for (StatusEffectInstance statusEffectInstance : player.getStatusEffects()) {
                    nbtList.add(statusEffectInstance.writeNbt(new NbtCompound()));
                }
                nbt.put("ActiveEffects", nbtList);
            }
            player.changeGameMode(GameMode.SPECTATOR);
            return "Swapping from " + this.storedGameMode + " to camera mode";
        }
        else {
            String out;
            if (this.storedNbt != null) {
                NbtCompound nbt = this.storedNbt;
                // restore world
                ServerWorld world = player.getServerWorld();
                if (player.getServer() != null) {
                    world = player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(nbt.getString("Dimension"))));
                }
                // restore position
                NbtList pos = nbt.getList("Pos", NbtElement.DOUBLE_TYPE);
                Vec3d finalPos = new Vec3d(MathHelper.clamp(pos.getDouble(0), -3.0000512E7, 3.0000512E7),
                        MathHelper.clamp(pos.getDouble(1), -2.0E7, 2.0E7), MathHelper.clamp(pos
                                .getDouble(2), -3.0000512E7, 3.0000512E7));
                // restore rotation
                float yaw = nbt.getFloat("Yaw");
                float pitch = nbt.getFloat("Pitch");
                // restore all
                player.teleport(world, finalPos.x, finalPos.y, finalPos.z, yaw, pitch);
                // restore creative flight
                if (!nbt.getBoolean("Flying")) {
                    player.getAbilities().flying = false;
                }
                // restore elytra flight
                if (nbt.getBoolean("FallFlying")) {
                    player.startFallFlying();
                }
                // restore motion
                NbtList motion = nbt.getList("Motion", NbtElement.DOUBLE_TYPE);
                double dx = motion.getDouble(0);
                double dy = motion.getDouble(1);
                double dz = motion.getDouble(2);
                player.setVelocity(Math.abs(dx) > 10.0 ? 0.0 : dx, Math.abs(dy) > 10.0 ? 0.0 : dy, Math.abs(dz) > 10.0 ? 0.0 : dz);
                player.velocityModified = true;
                player.velocityDirty = true;
                // restore fall distance
                player.fallDistance = nbt.getFloat("FallDistance");
                // restore fire ticks
                player.setFireTicks(nbt.getInt("Fire"));
                // restore air ticks
                player.setAir(nbt.getInt("Air"));
                // restore frozen ticks
                player.setFrozenTicks(nbt.getInt("TicksFrozen"));
                // restore sleeping pos
                if (nbt.contains("SleepingPos")) {
                    NbtList sleepingPos = nbt.getList("SleepingPos", NbtElement.INT_TYPE);
                    player.setSleepingPosition(new BlockPos(sleepingPos.getInt(0), sleepingPos.getInt(1),
                            sleepingPos.getInt(2)));
                }
                // restore status effects
                if (nbt.contains("ActiveEffects", NbtElement.LIST_TYPE)) {
                    NbtList nbtList = nbt.getList("ActiveEffects", NbtElement.COMPOUND_TYPE);
                    for (int i = 0; i < nbtList.size(); ++i) {
                        NbtCompound nbtCompound = nbtList.getCompound(i);
                        StatusEffectInstance statusEffectInstance = StatusEffectInstance.fromNbt(nbtCompound);
                        if (statusEffectInstance == null) {
                            continue;
                        }
                        player.setStatusEffect(statusEffectInstance, null);
                    }
                }
                out = "Swapping from camera mode back to " + this.storedGameMode;
                this.isCamera = false;
            }
            else {
                out = "Swapping back to " + this.storedGameMode + " but can't restore playerdata - maybe corrupted?";
            }
            this.storedNbt = null;
            player.changeGameMode(GameMode.byName(this.storedGameMode, GameMode.SURVIVAL));
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
        if (this.isCamera && !(boolean) ConfigOptions.CAMERA_CAN_SPECTATE.value()) {
            player.sendMessage(TextUtils.formattable("Entity spectating is currently disabled in camera mode"), true);
            ci.cancel();
        }
        else if (this.isCamera && ConfigOptions.CAMERA_CONSOLE_LOGGING.value().equals("spectate") && player.getServer()
                != null) {
            player.getServer().sendMessage(TextUtils.formattable("[Camera Mode] " + player.getEntityName() +
                    " is spectating " + target.getEntityName()));
        }
    }


    /**
     * Automatically disables camera mode if player directly switches gamemodes
     */
    @Inject(method = "changeGameMode", at = @At("HEAD"))
    protected void disableCameraMode(GameMode gameMode, CallbackInfoReturnable<Boolean> cir){
        if (this.isCamera && gameMode != GameMode.SPECTATOR) {
            ((ServerPlayerEntity) (Object) this).sendMessage(TextUtils.formattable("Swapped gamemodes directly, " +
                    "disabling camera mode"), true);
            this.storedNbt = null;
            this.isCamera = false;
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("HEAD"))
    private void writeCameraNbt(NbtCompound nbt, CallbackInfo ci) {
        if (this.isCamera) {
            nbt.putBoolean("IsCamera", true);
            nbt.put("StoredNbt", this.storedNbt);
            nbt.putString("StoredGameMode", this.storedGameMode);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
    private void readCameraNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("IsCamera")) {
            this.isCamera = nbt.getBoolean("IsCamera");
            this.storedNbt = (NbtCompound) nbt.get("StoredNbt");
            this.storedGameMode = nbt.getString("StoredGameMode");
        }
    }

}
