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

package com.android.contacts.dialpad;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageButton;

/**
 * Custom {@link ImageButton} for dialpad buttons.
 * <p>
 * This class implements lift-to-type interaction when touch exploration is
 * enabled.
 */
public class DialpadImageButton extends ImageButton {
    /** Accessibility manager instance used to check touch exploration state. */
    private AccessibilityManager mAccessibilityManager;

    /** Bounds used to filter HOVER_EXIT events. */
    private Rect mHoverBounds = new Rect();

    public interface OnPressedListener {
        public void onPressed(View view, boolean pressed);
    }

    private OnPressedListener mOnPressedListener;

    public void setOnPressedListener(OnPressedListener onPressedListener) {
        mOnPressedListener = onPressedListener;
    }

    public DialpadImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initForAccessibility(context);
    }

    public DialpadImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initForAccessibility(context);
    }

    private void initForAccessibility(Context context) {
        mAccessibilityManager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (mOnPressedListener != null) {
            mOnPressedListener.onPressed(this, pressed);
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mHoverBounds.left = getPaddingLeft();
        mHoverBounds.right = w - getPaddingRight();
        mHoverBounds.top = getPaddingTop();
        mHoverBounds.bottom = h - getPaddingBottom();
    }

    @Override
    public boolean performClick() {
        // When accessibility is on, simulate press and release to preserve the
        // semantic meaning of performClick(). Required for Braille support.
        if (mAccessibilityManager.isEnabled()) {
            // Checking the press state prevents double activation.
            if (!isPressed()) {
                setPressed(true);
                setPressed(false);
            }

            return true;
        }

        return super.performClick();
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        // When touch exploration is turned on, lifting a finger while inside
        // the button's hover target bounds should perform a click action.
        if (mAccessibilityManager.isEnabled()
                && mAccessibilityManager.isTouchExplorationEnabled()) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    // Lift-to-type temporarily disables double-tap activation.
                    setClickable(false);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    if (mHoverBounds.contains((int) event.getX(), (int) event.getY())) {
                        performClick();
                    }
                    setClickable(true);
                    break;
            }
        }

        return super.onHoverEvent(event);
    }
}
