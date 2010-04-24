/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.widget;

import android.database.CharArrayBuffer;
import android.text.Spanned;

/**
 * A Spanned that highlights a part of text by dimming another part of that text.
 */
public interface TextWithHighlighting extends Spanned {
    void setText(CharArrayBuffer baseText, CharArrayBuffer highlightedText);
}
