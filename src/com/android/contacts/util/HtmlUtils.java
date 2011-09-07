package com.android.contacts.util;

import android.content.Context;
import android.content.res.Resources;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.Html.TagHandler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.text.style.QuoteSpan;

import com.android.contacts.R;

/**
 * Provides static functions to perform custom HTML to text conversions.
 * Specifically, it adjusts the color and padding of the vertical
 * stripe on block quotes and alignment of inlined images.
 */
public class HtmlUtils {

    /**
     * Converts HTML string to a {@link Spanned} text, adjusting formatting.
     */
    public static Spanned fromHtml(Context context, String text) {
        Spanned spanned = Html.fromHtml(text);
        postprocess(context, spanned);
        return spanned;
    }

    /**
     * Converts HTML string to a {@link Spanned} text, adjusting formatting and using a custom
     * image getter.
     */
    public static CharSequence fromHtml(Context context, String text, ImageGetter imageGetter,
            TagHandler tagHandler) {
        Spanned spanned = Html.fromHtml(text, imageGetter, tagHandler);
        postprocess(context, spanned);
        return spanned;
    }

    /**
     * Replaces some spans with custom versions of those.
     */
    private static void postprocess(Context context, Spanned spanned) {
        if (!(spanned instanceof SpannableStringBuilder)) {
            return;
        }

        int length = spanned.length();

        SpannableStringBuilder builder = (SpannableStringBuilder)spanned;
        QuoteSpan[] quoteSpans = spanned.getSpans(0, length, QuoteSpan.class);
        if (quoteSpans != null && quoteSpans.length != 0) {
            Resources resources = context.getResources();
            int color = resources.getColor(R.color.stream_item_stripe_color);
            int width = resources.getDimensionPixelSize(R.dimen.stream_item_stripe_width);
            for (int i = 0; i < quoteSpans.length; i++) {
                replaceSpan(builder, quoteSpans[i], new StreamItemQuoteSpan(color, width));
            }
        }

        ImageSpan[] imageSpans = spanned.getSpans(0, length, ImageSpan.class);
        if (imageSpans != null) {
            for (int i = 0; i < imageSpans.length; i++) {
                ImageSpan span = imageSpans[i];
                replaceSpan(builder, span, new ImageSpan(span.getDrawable(),
                        ImageSpan.ALIGN_BASELINE));
            }
        }
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
