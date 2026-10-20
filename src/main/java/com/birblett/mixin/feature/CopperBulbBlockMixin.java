package com.birblett.mixin.feature;

import com.birblett.impl.config.ConfigOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Allows copper bulbs to be activated on a game tick delay rather than instantly. Also allows the block updates from
 * powering to be ignored. See {@link ConfigOptions#FEATURE_COPPER_BULB_DELAY} and
 * {@link ConfigOptions#FEATURE_COPPER_BULB_NO_POWERED_UPDATES}.
 */
@Mixin(CopperBulbBlock.class)
public abstract class CopperBulbBlockMixin extends Block {

    public CopperBulbBlockMixin(Properties settings) {
        super(settings);
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    protected void bulbDelayLogic(BlockState state, Level world, BlockPos pos, Block sourceBlock, @Nullable Orientation wireOrientation, boolean notify, CallbackInfo ci) {
        int delay = ConfigOptions.FEATURE_COPPER_BULB_DELAY.val();
        if (delay > 0 && world instanceof ServerLevel) {
            boolean bl = world.hasNeighborSignal(pos);
            if (ConfigOptions.FEATURE_COPPER_BULB_NO_POWERED_UPDATES.val()) {
                world.getChunkAt(pos).setBlockState(pos, state.setValue(CopperBulbBlock.POWERED, bl), Block.UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE);
                world.sendBlockUpdated(pos, state, state.setValue(CopperBulbBlock.POWERED, bl), Block.UPDATE_ALL_IMMEDIATE);
            } else {
                world.setBlock(pos, state.setValue(CopperBulbBlock.POWERED, bl), Block.UPDATE_ALL);
            }
            if (bl) {
                world.scheduleTick(pos, this, delay);
            }
            ci.cancel();
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world instanceof ServerLevel) {
            ((CopperBulbBlock) (Object) this).checkAndFlip(state, world, pos);
            world.playSound(null, pos, state.cycle(CopperBulbBlock.LIT).getValue(CopperBulbBlock.LIT) ?
                            SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
                    SoundSource.BLOCKS);
            world.setBlock(pos, state.cycle(CopperBulbBlock.LIT), Block.UPDATE_ALL);
        }
    }

}
