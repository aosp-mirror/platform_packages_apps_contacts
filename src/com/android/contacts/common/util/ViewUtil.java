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

package com.android.contacts.common.util;

import android.content.res.Resources;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.R;

/**
 * Provides static functions to work with views
 */
public class ViewUtil {
    private ViewUtil() {}

    /**
     * Returns the width as specified in the LayoutParams
     * @throws IllegalStateException Thrown if the view's width is unknown before a layout pass
     * s
     */
    public static int getConstantPreLayoutWidth(View view) {
        // We haven't been layed out yet, so get the size from the LayoutParams
        final ViewGroup.LayoutParams p = view.getLayoutParams();
        if (p.width < 0) {
            throw new IllegalStateException("Expecting view's width to be a constant rather " +
                    "than a result of the layout pass");
        }
        return p.width;
    }

    /**
     * Returns a boolean indicating whether or not the view's layout direction is RTL
     *
     * @param view - A valid view
     * @return True if the view's layout direction is RTL
     */
    public static boolean isViewLayoutRtl(View view) {
        return view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Configures the floating action button, clipping it to a circle and setting its translation z.
     * @param view The float action button's view.
     * @param res The resources file.
     */
    public static void setupFloatingActionButton(View view, Resources res) {
        // Once layout is complete and the floating action button has been assigned a width and
        // height, assign the outline.
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                final Outline outline = new Outline();
                final int minDimension = Math.min(right - left, bottom - top);
                if (minDimension <= 0) {
                    return;
                }
                outline.setRoundRect(0, 0, right - left, bottom - top, minDimension / 2);
                v.setOutline(outline);
                v.setClipToOutline(true);
            }
        });
        view.setTranslationZ(
                res.getDimensionPixelSize(R.dimen.floating_action_button_translation_z));
    }
}
