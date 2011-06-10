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

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.list.ContactTileAdapter.StrequentEntry;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/*
 * A ContactTile displays the contact's picture overlayed with their name
 */
public class ContactTileView extends RelativeLayout {
    private final static String TAG = "ContactTileView";

    private Uri mLookupUri;
    private ImageView mPhoto;
    private TextView mName;
    private ContactPhotoManager mPhotoManager;

    public ContactTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPhotoManager = ContactPhotoManager.createContactPhotoManager(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mName = (TextView) findViewById(R.id.contact_tile_name);
        mPhoto = (ImageView) findViewById(R.id.contact_tile_image);
    }

    public void loadFromContact(StrequentEntry entry) {
        if (entry != null) {
            mName.setText(entry.name);
            mLookupUri = entry.lookupKey;

            if (mPhotoManager != null) mPhotoManager.loadPhoto(mPhoto, entry.photoUri);
            else Log.w(TAG, "contactPhotoManger not set");

        } else {
            Log.w(TAG, "loadFromContact received null formal");
            throw new IllegalArgumentException();
        }
    }

    public void setContactPhotoManager(ContactPhotoManager manager) {
        mPhotoManager = manager;
    }

    public Uri getLookupUri() {
        return mLookupUri;
    }
}
