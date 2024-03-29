package com.birblett.mixin.crafter;

import com.birblett.impl.config.ConfigOptions;
import com.birblett.lib.crafter.CrafterInterface;
import com.birblett.lib.crafter.RecipeCache;
import com.birblett.util.Constant;
import com.birblett.util.TextUtils;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.*;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.Orientation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Style;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Optional;

/**
 * Mostly ported over from 1.21 snapshot CrafterBlock stuff, with some custom behavior implemented for parity with
 * different snapshots
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin extends BlockWithEntity implements BlockEntityProvider {

    @Shadow @Final public static DirectionProperty FACING;
    @Shadow @Final public static BooleanProperty TRIGGERED;
    @Unique private static final RecipeCache RECIPE_CACHE = new RecipeCache(10);

    protected DispenserBlockMixin(Settings settings) {
        super(settings);
    }

    /**
     * Slightly tweaked craft logic from snapshots
     */
    @Unique private void craft(BlockState state, ServerWorld world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CrafterInterface crafterInterface)) {
            return;
        }
        // use this disgusting hack to temporarily remove marker items
        ItemStack[] temp = crafterInterface.removeMarkers();
        // actual crafting logic
        Optional<CraftingRecipe> optional = RECIPE_CACHE.getRecipe(world, crafterInterface);
        if (optional.isEmpty()) {
            world.syncWorldEvent(WorldEvents.DISPENSER_FAILS, pos, 0);
            crafterInterface.restoreMarkers(temp);
            return;
        }
        CraftingRecipe craftingRecipe = optional.get();
        ItemStack itemStack = craftingRecipe.craft(crafterInterface, world.getRegistryManager());
        if (itemStack.isEmpty()) {
            world.syncWorldEvent(WorldEvents.DISPENSER_FAILS, pos, 0);
            crafterInterface.restoreMarkers(temp);
            return;
        }
        // idk wtf onCraft does in the snapshot someone please tell me
        // itemStack.getItem().onCraft(itemStack, world);
        this.transferOrSpawnStack(world, pos, crafterInterface, itemStack, state);
        for (ItemStack itemStack2 : craftingRecipe.getRemainder(crafterInterface)) {
            if (itemStack2.isEmpty()) continue;
            this.transferOrSpawnStack(world, pos, crafterInterface, itemStack2, state);
        }
        // restore marker items after disgusting hack from earlier
        crafterInterface.getHeldStacks().forEach(stack -> {
            if (stack.isEmpty()) {
                return;
            }
            stack.decrement(1);
        });
        ((CrafterInterface) blockEntity).setCraftingTicks(6);
        world.setBlockState(pos, state.with(Constant.IS_CRAFTING, true), Block.NOTIFY_ALL);
        crafterInterface.restoreMarkers(temp);
        crafterInterface.markDirty();
    }

    /**
     * Copied dispensing logic
     */
    @Unique private void transferOrSpawnStack(World world, BlockPos pos, CrafterInterface blockEntity, ItemStack stack, BlockState state) {
        Direction direction = state.get(FACING);
        Inventory inventory = HopperBlockEntity.getInventoryAt(world, pos.offset(direction));
        ItemStack itemStack = stack.copy();
        if (inventory != null && (inventory instanceof CrafterInterface || stack.getCount() > inventory.getMaxCountPerStack())) {
            while (!itemStack.isEmpty() && HopperBlockEntity.transfer(blockEntity, inventory, itemStack.copyWithCount(1), direction.getOpposite()).isEmpty()) {
                itemStack.decrement(1);
            }
        } else if (inventory != null) {
            while (!itemStack.isEmpty() && itemStack.getCount() != (itemStack = HopperBlockEntity.transfer(blockEntity, inventory, itemStack, direction.getOpposite())).getCount()) {}
        }
        if (!itemStack.isEmpty()) {
            Vec3d vec3d = Vec3d.ofCenter(pos).offset(direction, 0.7);
            ItemDispenserBehavior.spawnItem(world, itemStack, 6, direction, vec3d);
            world.syncWorldEvent(WorldEvents.DISPENSER_DISPENSES, pos, 0);
            world.syncWorldEvent(WorldEvents.DISPENSER_DISPENSES, pos, direction.getId());
        }
    }

    /**
     * Craft on scheduled tick, only called if crafting is not instant
     */
    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void craftInstead(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (state.get(Constant.IS_CRAFTER)) {
            this.craft(state, world, pos);
            ci.cancel();
        }
    }

    /**
     * Override default comparator output with crafter logic
     */
    @ModifyReturnValue(method = "getComparatorOutput", at = @At("RETURN"))
    private int getCrafterComparatorOutput(int out, @Local BlockState state, @Local World world, @Local BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (state.get(Constant.IS_CRAFTER) && blockEntity instanceof CrafterInterface crafterInterface) {
            return crafterInterface.getComparatorOutput();
        }
        return out;
    }

    /**
     * Disables quasi-power for crafters if matching config option is disabled
     */
    @ModifyExpressionValue(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isReceivingRedstonePower(Lnet/minecraft/util/math/BlockPos;)Z", ordinal = 1))
    private boolean disableQuasi(boolean b, @Local BlockState state, @Local World world, @Local(ordinal = 0) BlockPos pos) {
        if (state.get(Constant.IS_CRAFTER) && !(Boolean) ConfigOptions.CRAFTER_QUASI_POWER.value()) {
            return false;
        }
        return b;
    }

    /**
     * Set cooldown if matching config option is nonzero
     */
    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyArg(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;scheduleBlockTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;I)V"))
    private int modifyCrafterCooldown(int cd, @Local BlockState state) {
        if (state.get(Constant.IS_CRAFTER) && (Integer) ConfigOptions.CRAFTER_COOLDOWN.value() > 0) {
            return (int) ConfigOptions.CRAFTER_COOLDOWN.value();
        }
        return cd;
    }

    /**
     * Next two methods force crafter to notify client on state change
     */
    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyArg(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z",
            ordinal = 0))
    private int forceNotify1(int notify, @Local BlockState state) {
        if (state.get(Constant.IS_CRAFTER) && (Integer) ConfigOptions.CRAFTER_COOLDOWN.value() > 0) {
            return Block.NOTIFY_ALL;
        }
        return notify;
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyArg(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z",
            ordinal = 1))
    private int forceNotify2(int notify, @Local BlockState state) {
        if (state.get(Constant.IS_CRAFTER) && (Integer) ConfigOptions.CRAFTER_COOLDOWN.value() > 0) {
            return Block.NOTIFY_ALL;
        }
        return notify;
    }

    /**
     * Instantly craft if matching config option is zero
     */
    @Inject(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;get(Lnet/minecraft/state/property/Property;)Ljava/lang/Comparable;"), cancellable = true, locals = LocalCapture.CAPTURE_FAILSOFT)
    private void crafterNoCooldown(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify, CallbackInfo ci, boolean bl) {
        if (state.get(Constant.IS_CRAFTER) && ConfigOptions.CRAFTER_COOLDOWN.value().equals(0) && world instanceof ServerWorld) {
            if (bl && !state.get(TRIGGERED)) {
                state = state.with(TRIGGERED, true);
                world.setBlockState(pos, state, Block.NOTIFY_ALL);
                this.craft(state, (ServerWorld) world, pos);
            }
            else if (!bl) {
                world.setBlockState(pos, state.with(TRIGGERED, false), Block.NOTIFY_ALL);
            }
            ci.cancel();
        }
    }

    /**
     * Apply crafter property to placed blocks with matching nbt
     */
    @ModifyReturnValue(method = "getPlacementState", at = @At("RETURN"))
    private BlockState applyCrafterNbt(BlockState b, @Local ItemPlacementContext ctx) {
        NbtCompound nbt;
        if ((nbt = ctx.getStack().getNbt()) != null && nbt.getInt("CustomModelData") == 13579) {
            Direction direction = ctx.getPlayerLookDirection().getOpposite();
            Direction direction2 = switch (direction) {
                case DOWN -> ctx.getHorizontalPlayerFacing().getOpposite();
                case UP -> ctx.getHorizontalPlayerFacing();
                case NORTH, SOUTH, WEST, EAST -> Direction.UP;
            };
            b = b.with(Constant.IS_CRAFTER, true).with(Constant.ORIENTATION, Orientation.byDirections(direction, direction2));
        }
        return b;
    }

    /**
     * Provides ticker to properly reset the crafting state
     */
    @Override @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : state.get(Constant.IS_CRAFTER) ? DispenserBlock.validateTicker(type, BlockEntityType
                .DROPPER, CrafterInterface::tickCrafting) : null;
    }

    /**
     * Applies custom name to crafters
     */
    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void applyCrafterName(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (world.getBlockEntity(pos) instanceof DispenserBlockEntity blockEntity && world.getBlockState(pos).get(Constant
                .IS_CRAFTER)) {
            if ((Boolean) ConfigOptions.USE_TRANSLATABLE_TEXT.value()) {
                blockEntity.setCustomName(TextUtils.translatable("container.crafter").setStyle(Style.EMPTY.withItalic(false)));
            }
            else {
                blockEntity.setCustomName(TextUtils.formattable("Crafter").setStyle(Style.EMPTY.withItalic(false)));
            }
        }
    }

    /**
     * State initializers
     */
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/DispenserBlock;setDefaultState(Lnet/minecraft/block/BlockState;)V"))
    private BlockState defaultCrafterProperty(BlockState defaultState) {
        return defaultState.with(Constant.IS_CRAFTER, false).with(Constant.ORIENTATION, Orientation.NORTH_UP)
                .with(Constant.IS_CRAFTING, false);
    }

    @Inject(method = "appendProperties", at = @At("HEAD"))
    private void addCrafterProperty(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(Constant.IS_CRAFTER).add(Constant.ORIENTATION).add(Constant.IS_CRAFTING);
    }

    /**
     * Removes barrier blocks from crafters on break. Maybe doing additional nbt check is necessary... if you're putting
     * barriers in crafters for some reason
     */
    @Inject(method = "onStateReplaced", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ItemScatterer;onStateReplaced(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"))
    private void removeDisabledSlots(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        Optional<DropperBlockEntity> b = world.getBlockEntity(pos, BlockEntityType.DROPPER);
        if (b.isPresent() && state.get(Constant.IS_CRAFTER)) {
            DropperBlockEntity dropper = b.get();
            for (int i = 0; i < 9; i++) {
                if (dropper.getStack(i).isOf(Items.BARRIER)) {
                    dropper.setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }
}
