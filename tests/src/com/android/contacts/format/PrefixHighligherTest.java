/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.format;

import android.database.CharArrayBuffer;
import android.graphics.Typeface;
import android.test.AndroidTestCase;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

/**
 * Unit tests for {@link PrefixHighlighter}.
 */
public class PrefixHighligherTest extends AndroidTestCase {
    private static final int TEST_PREFIX_HIGHLIGHT_COLOR = 0xFF0000;

    /** The object under test. */
    private PrefixHighlighter mPrefixHighlighter;
    /** The view to on which the text is set. */
    private TextView mView;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPrefixHighlighter = new PrefixHighlighter(TEST_PREFIX_HIGHLIGHT_COLOR);
        mView = new TextView(getContext());
        // This guarantees that the text will be stored as a spannable so that we can determine
        // which styles have been applied to it.
        mView.setText("", TextView.BufferType.SPANNABLE);
    }

    public void testSetText_EmptyPrefix() {
        mPrefixHighlighter.setText(mView, "", new char[0]);
        checkTextAndNoSpans("");
        mPrefixHighlighter.setText(mView, "test", new char[0]);
        checkTextAndNoSpans("test");
    }

    public void testSetText_MatchingPrefix() {
        mPrefixHighlighter.setText(mView, "test", "TE".toCharArray());
        checkTextAndSpan("test", 0, 2);
        mPrefixHighlighter.setText(mView, "Test", "TE".toCharArray());
        checkTextAndSpan("Test", 0, 2);
        mPrefixHighlighter.setText(mView, "TEst", "TE".toCharArray());
        checkTextAndSpan("TEst", 0, 2);
        mPrefixHighlighter.setText(mView, "a test", "TE".toCharArray());
        checkTextAndSpan("a test", 2, 4);
    }

    public void testSetText_NotMatchingPrefix() {
        mPrefixHighlighter.setText(mView, "test", "TA".toCharArray());
        checkTextAndNoSpans("test");
    }

    public void testSetText_FirstMatch() {
        mPrefixHighlighter.setText(mView, "a test's tests are not tests", "TE".toCharArray());
        checkTextAndSpan("a test's tests are not tests", 2, 4);
    }

    public void testSetText_NoMatchingMiddleOfWord() {
        mPrefixHighlighter.setText(mView, "atest", "TE".toCharArray());
        checkTextAndNoSpans("atest");
        mPrefixHighlighter.setText(mView, "atest otest", "TE".toCharArray());
        checkTextAndNoSpans("atest otest");
        mPrefixHighlighter.setText(mView, "atest test", "TE".toCharArray());
        checkTextAndSpan("atest test", 6, 8);
    }

    public void testSetText_CharArrayBuffer() {
        CharArrayBuffer buffer = new CharArrayBuffer(100);

        FormatUtils.copyToCharArrayBuffer("test", buffer);
        mPrefixHighlighter.setText(mView, buffer, new char[0]);
        checkTextAndNoSpans("test");

        FormatUtils.copyToCharArrayBuffer("a test", buffer);
        mPrefixHighlighter.setText(mView, buffer, "TE".toCharArray());
        checkTextAndSpan("a test", 2, 4);

        FormatUtils.copyToCharArrayBuffer("test", buffer);
        mPrefixHighlighter.setText(mView, buffer, "TA".toCharArray());
        checkTextAndNoSpans("test");
    }

    /**
     * Checks that the text view contains the given text and there is no highlighted prefix.
     *
     * @param expectedText the text expected to be in the view
     */
    private void checkTextAndNoSpans(String expectedText) {
        checkTextAndOptionalSpan(expectedText, false, 0, 0);
    }

    /**
     * Checks that the text view contains the given text and the prefix is highlighted at the given
     * position.
     *
     * @param expectedText the text expected to be in the view
     * @param expectedStart the expect start of the highlighted prefix
     * @param expectedEnd the expect end of the highlighted prefix
     */
    private void checkTextAndSpan(String expectedText, int expectedStart, int expectedEnd) {
        checkTextAndOptionalSpan(expectedText, true, expectedStart, expectedEnd);
    }

    /**
     * Checks that the text view contains the given text and the prefix is highlighted if expected.
     *
     * @param expectedText the text expected to be in the view
     * @param expectedHighlighted whether the prefix should be highlighted in the view
     * @param expectedStart the expect start of the highlighted prefix
     * @param expectedEnd the expect end of the highlighted prefix
     */
    private void checkTextAndOptionalSpan(String expectedText, boolean expectedHighlighted,
            int expectedStart, int expectedEnd) {
        // First check that the text is correct.
        assertEquals(expectedText, mView.getText().toString());
        // Get the spannable stored in the text view.
        Spannable actualText = (Spannable) mView.getText();
        // Get the style and color spans applied to the text.
        StyleSpan[] styleSpans = actualText.getSpans(0, expectedText.length(), StyleSpan.class);
        ForegroundColorSpan[] foregroundColorSpans =
                actualText.getSpans(0, expectedText.length(), ForegroundColorSpan.class);
        if (!expectedHighlighted) {
            // There should be no bold or colored text.
            assertEquals(0, styleSpans.length);
            assertEquals(0, foregroundColorSpans.length);
        } else {
            // The text up to the found prefix is bold.
            assertEquals(1, styleSpans.length);
            StyleSpan boldSpan = styleSpans[0];
            assertEquals(Typeface.BOLD, boldSpan.getStyle());
            assertEquals(0, actualText.getSpanStart(boldSpan));
            assertEquals(expectedStart, actualText.getSpanEnd(boldSpan));

            // The prefix itself is in the highlight color.
            assertEquals(1, foregroundColorSpans.length);
            ForegroundColorSpan foregroundColorSpan = foregroundColorSpans[0];
            assertEquals(TEST_PREFIX_HIGHLIGHT_COLOR, foregroundColorSpan.getForegroundColor());
            assertEquals(expectedStart, actualText.getSpanStart(foregroundColorSpan));
            assertEquals(expectedEnd, actualText.getSpanEnd(foregroundColorSpan));
        }
    }
}
