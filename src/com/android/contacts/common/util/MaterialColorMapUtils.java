/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.common.util;

import android.graphics.Color;
import android.os.Trace;

public class MaterialColorMapUtils {

    /**
     * Values from the extended Material color palette. 500 values are chosen when they meet
     * GAR accessibility requirements as a background for white text. Darker versions from the
     * extended palette are chosen when the 500 values don't meet GAR requirements (see b/16159407).
     */
    private static final int PRIMARY_COLORS[] = {0xFFDB4437, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
            0xFF3F51B5, 0xFF4285F4, 0xFF039BE5, 0xFF0097A7, 0xFF009688, 0xFF0F9D58, 0xFF689F38,
            0xFFEF6C00, 0xFFFF5722, 0xFF757575, 0xFF607D8B};

    /**
     * Darker versions of the colors in PRIMARY_COLORS. Two shades darker.
     */
    private static final int SECONDARY_COLORS[] = {0xFFC53929, 0xFFC2185B, 0xFF7B1FA2,
            0xFF512DA8, 0xFF303F9F, 0xFF3367D6, 0xFF0277BD, 0xFF006064, 0xFF00796B, 0xFF0B8043,
            0xFF33691E, 0xFFE65100, 0xFFE64A19, 0xFF424242, 0xFF455A64};

    public static class MaterialPalette {
        public MaterialPalette(int primaryColor, int secondaryColor) {
            mPrimaryColor = primaryColor;
            mSecondaryColor = secondaryColor;
        }
        public final int mPrimaryColor;
        public final int mSecondaryColor;
    }

    /**
     * Return primary and secondary colors from the Material color
     * palette that are similar to {@param color}.
     */
    public static MaterialPalette calculatePrimaryAndSecondaryColor(int color) {
        Trace.beginSection("calculatePrimaryAndSecondaryColor");

        // TODO: check matches with known LetterTileDrawable colors, once they are material colors

        final float colorHue = hue(color);
        float minimumDistance = Float.MAX_VALUE;
        int indexBestMatch = 0;
        for (int i = 0; i < PRIMARY_COLORS.length; i++) {
            final float comparedHue = hue(PRIMARY_COLORS[i]);
            // No need to be perceptually accurate when calculating color distances since
            // we are only mapping to 15 colors. Being slightly inaccurate isn't going to change
            // the mapping very often.
            final float distance = Math.abs(comparedHue - colorHue);
            if (distance < minimumDistance) {
                minimumDistance = distance;
                indexBestMatch = i;
            }
        }

        Trace.endSection();
        return new MaterialPalette(PRIMARY_COLORS[indexBestMatch],
                SECONDARY_COLORS[indexBestMatch]);
    }

    /**
     * Given a primary color, output a secondary color. Ideally, this function would use the exact
     * Material palette secondary color that corresponds with {@param primaryColor}.
     */
    // TODO: update to use a LUT, once primaryColor is gauranteed to be from Material palette
    public static MaterialPalette calculateSecondaryColor(int primaryColor) {
        // Arbitrarily chosen constant.
        final float hsv[] = new float[3];
        final float SYSTEM_BAR_BRIGHTNESS_FACTOR = 0.8f;
        // Create a darker version of the actionbar color. HSV is device dependent
        // and not perceptually-linear. Therefore, we can't say mStatusBarColor is
        // 70% as bright as the action bar color. We can only say: it is a bit darker.
        Color.colorToHSV(primaryColor, hsv);
        hsv[2] *= SYSTEM_BAR_BRIGHTNESS_FACTOR;
        return new MaterialPalette(primaryColor, Color.HSVToColor(hsv));
    }

    /**
     * Returns the hue component of a color int.
     *
     * @return A value between 0.0f and 1.0f
     */
    public static float hue(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int V = Math.max(b, Math.max(r, g));
        int temp = Math.min(b, Math.min(r, g));

        float H;

        if (V == temp) {
            H = 0;
        } else {
            final float vtemp = V - temp;
            final float cr = (V - r) / vtemp;
            final float cg = (V - g) / vtemp;
            final float cb = (V - b) / vtemp;

            if (r == V) {
                H = cb - cg;
            } else if (g == V) {
                H = 2 + cr - cb;
            } else {
                H = 4 + cg - cr;
            }

            H /= 6.f;
            if (H < 0) {
                H++;
            }
        }

        return H;
    }
}
