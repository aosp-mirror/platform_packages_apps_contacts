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

package com.android.contacts;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * A custom text editor that optionally automatically brings up the soft
 * keyboard when first focused.
 */
public class SearchEditText extends EditText {

    private boolean mAutoShowKeyboard;

    public SearchEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Automatically show the soft keyboard when the field gets focus.  This is a
     * single-shot setting - it is reset as soon as the keyboard is shown.
     */
    public void setAutoShowKeyboard(boolean flag) {
        mAutoShowKeyboard = flag;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && mAutoShowKeyboard) {
            showKeyboard();
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused && mAutoShowKeyboard) {
            showKeyboard();
        }
    }

    /**
     * Explicitly brings up the soft keyboard if necessary.
     */
    private void showKeyboard() {
        InputMethodManager inputManager = (InputMethodManager)getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputManager.showSoftInput(this, 0);
        mAutoShowKeyboard = false;
    }

    public void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager)getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(getWindowToken(), 0);
    }
}
