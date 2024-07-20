package com.birblett.mixin.mechanic;

import com.birblett.impl.config.ConfigOptions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BulbBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Allows copper bulbs to be activated on a game tick delay rather than instantly. See
 * {@link ConfigOptions#MECHANIC_COPPER_BULB_DELAY}.
 */
@Mixin(BulbBlock.class)
public abstract class BulbBlockMixin extends Block {

    public BulbBlockMixin(Settings settings) {
        super(settings);
    }

    @Inject(method = "neighborUpdate", at = @At("HEAD"), cancellable = true)
    protected void bulbDelayLogic(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify, CallbackInfo ci) {
        int delay = ConfigOptions.MECHANIC_COPPER_BULB_DELAY.getInt();
        if (delay > 0 && world instanceof ServerWorld) {
            boolean bl = world.isReceivingRedstonePower(pos);
            world.getWorldChunk(pos).setBlockState(pos, state.with(BulbBlock.POWERED, bl), false);
            world.updateListeners(pos, state, state.with(BulbBlock.POWERED, bl), Block.NOTIFY_ALL_AND_REDRAW);
            if (bl) {
                world.scheduleBlockTick(pos, this, delay);
            }
            ci.cancel();
        }
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (world instanceof ServerWorld) {
            ((BulbBlock) (Object) this).update(state, world, pos);
            world.playSound(null, pos, state.cycle(BulbBlock.LIT).get(BulbBlock.LIT) ?
                    SoundEvents.BLOCK_COPPER_BULB_TURN_ON : SoundEvents.BLOCK_COPPER_BULB_TURN_OFF,
                    SoundCategory.BLOCKS);
            world.setBlockState(pos, state.cycle(BulbBlock.LIT), Block.NOTIFY_ALL);
        }
    }

}
