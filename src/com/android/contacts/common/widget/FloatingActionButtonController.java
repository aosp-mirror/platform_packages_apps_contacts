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

package com.android.contacts.common.widget;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.View;
import android.widget.ImageButton;

import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.common.R;

/**
 * Controls the movement and appearance of the FAB (Floating Action Button).
 */
public class FloatingActionButtonController {
    public static final int ALIGN_MIDDLE = 0;
    public static final int ALIGN_QUARTER_END = 1;
    public static final int ALIGN_END = 2;

    private final int mAnimationDuration;
    private final int mFloatingActionButtonWidth;
    private final int mFloatingActionButtonMarginRight;
    private final View mFloatingActionButtonContainer;
    private final Interpolator mFabInterpolator;
    private int mScreenWidth;

    public FloatingActionButtonController(Activity activity, View container) {
        Resources resources = activity.getResources();
        mFabInterpolator = AnimationUtils.loadInterpolator(activity,
                android.R.interpolator.fast_out_slow_in);
        mFloatingActionButtonWidth = resources.getDimensionPixelSize(
                R.dimen.floating_action_button_width);
        mFloatingActionButtonMarginRight = resources.getDimensionPixelOffset(
                R.dimen.floating_action_button_margin_right);
        mAnimationDuration = resources.getInteger(
                R.integer.floating_action_button_animation_duration);
        mFloatingActionButtonContainer = container;
        ViewUtil.setupFloatingActionButton(mFloatingActionButtonContainer, resources);
    }

    /**
     * Passes the screen width into the class. Necessary for translation calculations.
     * Should be called as soon as parent View width is available.
     *
     * @param screenWidth The width of the screen in pixels.
     */
    public void setScreenWidth(int screenWidth) {
        mScreenWidth = screenWidth;
    }

    /**
     * Sets FAB as View.VISIBLE or View.GONE.
     *
     * @param visible Whether or not to make the container visible.
     */
    public void setVisible(boolean visible) {
        mFloatingActionButtonContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Updates the FAB location (middle to right position) as the PageView scrolls.
     *
     * @param positionOffset A fraction used to calculate position of the FAB during page scroll.
     */
    public void onPageScrolled(float positionOffset) {
        // As the page is scrolling, if we're on the first tab, update the FAB position so it
        // moves along with it.
        mFloatingActionButtonContainer.setTranslationX(
                (int) (positionOffset * getTranslationXForAlignment(ALIGN_END)));
        mFloatingActionButtonContainer.setTranslationY(0);
    }

    /**
     * Aligns the FAB to the described location plus specified additional offsets.
     *
     * @param align One of ALIGN_MIDDLE, ALIGN_QUARTER_RIGHT, or ALIGN_RIGHT.
     * @param offsetX Additional offsetX to translate by.
     * @param offsetY Additional offsetY to translate by.
     * @param animate Whether or not to animate the transition.
     */
    public void align(int align, int offsetX, int offsetY, boolean animate) {
        if (mScreenWidth == 0) return;
        int translationX = getTranslationXForAlignment(align);
        if (animate) {
            mFloatingActionButtonContainer.animate()
                    .translationX(translationX + offsetX)
                    .translationY(offsetY)
                    .setInterpolator(mFabInterpolator)
                    .setDuration(mAnimationDuration).start();
        } else {
            mFloatingActionButtonContainer.setTranslationX(translationX + offsetX);
            mFloatingActionButtonContainer.setTranslationY(offsetY);
        }
    }

    /**
     * Calculates the X offset of the FAB to the given alignment, adjusted for whether or not the
     * view is in RTL mode.
     *
     * @param align One of ALIGN_MIDDLE, ALIGN_QUARTER_RIGHT, or ALIGN_RIGHT.
     * @return The translationX for the given alignment.
     */
    public int getTranslationXForAlignment(int align) {
        int result = 0;
        switch (align) {
            case ALIGN_MIDDLE:
                // Moves the FAB to exactly center screen.
                return 0;
            case ALIGN_QUARTER_END:
                // Moves the FAB a quarter of the screen width.
                result = mScreenWidth / 4;
                break;
            case ALIGN_END:
                // Moves the FAB half the screen width. Same as aligning right with a marginRight.
                result = mScreenWidth / 2
                        - mFloatingActionButtonWidth / 2
                        - mFloatingActionButtonMarginRight;
                break;
        }
        if (isLayoutRtl()) {
            result *= -1;
        }
        return result;
    }

    /**
     * Manually translates the FAB without animation.
     *
     * @param translationX The x distance to translate.
     * @param translationY The y distance to translate.
     */
    public void manuallyTranslate(int translationX, int translationY) {
        mFloatingActionButtonContainer.setTranslationX(translationX);
        mFloatingActionButtonContainer.setTranslationY(translationY);
    }

    private boolean isLayoutRtl() {
        return mFloatingActionButtonContainer.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
