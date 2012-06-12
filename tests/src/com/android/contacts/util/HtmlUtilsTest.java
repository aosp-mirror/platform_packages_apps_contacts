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

package com.android.contacts.util;

import android.graphics.drawable.ColorDrawable;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ImageSpan;
import android.text.style.QuoteSpan;

import com.android.contacts.util.HtmlUtils.StreamItemQuoteSpan;

/**
 * Tests for {@link HtmlUtils}.
 *
 * adb shell am instrument -w -e class com.android.contacts.util.HtmlUtilsTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class HtmlUtilsTest extends AndroidTestCase {
    /**
     * Test for {@link HtmlUtils#postprocess} specifically about trimming newlines.
     */
    public void testPostProcess_trimNewLines() {
        checkTrimNewLines("", "");
        checkTrimNewLines("", "\n");
        checkTrimNewLines("", "\n\n");
        checkTrimNewLines("a", "a");
        checkTrimNewLines("abc", "abc");
        checkTrimNewLines("abc", "abc\n");
        checkTrimNewLines("abc", "abc\n\n\n");
        checkTrimNewLines("ab\nc", "ab\nc\n");

        assertNull(HtmlUtils.postprocess(getContext(), null));
    }

    private final void checkTrimNewLines(String expectedString, CharSequence text) {
        // Test with both SpannedString and SpannableStringBuilder.
        assertEquals(expectedString,
                HtmlUtils.postprocess(getContext(), new SpannedString(text)).toString());

        assertEquals(expectedString,
                HtmlUtils.postprocess(getContext(), new SpannableStringBuilder(text)).toString());
    }

    public void testPostProcess_with_newlines() {
        final SpannableStringBuilder builder = new SpannableStringBuilder("01234\n\n");

        setSpans(builder);

        // First test with a SpannableStringBuilder, as opposed to SpannedString
        checkPostProcess(HtmlUtils.postprocess(getContext(), builder));

        // Then pass a SpannedString, which is immutable, but the method should still work.
        checkPostProcess(HtmlUtils.postprocess(getContext(), new SpannedString(builder)));
    }

    /**
     * Same as {@link #testPostProcess_with_newlines}, but text has no newlines.
     * (The internal code path is slightly different.)
     */
    public void testPostProcess_no_newlines() {
        final SpannableStringBuilder builder = new SpannableStringBuilder("01234");

        setSpans(builder);

        // First test with a SpannableStringBuilder, as opposed to SpannedString
        checkPostProcess(HtmlUtils.postprocess(getContext(), builder));

        // Then pass a SpannedString, which is immutable, but the method should still work.
        checkPostProcess(HtmlUtils.postprocess(getContext(), new SpannedString(builder)));
    }

    private void setSpans(SpannableStringBuilder builder) {
        builder.setSpan(new ImageSpan(new ColorDrawable(), ImageSpan.ALIGN_BOTTOM), 0, 2, 0);
        builder.setSpan(new QuoteSpan(), 2, 4, 0);
        builder.setSpan(new CustomSpan(), 4, builder.length(), 0);
    }

    private void checkPostProcess(Spanned ret) {
        // Newlines should be trimmed.
        assertEquals("01234", ret.toString());

        // First, check the image span.
        // - Vertical alignment should be changed to ALIGN_BASELINE
        // - Drawable shouldn't be changed.
        ImageSpan[] imageSpans = ret.getSpans(0, ret.length(), ImageSpan.class);
        assertEquals(1, imageSpans.length);
        assertEquals(ImageSpan.ALIGN_BASELINE, imageSpans[0].getVerticalAlignment());
        assertEquals(ColorDrawable.class, imageSpans[0].getDrawable().getClass());

        // QuoteSpans should be replaced with StreamItemQuoteSpans.
        QuoteSpan[] quoteSpans = ret.getSpans(0, ret.length(), QuoteSpan.class);
        assertEquals(1, quoteSpans.length);
        assertEquals(StreamItemQuoteSpan.class, quoteSpans[0].getClass());

        // Other spans should be preserved.
        CustomSpan[] customSpans = ret.getSpans(0, ret.length(), CustomSpan.class);
        assertEquals(1, customSpans.length);
    }

    /** Custom span class used in {@link #testPostProcess} */
    private static class CustomSpan {
    }
}
