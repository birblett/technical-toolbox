package com.birblett.mixin.copper_bulb;

import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.Constant;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Provides all functionalities of copper bulbs
 */
@Mixin(RedstoneLampBlock.class)
public abstract class RedstoneLampBlockMixin extends Block implements Oxidizable {

    public RedstoneLampBlockMixin(Settings settings) {
        super(settings);
    }

    private static boolean isCopperBulb(BlockState state) {
        return state.contains(Constant.OXIDATION) && state.get(Constant.OXIDATION) > 0;
    }

    /**
     * Custom light level and solid-ness providers for bulbs only
     */
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static AbstractBlock.Settings modifyLuminance(AbstractBlock.Settings settings) {
        return settings.luminance((state) -> {
            if (state.get(RedstoneLampBlock.LIT)) {
                switch(state.get(Constant.OXIDATION)) {
                    case 1 -> {
                        return 15;
                    }
                    case 2 -> {
                        return 12;
                    }
                    case 3 -> {
                        return 8;
                    }
                    case 4 -> {
                        return 4;
                    }
                }
                return 15;
            }
            return 0;
        })
        .solidBlock((state, view, pos) -> state.get(Constant.OXIDATION) == 0);
    }

    /**
     * 3 mixins to set default properties
     */
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;with(Lnet/minecraft/state/property/Property;Ljava/lang/Comparable;)Ljava/lang/Object;"))
    private Object setDefaults(Object o) {
        return ((BlockState) o).with(Constant.OXIDATION, 0).with(Constant.POWERED,
                    false).with(Constant.WAXED, false).with(Constant
                .TRANSLATABLE, (boolean) ConfigOptions.USE_TRANSLATABLE_TEXT.value());
    }

    @ModifyReturnValue(method = "getPlacementState", at = @At("RETURN"))
    private BlockState applyBulbNbt(BlockState b, @Local ItemPlacementContext ctx) {
        NbtCompound nbt = ctx.getStack().getNbt();
        int oxidation = 0;
        boolean waxed = false;
        if (nbt != null) {
            oxidation = nbt.getInt("Oxidation");
            waxed = nbt.getBoolean("Waxed");
        }
        return b.with(Constant.OXIDATION, oxidation).with(Constant.POWERED, false)
                .with(Constant.WAXED, waxed).with(Constant.TRANSLATABLE, (boolean)
                        ConfigOptions.USE_TRANSLATABLE_TEXT.value());
    }

    @Inject(method = "appendProperties", at = @At("HEAD"))
    private void addBulbProperties(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(Constant.OXIDATION).add(Constant.POWERED)
                .add(Constant.WAXED).add(Constant.TRANSLATABLE);
    }


    /**
     * neighborUpdate copper bulb logic for both instant and delayed activation
     */
    @Inject(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;get(Lnet/minecraft/state/property/Property;)Ljava/lang/Comparable;"),
            cancellable = true)
    private void copperBulbNeighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify, CallbackInfo ci) {
        if (state.isOf(Blocks.REDSTONE_LAMP) && state.get(Constant.OXIDATION) > 0 && world instanceof ServerWorld) {
            int delay = (int) ConfigOptions.COPPER_BULB_DELAY.value();
            if (delay == 0) {
                this.update(state, (ServerWorld) world, pos);
            }
            else if (world.isReceivingRedstonePower(pos) != state.get(Constant.POWERED)) {
                world.scheduleBlockTick(pos, Blocks.REDSTONE_LAMP, delay);
            }
            ci.cancel();
        }
    }

    /**
     * Necessary to properly update state when placed
     */
    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (oldState.getBlock() != state.getBlock() && world instanceof ServerWorld serverWorld) {
            this.update(state, serverWorld, pos);
        }
    }

    /**
     * Allows for comparator output
     */
    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return state.get(Constant.OXIDATION) > 0;
    }

    /**
     * Output 15 if lit, else 0 (only when copper bulb)
     */
    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockState(pos).get(RedstoneLampBlock.LIT) ? 15 : 0;
    }

    /**
     * Random tick logic
     */
    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        this.tickDegradation(state, world, pos, random);
    }

    /**
     * Allow for random ticks if oxidation and waxing state allows for it
     */
    @Override
    public boolean hasRandomTicks(BlockState state) {
        int oxidation;
        return !state.get(Constant.WAXED) && (oxidation = state.get(Constant.OXIDATION)) < 4
                && oxidation > 0;
    }

    /**
     * Doesn't actually do anything, other logic will be handled in OxidizableMixin
     */
    @Override
    public OxidationLevel getDegradationLevel() {
        return OxidationLevel.UNAFFECTED;
    }

    /**
     * scheduledTick copper bulb logic from 23w45a, for delays > 0
     */
    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void copperBulbScheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (state.isOf(Blocks.REDSTONE_LAMP) && state.get(Constant.OXIDATION) > 0) {
            int delay = (int) ConfigOptions.COPPER_BULB_DELAY.value();
            if (delay > 0) {
                this.update(state, world, pos);
            }
            ci.cancel();
        }
    }

    /**
     * Toggles bulb state
     */
    public void update(BlockState state, ServerWorld world, BlockPos pos) {
        boolean bl = world.isReceivingRedstonePower(pos);
        if (bl == state.get(Constant.POWERED)) {
            return;
        }
        BlockState blockState = state;
        if (!state.get(Constant.POWERED)) {
            world.playSound(null, pos, (blockState = blockState.cycle(RedstoneLampBlock.LIT))
                    .get(RedstoneLampBlock.LIT) ? SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON : SoundEvents
                    .BLOCK_STONE_BUTTON_CLICK_OFF, SoundCategory.BLOCKS);
        }
        world.setBlockState(pos, blockState.with(Constant.POWERED, bl), Block.NOTIFY_ALL);
    }

}
