package com.birblett.mixin.legacy;

import com.birblett.impl.config.ConfigOptions;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.raid.Raid;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
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

import static net.minecraft.village.raid.Raid.RAVAGER_SPAWN_LOCATION;

@Mixin(Raid.class)
public abstract class RaidMixin {

    @Shadow
    @Final
    private static Text EVENT_TEXT;
    @Shadow
    @Final
    private static Text VICTORY_TITLE;
    @Shadow
    @Final
    private static Text DEFEAT_TITLE;
    @Shadow
    private BlockPos center;
    @Shadow
    private Raid.Status status;
    @Shadow
    private boolean active;
    @Shadow
    @Final
    private ServerBossBar bar;
    @Shadow
    private int wavesSpawned;
    @Shadow
    private long ticksActive;
    @Shadow
    private int preRaidTicks;
    @Shadow
    private Optional<BlockPos> preCalculatedRaidersSpawnLocation;
    @Shadow
    private boolean started;
    @Shadow
    private int postRaidTicks;
    @Shadow
    private int finishCooldown;
    @Shadow
    private int raidOmenLevel;
    @Shadow
    @Final
    private Set<UUID> heroesOfTheVillage;
    @Shadow
    public abstract void invalidate();
    @Shadow
    protected abstract void moveRaidCenter(ServerWorld world);
    @Shadow
    public abstract int getRaiderCount();
    @Shadow
    protected abstract boolean shouldSpawnMoreGroups();
    @Shadow
    protected abstract void updateBarToPlayers(ServerWorld world);
    @Shadow
    protected abstract void removeObsoleteRaiders(ServerWorld world);
    @Shadow
    protected abstract boolean canSpawnRaiders();
    @Shadow
    protected abstract void spawnNextWave(ServerWorld world, BlockPos p);
    @Shadow
    protected abstract void playRaidHorn(ServerWorld world, BlockPos p);
    @Shadow
    public abstract boolean hasStarted();
    @Shadow
    protected abstract void markDirty(ServerWorld world);
    @Shadow
    public abstract boolean isFinished();
    @Shadow
    public abstract boolean hasWon();

