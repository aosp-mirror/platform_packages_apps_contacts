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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SimpleOrStructuredView extends LinearLayout {
    private TextView mCaptionTextView;
    private EditText mFieldEditText;
    private Button mStructuredEditorButton;
    private Listener mListener;
    private boolean mHasFocus;

    public SimpleOrStructuredView(Context context) {
        super(context);
    }

    public SimpleOrStructuredView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SimpleOrStructuredView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static SimpleOrStructuredView inflate(LayoutInflater inflater, ViewGroup parent,
            boolean attachToRoot) {
        return (SimpleOrStructuredView) inflater.inflate(
                R.layout.list_edit_item_simple_or_structured, parent, attachToRoot);
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
        mStructuredEditorButton = (Button) findViewById(R.id.structuredEditorButton);
        mStructuredEditorButton.setOnClickListener(mFullEditorClickListener);
    }

    public void setLabelText(int resId) {
        mCaptionTextView.setText(resId);
    }

    public void setDisplayName(CharSequence value) {
        mFieldEditText.setText(value);
    }

    public CharSequence getDisplayName() {
        return mFieldEditText.getText();
    }

    private OnFocusChangeListener mFieldEditTextFocusChangeListener = new OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            if (mHasFocus && !hasFocus && mListener != null) {
                mListener.onFocusLost(SimpleOrStructuredView.this);
            }
            mHasFocus = hasFocus;
        }
    };

    private OnClickListener mFullEditorClickListener = new OnClickListener() {
        public void onClick(View v) {
            if (mListener != null) {
                mListener.onStructuredEditorRequested(SimpleOrStructuredView.this);
            }
        }
    };

    public interface Listener {
        void onFocusLost(SimpleOrStructuredView view);
        void onStructuredEditorRequested(SimpleOrStructuredView view);
    }
}
