/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.android.contacts.R;
import com.android.phone.common.animation.AnimUtils;

public class SearchEditTextLayout extends FrameLayout {
    private static final int ANIMATION_DURATION = 200;

    private OnKeyListener mPreImeKeyListener;
    private int mTopMargin;
    private int mBottomMargin;
    private int mLeftMargin;
    private int mRightMargin;

    /* Subclass-visible for testing */
    protected boolean mIsExpanded = false;
    protected boolean mIsFadedOut = false;

    private View mExpanded;
    private EditText mSearchView;
    private View mBackButtonView;
    private View mClearButtonView;

    private Callback mCallback;

    /**
     * Listener for the back button next to the search view being pressed
     */
    public interface Callback {
        public void onBackButtonClicked();
        public void onSearchViewClicked();
    }

    public SearchEditTextLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPreImeKeyListener(OnKeyListener listener) {
        mPreImeKeyListener = listener;
    }

    public void setCallback(Callback listener) {
        mCallback = listener;
    }

    @Override
    protected void onFinishInflate() {
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        mTopMargin = params.topMargin;
        mBottomMargin = params.bottomMargin;
        mLeftMargin = params.leftMargin;
        mRightMargin = params.rightMargin;

        mExpanded = findViewById(R.id.search_box_expanded);
        mSearchView = (EditText) mExpanded.findViewById(R.id.search_view);

        mBackButtonView = findViewById(R.id.search_back_button);
        mClearButtonView = findViewById(R.id.search_close_button);

        mSearchView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    showInputMethod(v);
                } else {
                    hideInputMethod(v);
                }
            }
        });

        mSearchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onSearchViewClicked();
                }
            }
        });

        mSearchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mClearButtonView.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        findViewById(R.id.search_close_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchView.setText(null);
            }
        });

        findViewById(R.id.search_back_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onBackButtonClicked();
                }
            }
        });

        super.onFinishInflate();
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (mPreImeKeyListener != null) {
            if (mPreImeKeyListener.onKey(this, event.getKeyCode(), event)) {
                return true;
            }
        }
        return super.dispatchKeyEventPreIme(event);
    }

    public void fadeOut() {
        fadeOut(null);
    }

    public void fadeOut(AnimUtils.AnimationCallback callback) {
        AnimUtils.fadeOut(this, ANIMATION_DURATION, callback);
        mIsFadedOut = true;
    }

    public void fadeIn() {
        AnimUtils.fadeIn(this, ANIMATION_DURATION);
        mIsFadedOut = false;
    }

    public void setVisible(boolean visible) {
        if (visible) {
            setAlpha(1);
            setVisibility(View.VISIBLE);
            mIsFadedOut = false;
        } else {
            setAlpha(0);
            setVisibility(View.GONE);
            mIsFadedOut = true;
        }
    }

    public void expand(boolean requestFocus) {
        updateVisibility(true /* isExpand */);
        mExpanded.setVisibility(View.VISIBLE);
        mExpanded.setAlpha(1);
        setMargins(0f);
        // Set 9-patch background. This owns the padding, so we need to restore the original values.
        int paddingTop = this.getPaddingTop();
        int paddingStart = this.getPaddingStart();
        int paddingBottom = this.getPaddingBottom();
        int paddingEnd = this.getPaddingEnd();
        setBackgroundResource(R.drawable.search_shadow);
        setElevation(0);
        setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom);

        if (requestFocus) {
            mSearchView.requestFocus();
        }
        mIsExpanded = true;
    }

    /**
     * Updates the visibility of views depending on whether we will show the expanded or collapsed
     * search view. This helps prevent some jank with the crossfading if we are animating.
     *
     * @param isExpand Whether we are about to show the expanded search box.
     */
    private void updateVisibility(boolean isExpand) {
        int expandedViewVisibility = isExpand ? View.VISIBLE : View.GONE;

        mBackButtonView.setVisibility(expandedViewVisibility);
        if (TextUtils.isEmpty(mSearchView.getText())) {
            mClearButtonView.setVisibility(View.GONE);
        } else {
            mClearButtonView.setVisibility(expandedViewVisibility);
        }
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    /**
     * Assigns margins to the search box as a fraction of its maximum margin size
     *
     * @param fraction How large the margins should be as a fraction of their full size
     */
    private void setMargins(float fraction) {
        MarginLayoutParams params = (MarginLayoutParams) getLayoutParams();
        params.topMargin = (int) (mTopMargin * fraction);
        params.bottomMargin = (int) (mBottomMargin * fraction);
        params.leftMargin = (int) (mLeftMargin * fraction);
        params.rightMargin = (int) (mRightMargin * fraction);
        requestLayout();
    }

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    private void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
