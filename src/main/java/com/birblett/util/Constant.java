package com.birblett.util;

import net.minecraft.block.enums.JigsawOrientation;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;

/**
 * Shared constants
 */
public class Constant {

    public static final BooleanProperty IS_CRAFTER = BooleanProperty.of("crafter");
    public static final BooleanProperty IS_CRAFTING = BooleanProperty.of("crafting");
    public static final EnumProperty<JigsawOrientation> ORIENTATION = Properties.ORIENTATION;

}
