package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.PointOfInterestTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.raid.Raid;
import net.minecraft.village.raid.RaidManager;
import net.minecraft.world.PersistentState;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.rule.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;

@Mixin(RaidManager.class)
public abstract class RaidManagerMixin extends PersistentState {

    @Shadow
    @Final
    private Int2ObjectMap<Raid> raids;
    @Shadow
    protected abstract Raid getOrCreateRaid(ServerWorld world, BlockPos pos);

    @Shadow
    protected abstract int nextId();

    @Inject(method = "startRaid", at = @At("HEAD"), cancellable = true)
    private void startRaid(ServerPlayerEntity player, BlockPos pos, CallbackInfoReturnable<Raid> cir) {
        if (ConfigOptions.LEGACY_RAID.val()) {
            cir.setReturnValue(this.legacyStartRaid(player, player.getEntityWorld()));
        }
    }

    @Unique
    private Raid legacyStartRaid(ServerPlayerEntity player, ServerWorld world) {
        if (player.isSpectator()) {
            return null;
        } else if (world.getGameRules().getValue(GameRules.DISABLE_RAIDS)) {
            return null;
        } else {
            if (!world.getEnvironmentAttributes().getAttributeValue(EnvironmentAttributes.CAN_START_RAID_GAMEPLAY, player.getBlockPos())) {
                return null;
            } else {
                BlockPos blockPos = player.getBlockPos();
                List<PointOfInterest> list = world.getPointOfInterestStorage().getInCircle((poiType) ->
                        poiType.isIn(PointOfInterestTypeTags.VILLAGE), blockPos, 64,
                        PointOfInterestStorage.OccupationStatus.IS_OCCUPIED).toList();
                int i = 0;
                Vec3d vec3d = Vec3d.ZERO;
                for(Iterator<PointOfInterest> var8 = list.iterator(); var8.hasNext(); ++i) {
                    PointOfInterest pointOfInterest = var8.next();
                    BlockPos blockPos2 = pointOfInterest.getPos();
                    vec3d = vec3d.add(blockPos2.getX(), blockPos2.getY(), blockPos2.getZ());
                }
                BlockPos blockPos3 = i > 0 ? BlockPos.ofFloored(vec3d.multiply(1.0 / (double) i)) : blockPos;
                Raid raid = this.getOrCreateRaid(player.getEntityWorld(), blockPos3);
                boolean bl = false;
                if (!raid.hasStarted()) {
                    this.raids.put(this.nextId(), raid);
                    bl = true;
                } else if (raid.getBadOmenLevel() < raid.getMaxAcceptableBadOmenLevel()) {
                    bl = true;
                } else {
                    player.removeStatusEffect(StatusEffects.BAD_OMEN);
                }
                if (bl) {
                    raid.start(player);
                    if (!raid.hasSpawned()) {
                        player.incrementStat(Stats.RAID_TRIGGER);
                        Criteria.VOLUNTARY_EXILE.trigger(player);
                    }
                }
                this.markDirty();
                return raid;
            }
        }
    }

}
