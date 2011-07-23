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
import com.android.contacts.ContactStatusUtil;
import com.android.contacts.R;
import com.android.contacts.list.ContactTileAdapter.ContactEntry;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import android.provider.ContactsContract.StatusUpdates;


/**
 * A ContactTile displays the contact's picture overlayed with their name
 */
public class ContactTileView extends FrameLayout {
    private final static String TAG = ContactTileView.class.getSimpleName();

    private Uri mLookupUri;
    private ImageView mPhoto;
    private ImageView mPresence;
    private QuickContactBadge mQuickContact;
    private TextView mName;
    private TextView mStatus;
    private ContactPhotoManager mPhotoManager = null;

    public ContactTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mName = (TextView) findViewById(R.id.contact_tile_name);

        mQuickContact = (QuickContactBadge) findViewById(R.id.contact_tile_quick);
        mPhoto = (ImageView) findViewById(R.id.contact_tile_image);
        mPresence = (ImageView) findViewById(R.id.contact_tile_presence);
        mStatus = (TextView) findViewById(R.id.contact_tile_status);
    }

    public void setPhotoManager(ContactPhotoManager photoManager) {
        mPhotoManager = photoManager;
    }

    /**
     * Populates the data members to be displayed from the
     * fields in {@link ContactEntry}
     */
    public void loadFromContact(ContactEntry entry) {
        if (entry != null) {
            mName.setText(entry.name);
            mLookupUri = entry.lookupKey;

            int presenceDrawableResId = (entry.presence == null ? 0 :
                    StatusUpdates.getPresenceIconResourceId(entry.presence));

            if (mPresence != null) {
                mPresence.setBackgroundResource(presenceDrawableResId);
            }

            if (mStatus != null) {
                String statusText;
                if (entry.presence == null) {
                    statusText = null;
                } else {
                    statusText =
                          (entry.status != null ? entry.status :
                          ContactStatusUtil.getStatusString(mContext, entry.presence));
                }
                mStatus.setText(statusText);
            }

            if (mQuickContact != null) {
                mQuickContact.assignContactUri(mLookupUri);
                mQuickContact.setImageBitmap(null);
            } else {
                mPhoto.setImageBitmap(null);
            }

            setVisibility(View.VISIBLE);

            if (mPhotoManager != null) {
                if (mQuickContact != null){
                    mPhotoManager.loadPhoto(mQuickContact, entry.photoUri);
                } else {
                    mPhotoManager.loadPhoto(mPhoto, entry.photoUri);
                }

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
}
