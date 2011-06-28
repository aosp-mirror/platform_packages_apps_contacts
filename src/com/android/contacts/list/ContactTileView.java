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
import com.android.contacts.list.ContactTileAdapter.ContactEntry;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A ContactTile displays the contact's picture overlayed with their name
 */
public class ContactTileView extends FrameLayout {
    private final static String TAG = "ContactTileView";

    /**
     * This divides into the width to define the height when
     * {link DisplayTypes@SINLGE_ROW} is true.
     */
    private final static int HEIGHT_RATIO = 5;
    private Uri mLookupUri;
    private ImageView mPhoto;
    private TextView mName;
    private ContactPhotoManager mPhotoManager = null;
    /**
     * Is set to true if the {@link ContactTileView} is a square.
     */
    private boolean mIsSquare;

    public ContactTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mName = (TextView) findViewById(R.id.contact_tile_name);
        mPhoto = (ImageView) findViewById(R.id.contact_tile_image);
    }

    public boolean isSquare() {
        return mIsSquare;
    }

    public void setIsSquare(boolean isSquare) {
        mIsSquare = isSquare;
    }

    public void setPhotoManager(ContactPhotoManager photoManager) {
        mPhotoManager = photoManager;
    }

    public void loadFromContact(ContactEntry entry) {
        if (entry != null) {
            mName.setText(entry.name);
            mLookupUri = entry.lookupKey;
            mPhoto.setImageBitmap(null);
            setVisibility(View.VISIBLE);

            if (mPhotoManager != null) {
                mPhotoManager.loadPhoto(mPhoto, entry.photoUri);
            } else {
                Log.w(TAG, "contactPhotoManager not set");
            }
        } else {
            setVisibility(View.INVISIBLE);
        }
    }

    public Uri getLookupUri() {
        return mLookupUri;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Getting how much space is currently available and telling our
        // Children to split it.
        int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int childMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        measureChildren(childMeasureSpec, childMeasureSpec);
        setMeasuredDimension(width, width / (mIsSquare ? 1 : HEIGHT_RATIO));
    }
}
