package com.birblett.mixin.feature;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public class ChunkGeneratingMixin {

    @WrapOperation(method = "generateFeatures", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V"))
    private static void stopFeatureGeneration(ChunkGenerator instance, WorldGenLevel world, ChunkAccess chunk, StructureManager structureAccessor, Operation<Void> original) {
        if (!ConfigOptions.FEATURE_GENERATE_EMPTY_CHUNKS.val()) {
            original.call(instance, world, chunk, structureAccessor);
        }
    }

    @Inject(method = "generateFeatures", at = @At("TAIL"))
    private static void deleteWorld(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (ConfigOptions.FEATURE_GENERATE_EMPTY_CHUNKS.val()) {
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                sections[i] = new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)), sections[i].getBiomes());
            }
            for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                chunk.removeBlockEntity(pos);
            }
            chunk.setHeightmap(Heightmap.Types.WORLD_SURFACE, new long[37]);
        }
    }

}
