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

import android.content.Context;
import android.util.AttributeSet;

/**
 * A {@link ContactTileStarredView} displays the contact's picture overlayed with their name
 * in a square.  The actual dimensions are set by
 * {@link com.android.contacts.list.ContactTileAdapter.ContactTileRow}.
 *
 * TODO Just remove this class.  We probably don't need {@link ContactTileSecondaryTargetView}
 * either.  (We can probably put the functionality to {@link ContactTileView})
 */
public class ContactTileStarredView extends ContactTileView {
    private final static String TAG = ContactTileStarredView.class.getSimpleName();

    public ContactTileStarredView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
