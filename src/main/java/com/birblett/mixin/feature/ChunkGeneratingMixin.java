package com.birblett.mixin.feature;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkGenerating.class)
public class ChunkGeneratingMixin {

    @WrapOperation(method = "generateFeatures", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/chunk/ChunkGenerator;generateFeatures(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/StructureAccessor;)V"))
    private static void stopFeatureGeneration(ChunkGenerator instance, StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor, Operation<Void> original) {
        if (!ConfigOptions.FEATURE_GENERATE_EMPTY_CHUNKS.val()) {
            original.call(instance, world, chunk, structureAccessor);
        }
    }

    @Inject(method = "generateFeatures", at = @At("TAIL"))
    private static void deleteWorld(ChunkGenerationContext context, ChunkGenerationStep step, BoundedRegionArray<AbstractChunkHolder> chunks, Chunk chunk, CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if (ConfigOptions.FEATURE_GENERATE_EMPTY_CHUNKS.val()) {
            ChunkSection[] sections = chunk.getSectionArray();
            for (int i = 0; i < sections.length; i++) {
                sections[i] = new ChunkSection(new PalettedContainer<>(Blocks.AIR.getDefaultState(), PaletteProvider.forBlockStates(Block.STATE_IDS)), sections[i].getBiomeContainer());
            }
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                chunk.removeBlockEntity(pos);
            }
            chunk.setHeightmap(Heightmap.Type.WORLD_SURFACE, new long[37]);
        }
    }

}
