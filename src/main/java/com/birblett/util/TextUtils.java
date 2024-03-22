package com.birblett.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Provides common text-related utilities for all text across versions, primarily to account for 1.19 text changes
 */
public class TextUtils {

    public static MutableText formattable(String s) {
        return Text.literal(s);
    }

}
