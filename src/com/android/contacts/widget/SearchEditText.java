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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

/**
 * A custom text editor that helps automatically dismiss the activity along with the soft
 * keyboard.
 */
public class SearchEditText extends EditText implements OnEditorActionListener, TextWatcher {

    private boolean mMaginfyingGlassEnabled = true;
    private Drawable mMagnifyingGlass;
    private OnFilterTextListener mListener;

    private boolean mMagnifyingGlassShown;

    public interface OnFilterTextListener {
        void onFilterChange(String queryString);
        void onCancelSearch();
    }

    public SearchEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        addTextChangedListener(this);
        setOnEditorActionListener(this);
        mMagnifyingGlass = getCompoundDrawables()[2];
        setCompoundDrawables(null, null, null, null);
    }

    public boolean isMaginfyingGlassEnabled() {
        return mMaginfyingGlassEnabled;
    }

    public void setMaginfyingGlassEnabled(boolean flag) {
        this.mMaginfyingGlassEnabled = flag;
    }

    public void setOnFilterTextListener(OnFilterTextListener listener) {
        this.mListener = listener;
    }

    /**
     * Conditionally shows a magnifying glass icon on the right side of the text field
     * when the text it empty.
     */
    @Override
    public boolean onPreDraw() {
        boolean emptyText = TextUtils.isEmpty(getText());
        if (mMagnifyingGlassShown != emptyText) {
            mMagnifyingGlassShown = emptyText;
            if (mMagnifyingGlassShown && mMaginfyingGlassEnabled) {
                setCompoundDrawables(null, null, mMagnifyingGlass, null);
            } else {
                setCompoundDrawables(null, null, null, null);
            }
            return false;
        }
        return super.onPreDraw();
    }

    /**
     * Dismisses the search UI along with the keyboard if the filter text is empty.
     */
    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && TextUtils.isEmpty(getText()) && mListener != null) {
            mListener.onCancelSearch();
            return true;
        }
        return false;
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    /**
     * Event handler for search UI.
     */
    public void afterTextChanged(Editable s) {
        if (mListener != null) {
            mListener.onFilterChange(trim(s));
        }
    }

    private String trim(Editable s) {
        return s.toString().trim();
    }

    /**
     * Event handler for search UI.
     */
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideSoftKeyboard();
            if (TextUtils.isEmpty(trim(getText())) && mListener != null) {
                mListener.onCancelSearch();
            }
            return true;
        }
        return false;
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
    }

}
