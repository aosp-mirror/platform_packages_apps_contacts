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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

/**
 * Highlights the text in a text field.
 */
public class PrefixHighlighter {
    private final CharArrayBuffer mBuffer = new CharArrayBuffer(128);
    private final int mPrefixHighlightColor;

    private ForegroundColorSpan mPrefixColorSpan;

    public PrefixHighlighter(int prefixHighlightColor) {
        mPrefixHighlightColor = prefixHighlightColor;
    }

    /**
     * Sets the text on the given text view, highlighting the word that matches the given prefix.
     *
     * @param view the view on which to set the text
     * @param text the string to use as the text
     * @param prefix the prefix to look for
     */
    public void setText(TextView view, String text, char[] prefix) {
        FormatUtils.copyToCharArrayBuffer(text, mBuffer);
        setText(view, mBuffer, prefix);
    }

    /**
     * Sets the text on the given text view, highlighting the word that matches the given prefix.
     *
     * @param view the view on which to set the text
     * @param text the text to use as the text
     * @param prefix the prefix to look for
     */
    public void setText(TextView view, CharArrayBuffer text, char[] prefix) {
        int index = FormatUtils.indexOfWordPrefix(text, prefix);
        if (index != -1) {
            if (mPrefixColorSpan == null) {
                mPrefixColorSpan = new ForegroundColorSpan(mPrefixHighlightColor);
            }

            String string = new String(text.data, 0, text.sizeCopied);
            SpannableString name = new SpannableString(
                    FormatUtils.applyStyleToSpan(Typeface.BOLD, string, 0, index,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
            name.setSpan(mPrefixColorSpan, index, index + prefix.length, 0 /* flags */);
            view.setText(name);
        } else {
            view.setText(text.data, 0, text.sizeCopied);
        }
    }
}