    @ModifyArg(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;getStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Lnet/minecraft/entity/effect/StatusEffectInstance;"))
    private RegistryEntry<StatusEffect> raidStatusEffect(RegistryEntry<StatusEffect> old) {
        return ConfigOptions.LEGACY_RAID.val() ? StatusEffects.BAD_OMEN : old;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, order = 1001)
    private void legacyRaidTick(ServerWorld world, CallbackInfo ci) {
        if (ConfigOptions.LEGACY_RAID.val()) {
            this.tickRaid(world);
            ci.cancel();
        }
    }

    @Inject(method = "findRandomRaidersSpawnLocation", at = @At("HEAD"), cancellable = true)
    private void legacyRaidSpawnMechanics(ServerWorld world, int proximity, CallbackInfoReturnable<BlockPos> cir) {
        if (ConfigOptions.LEGACY_RAID.val()) {
            cir.setReturnValue(this.getRavagerSpawnLocation(world, proximity, 20));
        }
    }

    @Unique
    private void tickRaid(ServerWorld world) {
        if (this.status != Raid.Status.STOPPED) {
            if (this.status == Raid.Status.ONGOING) {
                boolean bl = this.active;
                this.active = world.isChunkLoaded(this.center);
                if (world.getDifficulty() == Difficulty.PEACEFUL) {
                    this.invalidate();
                    return;
                }
                if (bl != this.active) {
                    this.bar.setVisible(this.active);
                }
                if (!this.active) {
                    return;
                }
                if (!world.isNearOccupiedPointOfInterest(this.center)) {
                    this.moveRaidCenter(world);
                }
                if (!world.isNearOccupiedPointOfInterest(this.center)) {
                    if (this.wavesSpawned > 0) {
                        this.status = Raid.Status.LOSS;
                    } else {
                        this.invalidate();
                    }
                }
                ++this.ticksActive;
                if (this.ticksActive >= 48000L) {
                    this.invalidate();
                    return;
                }
                int i = this.getRaiderCount();
                boolean bl2;
                if (i == 0 && this.shouldSpawnMoreGroups()) {
                    if (this.preRaidTicks <= 0) {
                        if (this.preRaidTicks == 0 && this.wavesSpawned > 0) {
                            this.preRaidTicks = 300;
                            this.bar.setName(EVENT_TEXT);
                            return;
                        }
                    } else {
                        bl2 = this.preCalculatedRaidersSpawnLocation.isPresent();
                        boolean bl3 = !bl2 && this.preRaidTicks % 5 == 0;
                        if (bl2 && !world.shouldTickEntityAt(this.preCalculatedRaidersSpawnLocation.get())) {
                            bl3 = true;
                        }
                        if (bl3) {
                            int j = 0;
                            if (this.preRaidTicks < 100) {
                                j = 1;
                            }
                            this.preCalculatedRaidersSpawnLocation = this.preCalculateRavagerSpawnLocation(world, j);
                        }
                        if (this.preRaidTicks == 300 || this.preRaidTicks % 20 == 0) {
                            this.updateBarToPlayers(world);
                        }
                        --this.preRaidTicks;
                        this.bar.setPercent(MathHelper.clamp((float)(300 - this.preRaidTicks) / 300.0F, 0.0F, 1.0F));
                    }
                }
                if (this.ticksActive % 20L == 0L) {
                    this.updateBarToPlayers(world);
                    this.removeObsoleteRaiders(world);
                    if (i > 0) {
                        if (i <= 2) {
                            this.bar.setName(EVENT_TEXT.copy().append(" - ").append(Text.translatable("event.minecraft.raid.raiders_remaining", i)));
                        } else {
                            this.bar.setName(EVENT_TEXT);
                        }
                    } else {
                        this.bar.setName(EVENT_TEXT);
                    }
                }
                bl2 = false;
                int k = 0;
                while(this.canSpawnRaiders()) {
                    BlockPos blockPos = this.preCalculatedRaidersSpawnLocation.isPresent() ? this.preCalculatedRaidersSpawnLocation.get() :
                            this.getRavagerSpawnLocation(world, k, 20);
                    if (blockPos != null) {
                        this.started = true;
                        this.spawnNextWave(world, blockPos);
                        if (!bl2) {
                            this.playRaidHorn(world, blockPos);
                            bl2 = true;
                        }
                    } else {
                        ++k;
                    }
                    if (k > 3) {
                        this.invalidate();
                        break;
                    }
                }
                if (this.hasStarted() && !this.shouldSpawnMoreGroups() && i == 0) {
                    if (this.postRaidTicks < 40) {
                        ++this.postRaidTicks;
                    } else {
                        this.status = Raid.Status.VICTORY;
                        for (UUID uUID : this.heroesOfTheVillage) {
                            Entity entity = world.getEntity(uUID);
                            if (entity instanceof LivingEntity livingEntity && !entity.isSpectator()) {
                                livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 48000, this.raidOmenLevel - 1, false, false, true));
                                if (livingEntity instanceof ServerPlayerEntity serverPlayerEntity) {
                                    serverPlayerEntity.incrementStat(Stats.RAID_WIN);
                                    Criteria.HERO_OF_THE_VILLAGE.trigger(serverPlayerEntity);
                                }
                            }
                        }
                    }
                }
                this.markDirty(world);
            } else if (this.isFinished()) {
                ++this.finishCooldown;
                if (this.finishCooldown >= 600) {
                    this.invalidate();
                    return;
                }

                if (this.finishCooldown % 20 == 0) {
                    this.updateBarToPlayers(world);
                    this.bar.setVisible(true);
                    if (this.hasWon()) {
                        this.bar.setPercent(0.0F);
                        this.bar.setName(VICTORY_TITLE);
                    } else {
                        this.bar.setName(DEFEAT_TITLE);
                    }
                }
            }
        }
    }

    @Unique
    private Optional<BlockPos> preCalculateRavagerSpawnLocation(ServerWorld world, int proximity) {
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
    private BlockPos getRavagerSpawnLocation(ServerWorld world, int proximity, int tries) {
        int i = proximity == 0 ? 2 : 2 - proximity;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for(int j = 0; j < tries; ++j) {
            float f = world.random.nextFloat() * 6.2831855F;
            int k = this.center.getX() + MathHelper.floor(MathHelper.cos(f) * 32.0F * i) + world.random.nextInt(5);
            int l = this.center.getZ() + MathHelper.floor(MathHelper.sin(f) * 32.0F * i) + world.random.nextInt(5);
            int m = world.getTopY(Heightmap.Type.WORLD_SURFACE, k, l);
            mutable.set(k, m, l);
            if (!world.isNearOccupiedPointOfInterest(mutable) || proximity >= 2) {
                if (world.isRegionLoaded(mutable.getX() - 10, mutable.getZ() - 10, mutable.getX() + 10,
                        mutable.getZ() + 10) && world.shouldTickEntityAt(mutable) &&
                        (RAVAGER_SPAWN_LOCATION.isSpawnPositionOk(world, mutable, EntityType.RAVAGER) ||
                        world.getBlockState(mutable.down()).isOf(Blocks.SNOW) && world.getBlockState(mutable).isAir())) {
                    return mutable;
                }
            }
        }
        return null;
    }

}
