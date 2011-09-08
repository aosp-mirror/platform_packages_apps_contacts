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

import com.android.contacts.widget.TextWithHighlighting;
import com.android.contacts.widget.TextWithHighlightingFactory;

import android.database.CharArrayBuffer;
import android.widget.TextView;

/**
 * Sets the content of the given text view, to contain the formatted display name, with a
 * prefix if necessary.
 */
public final class DisplayNameFormatter {
    private final CharArrayBuffer mNameBuffer = new CharArrayBuffer(128);
    private final CharArrayBuffer mAlternateNameBuffer = new CharArrayBuffer(128);
    private final PrefixHighlighter mPrefixHighlighter;

    private TextWithHighlightingFactory mTextWithHighlightingFactory;
    private TextWithHighlighting mTextWithHighlighting;
    private CharSequence mUnknownNameText;

    public DisplayNameFormatter(PrefixHighlighter prefixHighlighter) {
        mPrefixHighlighter = prefixHighlighter;
    }

    public CharArrayBuffer getNameBuffer() {
        return mNameBuffer;
    }

    public CharArrayBuffer getAlternateNameBuffer() {
        return mAlternateNameBuffer;
    }

    public void setUnknownNameText(CharSequence unknownNameText) {
        mUnknownNameText = unknownNameText;
    }

    public void setDisplayName(TextView view, int displayOrder,
            boolean highlightingEnabled, char[] highlightedPrefix) {
        view.setText(getDisplayName(displayOrder, highlightingEnabled, highlightedPrefix));
    }

    public CharSequence getDisplayName(int displayOrder, boolean highlightingEnabled,
            char[] highlightedPrefix) {
        int size = mNameBuffer.sizeCopied;
        if (size == 0) {
            return mUnknownNameText;
        }

        CharSequence text;
        if (highlightingEnabled) {
            if (mTextWithHighlighting == null) {
                mTextWithHighlighting =
                        mTextWithHighlightingFactory.createTextWithHighlighting();
            }
            mTextWithHighlighting.setText(mNameBuffer, mAlternateNameBuffer);
            text = mTextWithHighlighting;
        } else {
            text = FormatUtils.charArrayBufferToString(mNameBuffer);
        }
        if (highlightedPrefix != null) {
            text = mPrefixHighlighter.apply(text, highlightedPrefix);
        }
        return text;
    }
}
