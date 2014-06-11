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

package com.android.contacts.common.dialpad;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.android.contacts.common.animation.AnimUtils;

/**
 * View that displays a twelve-key phone dialpad.
 */
public class DialpadView extends LinearLayout {
    private static final String TAG = DialpadView.class.getSimpleName();

    private static final double DELAY_MULTIPLIER = 0.66;
    private static final double DURATION_MULTIPLIER = 0.8;

    private EditText mDigits;
    private ImageButton mDelete;
    private View mOverflowMenuButton;
    private ColorStateList mRippleColor;

    private boolean mCanDigitsBeEdited;

    private final int[] mButtonIds = new int[] {R.id.zero, R.id.one, R.id.two, R.id.three,
            R.id.four, R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.star,
            R.id.pound};

    // For animation.
    private static final int KEY_FRAME_DURATION = 33;

    private int mTranslateDistance;

    public DialpadView(Context context) {
        this(context, null);
    }

    public DialpadView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DialpadView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Dialpad);
        mRippleColor = a.getColorStateList(R.styleable.Dialpad_dialpad_key_button_touch_tint);
        a.recycle();

        mTranslateDistance = getResources().getDimensionPixelSize(
                R.dimen.dialpad_key_button_translate_y);
    }

    @Override
    protected void onFinishInflate() {
        setupKeypad();
        mDigits = (EditText) findViewById(R.id.digits);
        mDelete = (ImageButton) findViewById(R.id.deleteButton);
        mOverflowMenuButton = findViewById(R.id.dialpad_overflow);
    }

    private void setupKeypad() {
        final int[] numberIds = new int[] {R.string.dialpad_0_number, R.string.dialpad_1_number,
                R.string.dialpad_2_number, R.string.dialpad_3_number, R.string.dialpad_4_number,
                R.string.dialpad_5_number, R.string.dialpad_6_number, R.string.dialpad_7_number,
                R.string.dialpad_8_number, R.string.dialpad_9_number, R.string.dialpad_star_number,
                R.string.dialpad_pound_number};

        final int[] letterIds = new int[] {R.string.dialpad_0_letters, R.string.dialpad_1_letters,
                R.string.dialpad_2_letters, R.string.dialpad_3_letters, R.string.dialpad_4_letters,
                R.string.dialpad_5_letters, R.string.dialpad_6_letters, R.string.dialpad_7_letters,
                R.string.dialpad_8_letters, R.string.dialpad_9_letters,
                R.string.dialpad_star_letters, R.string.dialpad_pound_letters};

        final Resources resources = getContext().getResources();

        DialpadKeyButton dialpadKey;
        TextView numberView;
        TextView lettersView;

        for (int i = 0; i < mButtonIds.length; i++) {
            dialpadKey = (DialpadKeyButton) findViewById(mButtonIds[i]);
            numberView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_number);
            lettersView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_letters);
            final String numberString = resources.getString(numberIds[i]);
            final RippleDrawable rippleBackground =
                    (RippleDrawable) resources.getDrawable(R.drawable.btn_dialpad_key);
            if (mRippleColor != null) {
                rippleBackground.setColor(mRippleColor);
            }

            numberView.setText(numberString);
            numberView.setElegantTextHeight(false);
            dialpadKey.setContentDescription(numberString);
            dialpadKey.setBackground(rippleBackground);

            if (lettersView != null) {
                lettersView.setText(resources.getString(letterIds[i]));
            }
        }

        final DialpadKeyButton one = (DialpadKeyButton) findViewById(R.id.one);
        one.setLongHoverContentDescription(
                resources.getText(R.string.description_voicemail_button));

        final DialpadKeyButton zero = (DialpadKeyButton) findViewById(R.id.zero);
        zero.setLongHoverContentDescription(
                resources.getText(R.string.description_image_button_plus));

    }

    public void setShowVoicemailButton(boolean show) {
        View view = findViewById(R.id.dialpad_key_voicemail);
        if (view != null) {
            view.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * Whether or not the digits above the dialer can be edited.
     *
     * @param canBeEdited If true, the backspace button will be shown and the digits EditText
     *         will be configured to allow text manipulation.
     */
    public void setCanDigitsBeEdited(boolean canBeEdited) {
        View deleteButton = findViewById(R.id.deleteButton);
        deleteButton.setVisibility(canBeEdited ? View.VISIBLE : View.GONE);
        View overflowMenuButton = findViewById(R.id.dialpad_overflow);
        overflowMenuButton.setVisibility(canBeEdited ? View.VISIBLE : View.GONE);

        EditText digits = (EditText) findViewById(R.id.digits);
        digits.setClickable(canBeEdited);
        digits.setLongClickable(canBeEdited);
        digits.setFocusableInTouchMode(canBeEdited);
        digits.setCursorVisible(false);

        mCanDigitsBeEdited = canBeEdited;
    }

    public boolean canDigitsBeEdited() {
        return mCanDigitsBeEdited;
    }

    /**
     * Always returns true for onHoverEvent callbacks, to fix problems with accessibility due to
     * the dialpad overlaying other fragments.
     */
    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return true;
    }

    public void animateShow() {
        // This is a hack; without this, the setTranslationY is delayed in being applied, and the
        // numbers appear at their original position (0) momentarily before animating.
        final AnimatorListenerAdapter showListener = new AnimatorListenerAdapter() {};

        for (int i = 0; i < mButtonIds.length; i++) {
            int delay = (int)(getKeyButtonAnimationDelay(mButtonIds[i]) * DELAY_MULTIPLIER);
            int duration =
                    (int)(getKeyButtonAnimationDuration(mButtonIds[i]) * DURATION_MULTIPLIER);
            final DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(mButtonIds[i]);

            dialpadKey.setTranslationY(mTranslateDistance);
            dialpadKey.animate()
                    .translationY(0)
                    .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                    .setStartDelay(delay)
                    .setDuration(duration)
                    .setListener(showListener)
                    .start();
        }
    }

    public EditText getDigits() {
        return mDigits;
    }

    public ImageButton getDeleteButton() {
        return mDelete;
    }

    public View getOverflowMenuButton() {
        return mOverflowMenuButton;
    }

    private int getKeyButtonAnimationDelay(int buttonId) {
        switch(buttonId) {
            case R.id.one: return KEY_FRAME_DURATION * 1;
            case R.id.two: return KEY_FRAME_DURATION * 2;
            case R.id.three: return KEY_FRAME_DURATION * 3;
            case R.id.four: return KEY_FRAME_DURATION * 4;
            case R.id.five: return KEY_FRAME_DURATION * 5;
            case R.id.six: return KEY_FRAME_DURATION * 6;
            case R.id.seven: return KEY_FRAME_DURATION * 7;
            case R.id.eight: return KEY_FRAME_DURATION * 8;
            case R.id.nine: return KEY_FRAME_DURATION * 9;
            case R.id.star: return KEY_FRAME_DURATION * 10;
            case R.id.zero:
            case R.id.pound:
                return KEY_FRAME_DURATION * 11;
        }

        Log.wtf(TAG, "Attempted to get animation delay for invalid key button id.");
        return 0;
    }

    private int getKeyButtonAnimationDuration(int buttonId) {
        switch(buttonId) {
            case R.id.one:
            case R.id.two:
            case R.id.three:
            case R.id.four:
            case R.id.five:
            case R.id.six:
                return KEY_FRAME_DURATION * 10;
            case R.id.seven:
            case R.id.eight:
            case R.id.nine:
                return KEY_FRAME_DURATION * 9;
            case R.id.star:
            case R.id.zero:
            case R.id.pound:
                return KEY_FRAME_DURATION * 8;
        }

        Log.wtf(TAG, "Attempted to get animation duration for invalid key button id.");
        return 0;
    }
}
