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

package com.android.contacts.views.editor.typeViews;

import com.android.contacts.R;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DataView extends LinearLayout {
    public TextView mLabelTextView;
    public TextView mDataTextView;
    public ImageView mActionIconImageView;
    public ImageView mPrimaryIconImageView;
    public ImageView mSecondaryActionButtonImageView;
    public View mSecondaryActionDividerView;

    public DataView(Context context) {
        super(context);
    }

    public DataView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DataView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static DataView inflate(LayoutInflater inflater, ViewGroup parent,
            boolean attachToRoot) {
        return (DataView) inflater.inflate(R.layout.list_edit_item_text_icons, parent, attachToRoot);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLabelTextView = (TextView) findViewById(android.R.id.text1);
        mDataTextView = (TextView) findViewById(android.R.id.text2);
        mActionIconImageView = (ImageView) findViewById(R.id.action_icon);
        mPrimaryIconImageView = (ImageView) findViewById(R.id.primary_icon);
        mSecondaryActionButtonImageView = (ImageView) findViewById(R.id.secondary_action_button);
        mSecondaryActionDividerView = findViewById(R.id.divider);
    }

    public void setLabelText(String value, int maxLines) {
        mLabelTextView.setText(value);
        setMaxLines(mLabelTextView, maxLines);
    }

    public void setDataText(String value, int maxLines) {
        mDataTextView.setText(value);
        setMaxLines(mDataTextView, maxLines);
    }

    private static void setMaxLines(TextView textView, int maxLines) {
        if (maxLines == 1) {
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            textView.setSingleLine(false);
            textView.setMaxLines(maxLines);
            textView.setEllipsize(null);
        }
    }

    public void setPrimary(boolean value) {
        mPrimaryIconImageView.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    public void setPrimaryIntent(Intent intent, Resources resources, int actionIcon) {
        if (intent != null) {
            mActionIconImageView.setImageDrawable(resources.getDrawable(actionIcon));
            mActionIconImageView.setVisibility(View.VISIBLE);
        } else {
            mActionIconImageView.setVisibility(View.INVISIBLE);
        }
    }

    public void setSecondaryIntent(Intent intent, Resources resources, int actionIcon) {
        if (intent != null) {
            mSecondaryActionButtonImageView.setImageDrawable(resources.getDrawable(actionIcon));
            mSecondaryActionButtonImageView.setVisibility(View.VISIBLE);
            mSecondaryActionDividerView.setVisibility(View.VISIBLE);
        } else {
            mSecondaryActionButtonImageView.setVisibility(View.INVISIBLE);
            mSecondaryActionDividerView.setVisibility(View.INVISIBLE);
        }
    }
}
