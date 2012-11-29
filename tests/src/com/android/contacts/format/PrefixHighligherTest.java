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

import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

/**
 * Unit tests for {@link PrefixHighlighter}.
 */
@SmallTest
public class PrefixHighligherTest extends TestCase {
    private static final int TEST_PREFIX_HIGHLIGHT_COLOR = 0xFF0000;

    /** The object under test. */
    private PrefixHighlighter mPrefixHighlighter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPrefixHighlighter = new PrefixHighlighter(TEST_PREFIX_HIGHLIGHT_COLOR);
    }

    public void testApply_EmptyPrefix() {
        CharSequence seq = mPrefixHighlighter.apply("", new char[0]);
        SpannedTestUtils.assertNotSpanned(seq, "");

        seq = mPrefixHighlighter.apply("test", new char[0]);
        SpannedTestUtils.assertNotSpanned(seq, "test");
    }

    public void testSetText_MatchingPrefix() {
        final char[] charArray = "TE".toCharArray();

        CharSequence seq = mPrefixHighlighter.apply("test", charArray);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mPrefixHighlighter.apply("Test", charArray);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mPrefixHighlighter.apply("TEst", charArray);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mPrefixHighlighter.apply("a test", charArray);
        SpannedTestUtils.assertPrefixSpan(seq, 2, 3);
    }

    public void testSetText_NotMatchingPrefix() {
        final CharSequence seq = mPrefixHighlighter.apply("test", "TA".toCharArray());
        SpannedTestUtils.assertNotSpanned(seq, "test");
    }

    public void testSetText_FirstMatch() {
        final CharSequence seq = mPrefixHighlighter.apply("a test's tests are not tests",
                "TE".toCharArray());
        SpannedTestUtils.assertPrefixSpan(seq, 2, 3);
    }

    public void testSetText_NoMatchingMiddleOfWord() {
        final char[] charArray = "TE".toCharArray();
        CharSequence seq = mPrefixHighlighter.apply("atest", charArray);
        SpannedTestUtils.assertNotSpanned(seq, "atest");

        seq = mPrefixHighlighter.apply("atest otest", charArray);
        SpannedTestUtils.assertNotSpanned(seq, "atest otest");

        seq = mPrefixHighlighter.apply("atest test", charArray);
        SpannedTestUtils.assertPrefixSpan(seq, 6, 7);
    }
}
