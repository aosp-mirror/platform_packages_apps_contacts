/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.contacts.views.editor.view;

import com.android.contacts.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SingleFieldView extends LinearLayout {
    private TextView mCaptionTextView;
    private EditText mFieldEditText;
    private Listener mListener;
    private boolean mHasFocus;

    public SingleFieldView(Context context) {
        super(context);
    }

    public SingleFieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SingleFieldView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static SingleFieldView inflate(LayoutInflater inflater, ViewGroup parent,
            boolean attachToRoot) {
        return (SingleFieldView) inflater.inflate(R.layout.list_edit_item_single_field,
                parent, attachToRoot);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mCaptionTextView = (TextView) findViewById(R.id.caption);
        mFieldEditText = (EditText) findViewById(R.id.field);
        mFieldEditText.setOnFocusChangeListener(mFieldEditTextFocusChangeListener);
    }

    public void setLabelText(int resId) {
        mCaptionTextView.setText(resId);
    }

    public void setFieldValue(CharSequence value) {
        mFieldEditText.setText(value);
    }

    public CharSequence getFieldValue() {
        return mFieldEditText.getText();
    }

    private OnFocusChangeListener mFieldEditTextFocusChangeListener = new OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            if (mHasFocus && !hasFocus && mListener != null) {
                mListener.onFocusLost(SingleFieldView.this);
            }
            mHasFocus = hasFocus;
        }
    };

    public interface Listener {
        void onFocusLost(SingleFieldView view);
    }
}
