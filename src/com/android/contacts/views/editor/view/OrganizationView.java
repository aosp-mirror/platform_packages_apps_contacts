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

public class OrganizationView extends LinearLayout {
    private TextView mCaptionTextView;
    private EditText mCompanyFieldEditText;
    private EditText mTitleFieldEditText;
    private Button mTypeButton;
    private Listener mListener;
    private boolean mHasFocus;

    public OrganizationView(Context context) {
        super(context);
    }

    public OrganizationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OrganizationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static OrganizationView inflate(LayoutInflater inflater, ViewGroup parent,
            boolean attachToRoot) {
        return (OrganizationView) inflater.inflate(R.layout.list_edit_item_organization, parent,
                attachToRoot);
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mCaptionTextView = (TextView) findViewById(R.id.caption);
        mCompanyFieldEditText = (EditText) findViewById(R.id.company_field);
        mCompanyFieldEditText.setOnFocusChangeListener(mFieldEditTextFocusChangeListener);
        mTitleFieldEditText = (EditText) findViewById(R.id.title_field);
        mTitleFieldEditText.setOnFocusChangeListener(mFieldEditTextFocusChangeListener);
        mTypeButton = (Button) findViewById(R.id.type);
    }

    public void setLabelText(int resId) {
        mCaptionTextView.setText(resId);
    }

    public void setFieldValues(CharSequence company, CharSequence title) {
        mCompanyFieldEditText.setText(company);
        mTitleFieldEditText.setText(title);
    }

    public CharSequence getCompanyFieldValue() {
        return mCompanyFieldEditText.getText();
    }

    public CharSequence getTitleFieldValue() {
        return mTitleFieldEditText.getText();
    }

    public void setTypeDisplayLabel(CharSequence type) {
        mTypeButton.setText(type);
    }

    private OnFocusChangeListener mFieldEditTextFocusChangeListener = new OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            boolean anyHasFocus = mCompanyFieldEditText.hasFocus()
                    || mTitleFieldEditText.hasFocus();
            if (mHasFocus && !anyHasFocus && mListener != null) {
                mListener.onFocusLost(OrganizationView.this);
            }
            mHasFocus = anyHasFocus;
        }
    };

    public interface Listener {
        void onFocusLost(OrganizationView view);
    }
}
