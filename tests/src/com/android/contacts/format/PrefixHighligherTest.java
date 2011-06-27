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

import android.test.AndroidTestCase;
import android.widget.TextView;

/**
 * Unit tests for {@link PrefixHighlighter}.
 */
public class PrefixHighligherTest extends AndroidTestCase {
    private static final int TEST_PREFIX_HIGHLIGHT_COLOR = 0xFF0000;
    /** The HTML code used to mark the start of the highlighted part. */
    private static final String START = "<font color =\"#1ff0000\">";
    /** The HTML code used to mark the end of the highlighted part. */
    private static final String END = "</font>";

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
        SpannedTestUtils.checkHtmlText("", mView);

        mPrefixHighlighter.setText(mView, "test", new char[0]);
        SpannedTestUtils.checkHtmlText("test", mView);
    }

    public void testSetText_MatchingPrefix() {
        mPrefixHighlighter.setText(mView, "test", "TE".toCharArray());
        SpannedTestUtils.checkHtmlText(START + "te" + END + "st", mView);

        mPrefixHighlighter.setText(mView, "Test", "TE".toCharArray());
        SpannedTestUtils.checkHtmlText(START + "Te" + END + "st", mView);

        mPrefixHighlighter.setText(mView, "TEst", "TE".toCharArray());
        SpannedTestUtils.checkHtmlText(START + "TE" + END + "st", mView);

        mPrefixHighlighter.setText(mView, "a test", "TE".toCharArray());
        SpannedTestUtils.checkHtmlText("a " + START + "te" + END + "st", mView);
    }

    public void testSetText_NotMatchingPrefix() {
        mPrefixHighlighter.setText(mView, "test", "TA".toCharArray());
        SpannedTestUtils.checkHtmlText("test", mView);
    }

    public void testSetText_FirstMatch() {
        mPrefixHighlighter.setText(mView, "a test's tests are not tests", "TE".toCharArray());
        SpannedTestUtils.checkHtmlText("a " +START + "te" + END + "st's tests are not tests",
                mView);
    }

    public void testSetText_NoMatchingMiddleOfWord() {
        mPrefixHighlighter.setText(mView, "atest", "TE".toCharArray());
        SpannedTestUtils.checkHtmlText("atest", mView);

        mPrefixHighlighter.setText(mView, "atest otest", "TE".toCharArray());
        SpannedTestUtils.checkHtmlText("atest otest", mView);

        mPrefixHighlighter.setText(mView, "atest test", "TE".toCharArray());
        SpannedTestUtils.checkHtmlText("atest " + START + "te" + END + "st", mView);
    }
}
