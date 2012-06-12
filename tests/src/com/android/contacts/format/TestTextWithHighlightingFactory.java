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
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;

import com.android.contacts.widget.TextWithHighlighting;
import com.android.contacts.widget.TextWithHighlightingFactory;

/** A factory for {@link TextWithHighlighting} that wraps its parts in italics. */
@SmallTest
public final class TestTextWithHighlightingFactory implements TextWithHighlightingFactory {
    /** A {@link TextWithHighlighting} implementation that wraps its parts in italics. */
    private final static class TestTextWithHighlighting extends SpannableStringBuilder
            implements TextWithHighlighting {
        @Override
        public void setText(CharArrayBuffer baseText, CharArrayBuffer highlightedText) {
            append(new String(baseText.data, 0, baseText.sizeCopied));
            append(' ');
            append(new String(highlightedText.data, 0, highlightedText.sizeCopied));
            setSpan(new StyleSpan(Typeface.ITALIC), 0, baseText.sizeCopied, 0);
            setSpan(new StyleSpan(Typeface.ITALIC), baseText.sizeCopied + 1,
                    baseText.sizeCopied + 1 + highlightedText.sizeCopied, 0);
        }
    }

    @Override
    public TextWithHighlighting createTextWithHighlighting() {
        return new TestTextWithHighlighting();
    }
}
