/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.internal.R;

import android.database.CharArrayBuffer;
import android.graphics.Color;
import android.os.Handler;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * An animation that alternately dims and brightens the non-highlighted portion of text.
 */
public abstract class TextHighlightingAnimation implements Runnable {

    private static final int MAX_ALPHA = 255;
    private static final int MIN_ALPHA = 50;

    private AccelerateInterpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();
    private DecelerateInterpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();

    private final static DimmingSpan[] sEmptySpans = new DimmingSpan[0];

    /**
     * Frame rate expressed a number of millis between frames.
     */
    private static final long FRAME_RATE = 50;

    private DimmingSpan mDimmingSpan;
    private Handler mHandler;
    private boolean mAnimating;
    private boolean mDimming;
    private long mTargetTime;
    private final int mDuration;

    /**
     * A Spanned that highlights a part of text by dimming another part of that text.
     */
    public class TextWithHighlighting implements Spanned {

        private final DimmingSpan[] mSpans;
        private boolean mDimmingEnabled;
        private CharArrayBuffer mText;
        private int mDimmingSpanStart;
        private int mDimmingSpanEnd;
        private String mString;

        public TextWithHighlighting() {
            mSpans = new DimmingSpan[] { mDimmingSpan };
        }

        public void setText(CharArrayBuffer baseText, CharArrayBuffer highlightedText) {
            mText = baseText;

            // TODO figure out a way to avoid string allocation
            mString = new String(mText.data, 0, mText.sizeCopied);

            int index = indexOf(baseText, highlightedText);

            if (index == 0 || index == -1) {
                mDimmingEnabled = false;
            } else {
                mDimmingEnabled = true;
                mDimmingSpanStart = 0;
                mDimmingSpanEnd = index;
            }
        }

        /**
         * An implementation of indexOf on CharArrayBuffers that finds the first match of
         * the start of buffer2 in buffer1.  For example, indexOf("abcd", "cdef") == 2
         */
        private int indexOf(CharArrayBuffer buffer1, CharArrayBuffer buffer2) {
            char[] string1 = buffer1.data;
            char[] string2 = buffer2.data;
            int count1 = buffer1.sizeCopied;
            int count2 = buffer2.sizeCopied;

            // Ignore matching tails of the two buffers
            while (count1 > 0 && count2 > 0 && string1[count1 - 1] == string2[count2 - 1]) {
                count1--;
                count2--;
            }

            int size = count2;
            for (int i = 0; i < count1; i++) {
                if (i + size > count1) {
                    size = count1 - i;
                }
                int j;
                for (j = 0; j < size; j++) {
                    if (string1[i+j] != string2[j]) {
                        break;
                    }
                }
                if (j == size) {
                    return i;
                }
            }

            return -1;
        }


        @SuppressWarnings("unchecked")
        public <T> T[] getSpans(int start, int end, Class<T> type) {
            if (mDimmingEnabled) {
                return (T[])mSpans;
            } else {
                return (T[])sEmptySpans;
            }
        }

        public int getSpanStart(Object tag) {
            // We only have one span - no need to check the tag parameter
            return mDimmingSpanStart;
        }

        public int getSpanEnd(Object tag) {
            // We only have one span - no need to check the tag parameter
            return mDimmingSpanEnd;
        }

        public int getSpanFlags(Object tag) {
            // String is immutable - flags not needed
            return 0;
        }

        public int nextSpanTransition(int start, int limit, Class type) {
            // Never called since we only have one span
            return 0;
        }

        public char charAt(int index) {
            return mText.data[index];
        }

        public int length() {
            return mText.sizeCopied;
        }

        public CharSequence subSequence(int start, int end) {
            // Never called - implementing for completeness
            return new String(mText.data, start, end);
        }

        @Override
        public String toString() {
            return mString;
        }
    }

    /**
     * A Span that modifies alpha of the default foreground color.
     */
    private static class DimmingSpan extends CharacterStyle {
        private int mAlpha;

        public void setAlpha(int alpha) {
            mAlpha = alpha;
        }

        @Override
        public void updateDrawState(TextPaint ds) {

            // Only dim the text in the basic state; not selected, focused or pressed
            int[] states = ds.drawableState;
            if (states != null) {
                int count = states.length;
                for (int i = 0; i < count; i++) {
                    switch (states[i]) {
                        case R.attr.state_pressed:
                        case R.attr.state_selected:
                        case R.attr.state_focused:
                            // We can simply return, because the supplied text
                            // paint is already configured with defaults.
                            return;
                    }
                }
            }

            int color = ds.getColor();
            color = Color.argb(mAlpha, Color.red(color), Color.green(color), Color.blue(color));
            ds.setColor(color);
        }
    }

    /**
     * Constructor.
     */
    public TextHighlightingAnimation(int duration) {
        mDuration = duration;
        mHandler = new Handler();
        mDimmingSpan = new DimmingSpan();
        mDimmingSpan.setAlpha(MAX_ALPHA);
    }

    /**
     * Returns a Spanned that can be used by a text view to show text with highlighting.
     */
    public TextWithHighlighting createTextWithHighlighting() {
        return new TextWithHighlighting();
    }

    /**
     * Override and invalidate (redraw) TextViews showing {@link TextWithHighlighting}.
     */
    protected abstract void invalidate();

    /**
     * Starts the highlighting animation, which will dim portions of text.
     */
    public void startHighlighting() {
        startAnimation(true);
    }

    /**
     * Starts un-highlighting animation, which will brighten the dimmed portions of text
     * to the brightness level of the rest of text.
     */
    public void stopHighlighting() {
        startAnimation(false);
    }

    /**
     * Called when the animation starts.
     */
    protected void onAnimationStarted() {
    }

    /**
     * Called when the animation has stopped.
     */
    protected void onAnimationEnded() {
    }

    private void startAnimation(boolean dim) {
        if (mDimming != dim) {
            mDimming = dim;
            long now = System.currentTimeMillis();
            if (!mAnimating) {
                mAnimating = true;
                mTargetTime = now + mDuration;
                onAnimationStarted();
                mHandler.post(this);
            } else  {

                // If we have started dimming, reverse the direction and adjust the target
                // time accordingly.
                mTargetTime = (now + mDuration) - (mTargetTime - now);
            }
        }
    }

    /**
     * Animation step.
     */
    public void run() {
        long now = System.currentTimeMillis();
        long timeLeft = mTargetTime - now;
        if (timeLeft < 0) {
            mDimmingSpan.setAlpha(mDimming ? MIN_ALPHA : MAX_ALPHA);
            mAnimating = false;
            onAnimationEnded();
            return;
        }

        // Start=1, end=0
        float virtualTime = (float)timeLeft / mDuration;
        if (mDimming) {
            float interpolatedTime = DECELERATE_INTERPOLATOR.getInterpolation(virtualTime);
            mDimmingSpan.setAlpha((int)(MIN_ALPHA + (MAX_ALPHA-MIN_ALPHA) * interpolatedTime));
        } else {
            float interpolatedTime = ACCELERATE_INTERPOLATOR.getInterpolation(virtualTime);
            mDimmingSpan.setAlpha((int)(MIN_ALPHA + (MAX_ALPHA-MIN_ALPHA) * (1-interpolatedTime)));
        }

        invalidate();

        // Repeat
        mHandler.postDelayed(this, FRAME_RATE);
    }
}
