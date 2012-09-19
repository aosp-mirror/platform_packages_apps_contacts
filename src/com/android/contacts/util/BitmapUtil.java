/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Provides static functions to decode bitmaps at the optimal size
 */
public class BitmapUtil {
    private BitmapUtil() {}

    /**
     * Returns Width or Height of the picture, depending on which size is smaller. Doesn't actually
     * decode the picture, so it is pretty efficient to run.
     */
    public static int getSmallerExtentFromBytes(byte[] bytes) {
        final BitmapFactory.Options options = new BitmapFactory.Options();

        // don't actually decode the picture, just return its bounds
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        // test what the best sample size is
        return Math.min(options.outWidth, options.outHeight);
    }

    /**
     * Finds the optimal sampleSize for loading the picture
     * @param originalSmallerExtent Width or height of the picture, whichever is smaller
     * @param targetExtent Width or height of the target view, whichever is bigger.
     *
     * If either one of the parameters is 0 or smaller, no sampling is applied
     */
    public static int findOptimalSampleSize(int originalSmallerExtent, int targetExtent) {
        // If we don't know sizes, we can't do sampling.
        if (targetExtent < 1) return 1;
        if (originalSmallerExtent < 1) return 1;

        // Test what the best sample size is. To do that, we find the sample size that gives us
        // the best trade-off between resulting image size and memory requirement. We allow
        // the down-sampled image to be 20% smaller than the target size. That way we can get around
        // unfortunate cases where e.g. a 720 picture is requested for 362 and not down-sampled at
        // all. Why 20%? Why not. Prove me wrong.
        int extent = originalSmallerExtent;
        int sampleSize = 1;
        while ((extent >> 1) >= targetExtent * 0.8f) {
            sampleSize <<= 1;
            extent >>= 1;
        }

        return sampleSize;
    }

    /**
     * Decodes the bitmap with the given sample size
     */
    public static Bitmap decodeBitmapFromBytes(byte[] bytes, int sampleSize) {
        final BitmapFactory.Options options;
        if (sampleSize <= 1) {
            options = null;
        } else {
            options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
