package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.world.entity.raid.Raid.RAVAGER_SPAWN_PLACEMENT_TYPE;

@Mixin(Raid.class)
public abstract class RaidMixin {

    @Shadow
    @Final
    private static Component RAID_NAME_COMPONENT;
    @Shadow
    @Final
    private static Component RAID_BAR_VICTORY_COMPONENT;
    @Shadow
    @Final
    private static Component RAID_BAR_DEFEAT_COMPONENT;
    @Shadow
    private BlockPos center;
    @Shadow
    private Raid.RaidStatus status;
    @Shadow
    private boolean active;
    @Shadow
    @Final
    private ServerBossEvent raidEvent;
    @Shadow
    private int groupsSpawned;
    @Shadow
    private long ticksActive;
    @Shadow
    private int raidCooldownTicks;
    @Shadow
    private Optional<BlockPos> waveSpawnPos;
    @Shadow
    private boolean started;
    @Shadow
    private int postRaidTicks;
    @Shadow
    private int celebrationTicks;
    @Shadow
    private int raidOmenLevel;
    @Shadow
    @Final
    private Set<UUID> heroesOfTheVillage;
    @Shadow
    public abstract void stop();
    @Shadow
    protected abstract void moveRaidCenterToNearbyVillageSection(ServerLevel world);
    @Shadow
    public abstract int getTotalRaidersAlive();
    @Shadow
    protected abstract boolean hasMoreWaves();
    @Shadow
    protected abstract void updatePlayers(ServerLevel world);
    @Shadow
    protected abstract void updateRaiders(ServerLevel world);
    @Shadow
    protected abstract boolean shouldSpawnGroup();
    @Shadow
    protected abstract void spawnGroup(ServerLevel world, BlockPos p);
    @Shadow
    protected abstract void playSound(ServerLevel world, BlockPos p);
    @Shadow
    public abstract boolean isStarted();
    @Shadow
    protected abstract void setDirty(ServerLevel world);
    @Shadow
    public abstract boolean isOver();
    @Shadow
    public abstract boolean isVictory();

