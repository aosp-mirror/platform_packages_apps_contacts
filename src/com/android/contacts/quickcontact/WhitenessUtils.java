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


package com.android.contacts.quickcontact;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Trace;

/**
 * Utility class for determining whether Bitmaps contain a lot of white pixels in locations
 * where QuickContactActivity will want to place white text or buttons.
 *
 * This class liberally considers bitmaps white. All constants are chosen with a small amount of
 * experimentation. Despite a lack of rigour, this class successfully allows QuickContactsActivity
 * to detect when Bitmap are obviously *not* white. Therefore, it is better than nothing.
 */
public class WhitenessUtils {

    /**
     * Analyze this amount of the top and bottom of the bitmap.
     */
    private static final float HEIGHT_PERCENT_ANALYZED = 0.2f;

    /**
     * An image with more than this amount white, is considered to be a whitish image.
     */
    private static final float PROPORTION_WHITE_CUTOFF = 0.1f;

    private static final float THIRD = 0.33f;

    /**
     * Colors with luma greater than this are considered close to white. This value is lower than
     * the value used in Palette's ColorUtils, since we want to liberally declare images white.
     */
    private static final float LUMINANCE_OF_WHITE =  0.90f;

    /**
     * Returns true if 20% of the image's top right corner is white, or 20% of the bottom
     * of the image is white.
     */
    public static boolean isBitmapWhiteAtTopOrBottom(Bitmap largeBitmap) {
        Trace.beginSection("isBitmapWhiteAtTopOrBottom");
        try {
            final Bitmap smallBitmap = scaleBitmapDown(largeBitmap);

            final int[] rgbPixels = new int[smallBitmap.getWidth() * smallBitmap.getHeight()];
            smallBitmap.getPixels(rgbPixels, 0, smallBitmap.getWidth(), 0, 0,
                    smallBitmap.getWidth(), smallBitmap.getHeight());

            // look at top right corner of the bitmap
            int whiteCount = 0;
            for (int y = 0; y < smallBitmap.getHeight() * HEIGHT_PERCENT_ANALYZED; y++) {
                for (int x = (int) (smallBitmap.getWidth() * (1 - THIRD));
                        x < smallBitmap.getWidth(); x++) {
                    final int rgb = rgbPixels[y * smallBitmap.getWidth() + x];
                    if (isWhite(rgb)) {
                        whiteCount ++;
                    }
                }
            }
            int totalPixels = (int) (smallBitmap.getHeight() * smallBitmap.getWidth()
                    * THIRD * HEIGHT_PERCENT_ANALYZED);
            if (whiteCount / (float) totalPixels > PROPORTION_WHITE_CUTOFF) {
                return true;
            }

            // look at bottom portion of bitmap
            whiteCount = 0;
            for (int y = (int) (smallBitmap.getHeight() * (1 - HEIGHT_PERCENT_ANALYZED));
                    y <  smallBitmap.getHeight(); y++) {
                for (int x = 0; x < smallBitmap.getWidth(); x++) {
                    final int rgb = rgbPixels[y * smallBitmap.getWidth() + x];
                    if (isWhite(rgb)) {
                        whiteCount ++;
                    }
                }
            }

            totalPixels = (int) (smallBitmap.getHeight()
                    * smallBitmap.getWidth() * HEIGHT_PERCENT_ANALYZED);

            return whiteCount / (float) totalPixels > PROPORTION_WHITE_CUTOFF;
        } finally {
            Trace.endSection();
        }
    }

    private static boolean isWhite(int rgb) {
        return calculateXyzLuma(rgb) > LUMINANCE_OF_WHITE;
    }

    private static float calculateXyzLuma(int rgb) {
        return (0.2126f * Color.red(rgb) +
                0.7152f * Color.green(rgb) +
                0.0722f * Color.blue(rgb)) / 255f;
    }

    /**
     * Scale down the bitmap in order to make color analysis faster. Taken from Palette.
     */
    private static Bitmap scaleBitmapDown(Bitmap bitmap) {
        final int CALCULATE_BITMAP_MIN_DIMENSION = 100;
        final int minDimension = Math.min(bitmap.getWidth(), bitmap.getHeight());

        if (minDimension <= CALCULATE_BITMAP_MIN_DIMENSION) {
            // If the bitmap is small enough already, just return it
            return bitmap;
        }

        final float scaleRatio = CALCULATE_BITMAP_MIN_DIMENSION / (float) minDimension;
        return Bitmap.createScaledBitmap(bitmap,
                Math.round(bitmap.getWidth() * scaleRatio),
                Math.round(bitmap.getHeight() * scaleRatio),
                false);
    }
}
