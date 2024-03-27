package com.birblett.mixin.copper_bulb;

import com.birblett.impl.config.ConfigOptions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RedstoneLampBlock.class)
public abstract class RedstoneLampBlockMixin extends Block {

    @Unique private static final IntProperty OXIDATION = IntProperty.of("oxidation", 0, 4);
    @Unique private static final BooleanProperty POWERED = BooleanProperty.of("powered");
    @Unique private static final BooleanProperty WAXED = BooleanProperty.of("waxed");
    @Unique private static final BooleanProperty TRANSLATABLE = BooleanProperty.of("translatable");

    public RedstoneLampBlockMixin(Settings settings) {
        super(settings);
    }

    private static boolean isCopperBulb(BlockState state) {
        return state.contains(RedstoneLampBlockMixin.OXIDATION) && state.get(RedstoneLampBlockMixin.OXIDATION) > 0;
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static AbstractBlock.Settings modifyLuminance(AbstractBlock.Settings settings) {
        return settings.luminance((state) -> {
            if (state.get(RedstoneLampBlock.LIT)) {
                switch(state.get(RedstoneLampBlockMixin.OXIDATION)) {
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
        .solidBlock((state, view, pos) -> state.get(RedstoneLampBlockMixin.OXIDATION) == 0);
    }

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;with(Lnet/minecraft/state/property/Property;Ljava/lang/Comparable;)Ljava/lang/Object;"))
    private Object setDefaults(Object o) {
        return ((BlockState) o).with(RedstoneLampBlockMixin.OXIDATION, 0).with(RedstoneLampBlockMixin.POWERED,
                    false).with(RedstoneLampBlockMixin.WAXED, false).with(RedstoneLampBlockMixin
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
        return b.with(RedstoneLampBlockMixin.OXIDATION, oxidation).with(RedstoneLampBlockMixin.POWERED, false)
                .with(RedstoneLampBlockMixin.WAXED, waxed).with(RedstoneLampBlockMixin.TRANSLATABLE, (boolean)
                        ConfigOptions.USE_TRANSLATABLE_TEXT.value());
    }

    @Inject(method = "appendProperties", at = @At("HEAD"))
    private void addBulbProperties(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(RedstoneLampBlockMixin.OXIDATION).add(RedstoneLampBlockMixin.POWERED)
                .add(RedstoneLampBlockMixin.WAXED).add(RedstoneLampBlockMixin.TRANSLATABLE);
    }

}
