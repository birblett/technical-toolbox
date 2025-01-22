package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
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
import net.minecraft.world.GameRules;
import net.minecraft.world.PersistentState;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(RaidManager.class)
public abstract class RaidManagerMixin extends PersistentState {

    @Shadow
    @Final
    private Map<Integer, Raid> raids;
    @Shadow
    protected abstract Raid getOrCreateRaid(ServerWorld world, BlockPos pos);

    @Inject(method = "startRaid", at = @At("HEAD"), cancellable = true)
    private void startRaid(ServerPlayerEntity player, BlockPos pos, CallbackInfoReturnable<Raid> cir) {
        if (ConfigOptions.LEGACY_RAID.val()) {
            cir.setReturnValue(this.legacyStartRaid(player, player.getServerWorld()));
        }
    }

    @Unique
    private Raid legacyStartRaid(ServerPlayerEntity player, ServerWorld world) {
        if (player.isSpectator()) {
            return null;
        } else if (world.getGameRules().getBoolean(GameRules.DISABLE_RAIDS)) {
            return null;
        } else {
            DimensionType dimensionType = player.getWorld().getDimension();
            if (!dimensionType.hasRaids()) {
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
                Raid raid = this.getOrCreateRaid(player.getServerWorld(), blockPos3);
                boolean bl = false;
                if (!raid.hasStarted()) {
                    if (!this.raids.containsKey(raid.getRaidId())) {
                        this.raids.put(raid.getRaidId(), raid);
                    }

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
