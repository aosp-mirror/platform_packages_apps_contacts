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

package com.android.contacts.common.format;

import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

/**
 * Unit tests for {@link com.android.contacts.common.format.PrefixHighlighter}.
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
        CharSequence seq = mPrefixHighlighter.apply("", "");
        SpannedTestUtils.assertNotSpanned(seq, "");

        seq = mPrefixHighlighter.apply("test", "");
        SpannedTestUtils.assertNotSpanned(seq, "test");
    }

    public void testSetText_MatchingPrefix() {
        final String prefix = "TE";

        CharSequence seq = mPrefixHighlighter.apply("test", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mPrefixHighlighter.apply("Test", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mPrefixHighlighter.apply("TEst", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mPrefixHighlighter.apply("a test", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 2, 3);
    }

    public void testSetText_NotMatchingPrefix() {
        final CharSequence seq = mPrefixHighlighter.apply("test", "TA");
        SpannedTestUtils.assertNotSpanned(seq, "test");
    }

    public void testSetText_FirstMatch() {
        final CharSequence seq = mPrefixHighlighter.apply("a test's tests are not tests", "TE");
        SpannedTestUtils.assertPrefixSpan(seq, 2, 3);
    }

    public void testSetText_NoMatchingMiddleOfWord() {
        final String prefix = "TE";
        CharSequence seq = mPrefixHighlighter.apply("atest", prefix);
        SpannedTestUtils.assertNotSpanned(seq, "atest");

        seq = mPrefixHighlighter.apply("atest otest", prefix);
        SpannedTestUtils.assertNotSpanned(seq, "atest otest");

        seq = mPrefixHighlighter.apply("atest test", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 6, 7);
    }
}
