package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;

@Mixin(Raids.class)
public abstract class RaidsMixin extends SavedData {

    @Shadow
    @Final
    private Int2ObjectMap<Raid> raidMap;
    @Shadow
    protected abstract Raid getOrCreateRaid(ServerLevel world, BlockPos pos);

    @Shadow
    protected abstract int getUniqueId();

    @Inject(method = "createOrExtendRaid", at = @At("HEAD"), cancellable = true)
    private void startRaid(ServerPlayer player, BlockPos pos, CallbackInfoReturnable<Raid> cir) {
        if (ConfigOptions.LEGACY_RAID.val()) {
            cir.setReturnValue(this.legacyStartRaid(player, player.level()));
        }
    }

    @Unique
    private Raid legacyStartRaid(ServerPlayer player, ServerLevel world) {
        if (player.isSpectator()) {
            return null;
        } else if (world.getGameRules().get(GameRules.RAIDS)) {
            return null;
        } else {
            if (!world.environmentAttributes().getValue(EnvironmentAttributes.CAN_START_RAID, player.blockPosition())) {
                return null;
            } else {
                BlockPos blockPos = player.blockPosition();
                List<PoiRecord> list = world.getPoiManager().getInRange((poiType) ->
                        poiType.is(PoiTypeTags.VILLAGE), blockPos, 64,
                        PoiManager.Occupancy.IS_OCCUPIED).toList();
                int i = 0;
                Vec3 vec3d = Vec3.ZERO;
                for(Iterator<PoiRecord> var8 = list.iterator(); var8.hasNext(); ++i) {
                    PoiRecord pointOfInterest = var8.next();
                    BlockPos blockPos2 = pointOfInterest.getPos();
                    vec3d = vec3d.add(blockPos2.getX(), blockPos2.getY(), blockPos2.getZ());
                }
                BlockPos blockPos3 = i > 0 ? BlockPos.containing(vec3d.scale(1.0 / (double) i)) : blockPos;
                Raid raid = this.getOrCreateRaid(player.level(), blockPos3);
                boolean bl = false;
                if (!raid.isStarted()) {
                    this.raidMap.put(this.getUniqueId(), raid);
                    bl = true;
                } else if (raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel()) {
                    bl = true;
                } else {
                    player.removeEffect(MobEffects.BAD_OMEN);
                }
                if (bl) {
                    raid.absorbRaidOmen(player);
                    if (!raid.hasFirstWaveSpawned()) {
                        player.awardStat(Stats.RAID_TRIGGER);
                        CriteriaTriggers.RAID_OMEN.trigger(player);
                    }
                }
                this.setDirty();
                return raid;
            }
        }
    }

}
