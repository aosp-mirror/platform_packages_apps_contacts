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

package com.android.contacts.views.editor;

import com.android.contacts.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Header for displaying the title bar in the contact editor
 */
public class ContactEditorHeaderView extends FrameLayout {
    private static final String TAG = "ContactEditorHeaderView";

    private TextView mMergeInfo;

    public ContactEditorHeaderView(Context context) {
        this(context, null);
    }

    public ContactEditorHeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContactEditorHeaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater =
            (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.contact_editor_header_view, this);

        mMergeInfo = (TextView) findViewById(R.id.merge_info);

        // Start with unmerged
        setMergeInfo(1);
    }

    public void setMergeInfo(int count) {
        if (count <= 1) {
            mMergeInfo.setVisibility(GONE);
        } else {
            mMergeInfo.setVisibility(VISIBLE);
            mMergeInfo.setText(
                    getResources().getQuantityString(R.plurals.merge_info, count, count));
        }
    }
}
