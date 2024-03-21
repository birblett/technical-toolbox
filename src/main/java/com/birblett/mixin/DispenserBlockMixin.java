package com.birblett.mixin;

import com.birblett.lib.CrafterInterface;
import com.birblett.lib.RecipeCache;
import com.birblett.util.ServerUtil;
import com.birblett.util.config.ConfigOptions;
import com.birblett.util.config.ConfigUtil;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Optional;

/**
 * Mostly ported over from 1.21 snapshot CrafterBlock stuff, with some custom behavior implemented for parity with
 * different snapshots
 */
@Mixin(DispenserBlock.class)
public class DispenserBlockMixin {

    @Shadow @Final public static DirectionProperty FACING;
    @Shadow @Final public static BooleanProperty TRIGGERED;
    @Unique private static final RecipeCache RECIPE_CACHE = new RecipeCache(10);

    @Unique private void restoreMarkers(ItemStack[] temp, CrafterInterface crafterInterface) {
        for (int slot = 0; slot < 9; slot++) {
            if (temp[slot] != null) {
                crafterInterface.setStack(slot, temp[slot]);
            }
        }
    }

    @Unique private void craft(BlockState state, ServerWorld world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CrafterInterface crafterInterface)) {
            return;
        }
        // use this disgusting hack to temporarily remove marker items
        ItemStack[] temp = new ItemStack[9];
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = crafterInterface.getStack(slot);
            if (ConfigUtil.crafterDisabled().test(stack)) {
                temp[slot] = stack;
                crafterInterface.setStack(slot, ItemStack.EMPTY);
            }
        }
        Optional<CraftingRecipe> optional = getCraftingRecipe(world, crafterInterface);
        if (optional.isEmpty()) {
            world.syncWorldEvent(WorldEvents.DISPENSER_FAILS, pos, 0);
            this.restoreMarkers(temp, crafterInterface);
            return;
        }
        CraftingRecipe craftingRecipe = optional.get();
        ItemStack itemStack = craftingRecipe.craft(crafterInterface, world.getRegistryManager());
        if (itemStack.isEmpty()) {
            world.syncWorldEvent(WorldEvents.DISPENSER_FAILS, pos, 0);
            this.restoreMarkers(temp, crafterInterface);
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
        crafterInterface.getInputStacks().forEach(stack -> {
            if (stack.isEmpty()) {
                return;
            }
            stack.decrement(1);
        });
        this.restoreMarkers(temp, crafterInterface);
        crafterInterface.markDirty();
    }

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

    @Unique private static Optional<CraftingRecipe> getCraftingRecipe(World world, RecipeInputInventory inputInventory) {
        return RECIPE_CACHE.getRecipe(world, inputInventory);
    }

    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void craftInstead(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (state.get(ServerUtil.IS_CRAFTER)) {
            this.craft(state, world, pos);
            ci.cancel();
        }
    }

    /**
     * Override default comparator output with crafter logic
     */
    @Inject(method = "getComparatorOutput", at = @At("HEAD"), cancellable = true)
    private void getCrafterComparatorOutput(BlockState state, World world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (state.get(ServerUtil.IS_CRAFTER) && blockEntity instanceof CrafterInterface crafterInterface) {
            cir.setReturnValue(crafterInterface.getComparatorOutput());
        }
    }

    /**
     * Disables quasi-power for crafters if matching config option is disabled
     */
    @ModifyVariable(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;get(Lnet/minecraft/state/property/Property;)Ljava/lang/Comparable;"), index = 7)
    private boolean disableQuasi(boolean b, @Local BlockState state, @Local World world, @Local(ordinal = 0) BlockPos pos) {
        if (state.get(ServerUtil.IS_CRAFTER) && !(Boolean) ConfigOptions.CRAFTER_QUASI_POWER.value()) {
            return b && world.isReceivingRedstonePower(pos);
        }
        return b;
    }

    /**
     * Set cooldown if matching config option is nonzero
     */
    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyArg(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;scheduleBlockTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;I)V"))
    private int modifyCrafterCooldown(int cd, @Local BlockState state) {
        if (state.get(ServerUtil.IS_CRAFTER) && (Integer) ConfigOptions.CRAFTER_COOLDOWN.value() > 0) {
            return (int) ConfigOptions.CRAFTER_COOLDOWN.value();
        }
        return cd;
    }

    /**
     * Instantly craft if matching config option is zero
     */
    @Inject(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;get(Lnet/minecraft/state/property/Property;)Ljava/lang/Comparable;"), cancellable = true, locals = LocalCapture.CAPTURE_FAILSOFT)
    private void crafterNoCooldown(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify, CallbackInfo ci, boolean bl) {
        if (state.get(ServerUtil.IS_CRAFTER) && ConfigOptions.CRAFTER_COOLDOWN.value().equals(0) && world instanceof ServerWorld) {
            if (bl && !state.get(TRIGGERED)) {
                world.setBlockState(pos, state.with(TRIGGERED, true), Block.NOTIFY_ALL);
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
    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void applyCrafterNbt(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        NbtCompound nbt;
        if ((nbt = ctx.getStack().getNbt()) != null && nbt.getBoolean("IsCrafter")) {
            cir.setReturnValue(cir.getReturnValue().with(ServerUtil.IS_CRAFTER, true));
        }
    }

    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void applyCrafterName(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (!itemStack.hasCustomName() && world.getBlockEntity(pos) instanceof DispenserBlockEntity blockEntity &&
                world.getBlockState(pos).get(ServerUtil.IS_CRAFTER)) {
            blockEntity.setCustomName(Text.of("Crafter"));
        }
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/DispenserBlock;setDefaultState(Lnet/minecraft/block/BlockState;)V"))
    private BlockState defaultCrafterProperty(BlockState defaultState) {
        return defaultState.with(ServerUtil.IS_CRAFTER, false);
    }

    @Inject(method = "appendProperties", at = @At("HEAD"))
    private void addCrafterProperty(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ServerUtil.IS_CRAFTER);
    }
}
