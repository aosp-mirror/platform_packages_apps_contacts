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
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

/**
 * A {@link ContactTileSecondaryTargetView} displays the contact's picture overlayed with their name
 * in a perfect square like the {@link ContactTileStarredView}. However it adds in an additional
 * touch target for a secondary action.
 */
public class ContactTileSecondaryTargetView extends ContactTileStarredView {

    private final static String TAG = ContactTileSecondaryTargetView.class.getSimpleName();

    private ImageButton mSecondaryButton;

    public ContactTileSecondaryTargetView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSecondaryButton = (ImageButton) findViewById(R.id.contact_tile_secondary_button);
        mSecondaryButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, getLookupUri()));
            }
        });
    }

    @Override
    protected boolean isDarkTheme() {
        return true;
    }
}
