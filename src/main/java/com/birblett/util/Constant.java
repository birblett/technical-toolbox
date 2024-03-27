package com.birblett.util;

import net.minecraft.block.enums.JigsawOrientation;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;

/**
 * Shared constants
 */
public class Constant {

    public static final BooleanProperty IS_CRAFTER = BooleanProperty.of("crafter");
    public static final BooleanProperty IS_CRAFTING = BooleanProperty.of("crafting");
    public static final EnumProperty<JigsawOrientation> ORIENTATION = Properties.ORIENTATION;

    /**
     * Oxidation determines type - 0 oxidation corresponds to redstone lamp, 1-4 correspond to copper bulbs of various
     * oxidation levels
     */
    public static final IntProperty OXIDATION = IntProperty.of("oxidation", 0, 4);
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final BooleanProperty WAXED = BooleanProperty.of("waxed");
    public static final BooleanProperty TRANSLATABLE = BooleanProperty.of("translatable");

}