    @ModifyArg(method = "absorbRaidOmen", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;"))
    private Holder<MobEffect> raidStatusEffect(Holder<MobEffect> old) {
        return ConfigOptions.LEGACY_RAID.val() ? MobEffects.BAD_OMEN : old;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, order = 1001)
    private void legacyRaidTick(ServerLevel world, CallbackInfo ci) {
        if (ConfigOptions.LEGACY_RAID.val()) {
            this.tickRaid(world);
            ci.cancel();
        }
    }

    @Inject(method = "findRandomSpawnPos", at = @At("HEAD"), cancellable = true)
    private void legacyRaidSpawnMechanics(ServerLevel world, int proximity, CallbackInfoReturnable<BlockPos> cir) {
        if (ConfigOptions.LEGACY_RAID.val()) {
            cir.setReturnValue(this.getRavagerSpawnLocation(world, proximity, 20));
        }
    }

    @Unique
    private void tickRaid(ServerLevel world) {
        if (this.status != Raid.RaidStatus.STOPPED) {
            if (this.status == Raid.RaidStatus.ONGOING) {
                boolean bl = this.active;
                this.active = world.hasChunkAt(this.center);
                if (world.getDifficulty() == Difficulty.PEACEFUL) {
                    this.stop();
                    return;
                }
                if (bl != this.active) {
                    this.raidEvent.setVisible(this.active);
                }
                if (!this.active) {
                    return;
                }
                if (!world.isVillage(this.center)) {
                    this.moveRaidCenterToNearbyVillageSection(world);
                }
                if (!world.isVillage(this.center)) {
                    if (this.groupsSpawned > 0) {
                        this.status = Raid.RaidStatus.LOSS;
                    } else {
                        this.stop();
                    }
                }
                ++this.ticksActive;
                if (this.ticksActive >= 48000L) {
                    this.stop();
                    return;
                }
                int i = this.getTotalRaidersAlive();
                boolean bl2;
                if (i == 0 && this.hasMoreWaves()) {
                    if (this.raidCooldownTicks <= 0) {
                        if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
                            this.raidCooldownTicks = 300;
                            this.raidEvent.setName(RAID_NAME_COMPONENT);
                            return;
                        }
                    } else {
                        bl2 = this.waveSpawnPos.isPresent();
                        boolean bl3 = !bl2 && this.raidCooldownTicks % 5 == 0;
                        if (bl2 && !world.isPositionEntityTicking(this.waveSpawnPos.get())) {
                            bl3 = true;
                        }
                        if (bl3) {
                            int j = 0;
                            if (this.raidCooldownTicks < 100) {
                                j = 1;
                            }
                            this.waveSpawnPos = this.preCalculateRavagerSpawnLocation(world, j);
                        }
                        if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
                            this.updatePlayers(world);
                        }
                        --this.raidCooldownTicks;
                        this.raidEvent.setProgress(Mth.clamp((float)(300 - this.raidCooldownTicks) / 300.0F, 0.0F, 1.0F));
                    }
                }
                if (this.ticksActive % 20L == 0L) {
                    this.updatePlayers(world);
                    this.updateRaiders(world);
                    if (i > 0) {
                        if (i <= 2) {
                            this.raidEvent.setName(RAID_NAME_COMPONENT.copy().append(" - ").append(Component.translatable("event.minecraft.raid.raiders_remaining", i)));
                        } else {
                            this.raidEvent.setName(RAID_NAME_COMPONENT);
                        }
                    } else {
                        this.raidEvent.setName(RAID_NAME_COMPONENT);
                    }
                }
                bl2 = false;
                int k = 0;
                while(this.shouldSpawnGroup()) {
                    BlockPos blockPos = this.waveSpawnPos.isPresent() ? this.waveSpawnPos.get() :
                            this.getRavagerSpawnLocation(world, k, 20);
                    if (blockPos != null) {
                        this.started = true;
                        this.spawnGroup(world, blockPos);
                        if (!bl2) {
                            this.playSound(world, blockPos);
                            bl2 = true;
                        }
                    } else {
                        ++k;
                    }
                    if (k > 3) {
                        this.stop();
                        break;
                    }
                }
                if (this.isStarted() && !this.hasMoreWaves() && i == 0) {
                    if (this.postRaidTicks < 40) {
                        ++this.postRaidTicks;
                    } else {
                        this.status = Raid.RaidStatus.VICTORY;
                        for (UUID uUID : this.heroesOfTheVillage) {
                            Entity entity = world.getEntity(uUID);
                            if (entity instanceof LivingEntity livingEntity && !entity.isSpectator()) {
                                livingEntity.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.raidOmenLevel - 1, false, false, true));
                                if (livingEntity instanceof ServerPlayer serverPlayerEntity) {
                                    serverPlayerEntity.awardStat(Stats.RAID_WIN);
                                    CriteriaTriggers.RAID_WIN.trigger(serverPlayerEntity);
                                }
                            }
                        }
                    }
                }
                this.setDirty(world);
            } else if (this.isOver()) {
                ++this.celebrationTicks;
                if (this.celebrationTicks >= 600) {
                    this.stop();
                    return;
                }

                if (this.celebrationTicks % 20 == 0) {
                    this.updatePlayers(world);
                    this.raidEvent.setVisible(true);
                    if (this.isVictory()) {
                        this.raidEvent.setProgress(0.0F);
                        this.raidEvent.setName(RAID_BAR_VICTORY_COMPONENT);
                    } else {
                        this.raidEvent.setName(RAID_BAR_DEFEAT_COMPONENT);
                    }
                }
            }
        }
    }

    @Unique
    private Optional<BlockPos> preCalculateRavagerSpawnLocation(ServerLevel world, int proximity) {
        for(int i = 0; i < 3; ++i) {
            BlockPos blockPos = this.getRavagerSpawnLocation(world, proximity, 1);
            if (blockPos != null) {
                return Optional.of(blockPos);
            }
        }
        return Optional.empty();
    }

    @Unique
    @Nullable
    private BlockPos getRavagerSpawnLocation(ServerLevel world, int proximity, int tries) {
        int i = proximity == 0 ? 2 : 2 - proximity;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for(int j = 0; j < tries; ++j) {
            float f = world.random.nextFloat() * 6.2831855F;
            int k = this.center.getX() + Mth.floor(Mth.cos(f) * 32.0F * i) + world.random.nextInt(5);
            int l = this.center.getZ() + Mth.floor(Mth.sin(f) * 32.0F * i) + world.random.nextInt(5);
            int m = world.getHeight(Heightmap.Types.WORLD_SURFACE, k, l);
            mutable.set(k, m, l);
            if (!world.isVillage(mutable) || proximity >= 2) {
                if (world.hasChunksAt(mutable.getX() - 10, mutable.getZ() - 10, mutable.getX() + 10,
                        mutable.getZ() + 10) && world.isPositionEntityTicking(mutable) &&
                        (RAVAGER_SPAWN_PLACEMENT_TYPE.isSpawnPositionOk(world, mutable, EntityType.RAVAGER) ||
                        world.getBlockState(mutable.below()).is(Blocks.SNOW) && world.getBlockState(mutable).isAir())) {
                    return mutable;
                }
            }
        }
        return null;
    }

}
