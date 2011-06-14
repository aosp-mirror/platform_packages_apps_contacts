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
package com.android.contacts.list;

import com.android.contacts.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Contact list entry that represents the user's personal profile data.
 */
public class ContactListProfileItemView extends ContactListItemView {

    public ContactListProfileItemView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ContactListItemView);
        setDefaultPhotoViewSize(a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_profile_photo_size, 0));
    }

    @Override
    public TextView getNameTextView() {
        TextView nameTextView = super.getNameTextView();
        nameTextView.setTextAppearance(getContext(), android.R.style.TextAppearance_Large);
        return nameTextView;
    }

    @Override
    public void showDisplayName(Cursor cursor, int nameColumnIndex, int alternativeNameColumnIndex,
            boolean highlightingEnabled, int displayOrder) {
        getNameTextView().setText(getContext().getText(R.string.profile_display_name));
    }
}
