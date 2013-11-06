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

import android.content.Context;
import android.content.res.Resources;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.Html.TagHandler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.text.style.QuoteSpan;

import com.android.contacts.R;
import com.google.common.annotations.VisibleForTesting;

/**
 * Provides static functions to perform custom HTML to text conversions.
 * Specifically, it adjusts the color and padding of the vertical
 * stripe on block quotes and alignment of inlined images.
 */
@VisibleForTesting
public class HtmlUtils {

    /**
     * Converts HTML string to a {@link Spanned} text, adjusting formatting. Any extra new line
     * characters at the end of the text will be trimmed.
     */
    public static Spanned fromHtml(Context context, String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        Spanned spanned = Html.fromHtml(text);
        return postprocess(context, spanned);
    }

    /**
     * Converts HTML string to a {@link Spanned} text, adjusting formatting and using a custom
     * image getter. Any extra new line characters at the end of the text will be trimmed.
     */
    public static CharSequence fromHtml(Context context, String text, ImageGetter imageGetter,
            TagHandler tagHandler) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        return postprocess(context, Html.fromHtml(text, imageGetter, tagHandler));
    }

    /**
     * Replaces some spans with custom versions of those. Any extra new line characters at the end
     * of the text will be trimmed.
     */
    @VisibleForTesting
    static Spanned postprocess(Context context, Spanned original) {
        if (original == null) {
            return null;
        }
        final int length = original.length();
        if (length == 0) {
            return original; // Bail early.
        }

        // If it's a SpannableStringBuilder, just use it.  Otherwise, create a new
        // SpannableStringBuilder based on the passed Spanned.
        final SpannableStringBuilder builder;
        if (original instanceof SpannableStringBuilder) {
            builder = (SpannableStringBuilder) original;
        } else {
            builder = new SpannableStringBuilder(original);
        }

        final QuoteSpan[] quoteSpans = builder.getSpans(0, length, QuoteSpan.class);
        if (quoteSpans != null && quoteSpans.length != 0) {
            Resources resources = context.getResources();
            int color = resources.getColor(R.color.stream_item_stripe_color);
            int width = resources.getDimensionPixelSize(R.dimen.stream_item_stripe_width);
            for (int i = 0; i < quoteSpans.length; i++) {
                replaceSpan(builder, quoteSpans[i], new StreamItemQuoteSpan(color, width));
            }
        }

        final ImageSpan[] imageSpans = builder.getSpans(0, length, ImageSpan.class);
        if (imageSpans != null) {
            for (int i = 0; i < imageSpans.length; i++) {
                ImageSpan span = imageSpans[i];
                replaceSpan(builder, span, new ImageSpan(span.getDrawable(),
                        ImageSpan.ALIGN_BASELINE));
            }
        }

        // Trim the trailing new line characters at the end of the text (which can be added
        // when HTML block quote tags are turned into new line characters).
        int end = length;
        for (int i = builder.length() - 1; i >= 0; i--) {
            if (builder.charAt(i) != '\n') {
                break;
            }
            end = i;
        }

        // If there's no trailing newlines, just return it.
        if (end == length) {
            return builder;
        }

        // Otherwise, Return a substring of the original {@link Spanned} text
        // from the start index (inclusive) to the end index (exclusive).
        return new SpannableStringBuilder(builder, 0, end);
    }

    /**
     * Replaces one span with the other.
     */
    private static void replaceSpan(SpannableStringBuilder builder, Object originalSpan,
            Object newSpan) {
        builder.setSpan(newSpan,
                builder.getSpanStart(originalSpan),
                builder.getSpanEnd(originalSpan),
                builder.getSpanFlags(originalSpan));
        builder.removeSpan(originalSpan);
    }

    public static class StreamItemQuoteSpan extends QuoteSpan {
        private final int mWidth;

        public StreamItemQuoteSpan(int color, int width) {
            super(color);
            this.mWidth = width;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getLeadingMargin(boolean first) {
            return mWidth;
        }
    }
}
