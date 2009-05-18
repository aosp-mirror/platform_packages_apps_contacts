/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

/**
 * Simple extension to {@link ListView} that internally keeps track of scrolling
 * position and moves any currently displayed {@link FloatyWindow}.
 */
public class FloatyListView extends ListView {

    /**
     * Interface over to some sort of floating window that allows repositioning
     * to a specific screen location.
     */
    public static interface FloatyWindow {
        void showAt(int x, int y);
    }

    public FloatyListView(Context context) {
        super(context);
    }

    public FloatyListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatyListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private FloatyWindow mFloaty;
    private int mPosition;
    private int mAnchorY;

    /**
     * Show the given {@link FloatyWindow} around the given {@link ListView}
     * position. Specifically, it will keep the window aligned with the top of
     * that list position.
     */
    public void setFloatyWindow(FloatyWindow floaty, int position) {
        mFloaty = floaty;
        mPosition = position;

        if (mFloaty != null) {
            mAnchorY = getPositionY();
            mFloaty.showAt(0, mAnchorY);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void offsetChildrenTopAndBottom(int offset) {
        super.offsetChildrenTopAndBottom(offset);

        if (mFloaty != null) {
            mAnchorY += offset;
            mFloaty.showAt(0, mAnchorY);
        }
    }

    private int[] mLocation = new int[2];

    /**
     * Calculate the current Y location of the internally tracked position.
     */
    public int getPositionY() {
        return getPositionY(mPosition);
    }

    /**
     * Calculate the current Y location of the given list position, or -1 if
     * that position is offscreen.
     */
    public int getPositionY(int position) {
        final int firstVis = getFirstVisiblePosition();
        final int lastVis = getLastVisiblePosition();

        if (position >= firstVis && position < lastVis) {
            // Vertical position is just above position location
            View childView = getChildAt(position - firstVis);
            childView.getLocationOnScreen(mLocation);
            return mLocation[1];
        }
        return -1;
    }
}
