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
import android.widget.QuickContactBadge;
import android.widget.TextView;

/**
 * A ContactTile displays the contact's picture overlayed with their name
 */
public class ContactTileView extends FrameLayout {
    private final static String TAG = ContactTileView.class.getSimpleName();

    private Uri mLookupUri;
    private ImageView mPhoto;
    private QuickContactBadge mQuickContact;
    private TextView mName;
    private TextView mStatus;
    private TextView mPhoneLabel;
    private TextView mPhoneNumber;
    private ContactPhotoManager mPhotoManager = null;
    private View mPushState;
    private View mHorizontalDivider;
    private Listener mListener;

    public ContactTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mName = (TextView) findViewById(R.id.contact_tile_name);

        mQuickContact = (QuickContactBadge) findViewById(R.id.contact_tile_quick);
        mPhoto = (ImageView) findViewById(R.id.contact_tile_image);
        mStatus = (TextView) findViewById(R.id.contact_tile_status);
        mPhoneLabel = (TextView) findViewById(R.id.contact_tile_phone_type);
        mPhoneNumber = (TextView) findViewById(R.id.contact_tile_phone_number);
        mPushState = findViewById(R.id.contact_tile_push_state);
        mHorizontalDivider = findViewById(R.id.contact_tile_horizontal_divider);

        OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onClick(ContactTileView.this);
                }
            }
        };

        if(mPushState != null) {
            mPushState.setOnClickListener(listener);
        } else {
            setOnClickListener(listener);
        }
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

            if (mStatus != null) {
                if (entry.status == null) {
                    mStatus.setVisibility(View.GONE);
                } else {
                    mStatus.setText(entry.status);
                    mStatus.setCompoundDrawablesWithIntrinsicBounds(entry.presenceIcon,
                            null, null, null);
                    mStatus.setVisibility(View.VISIBLE);
                }
            }

            if (mPhoneLabel != null) {
                mPhoneLabel.setText(entry.phoneLabel);
            }

            if (mPhoneNumber != null) {
                // TODO: Format number correctly
                mPhoneNumber.setText(entry.phoneNumber);
            }

            setVisibility(View.VISIBLE);

            if (mPhotoManager != null) {
                if (mPhoto != null) {
                    mPhotoManager.loadPhoto(mPhoto, entry.photoUri, isDefaultIconHires(),
                            isDarkTheme());

                    if (mQuickContact != null) {
                        mQuickContact.assignContactUri(mLookupUri);
                    }
                } else if (mQuickContact != null) {
                    mQuickContact.assignContactUri(mLookupUri);
                    mPhotoManager.loadPhoto(mQuickContact, entry.photoUri, isDefaultIconHires(),
                            isDarkTheme());
                }

            } else {
                Log.w(TAG, "contactPhotoManager not set");
            }

            if (mPushState != null) {
                mPushState.setContentDescription(entry.name);
            } else if (mQuickContact != null) {
                mQuickContact.setContentDescription(entry.name);
            }
        } else {
            setVisibility(View.INVISIBLE);
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setHorizontalDividerVisibility(int visibility) {
        if (mHorizontalDivider != null) mHorizontalDivider.setVisibility(visibility);
    }

    public Uri getLookupUri() {
        return mLookupUri;
    }

    protected boolean isDefaultIconHires() {
        return false;
    }

    protected boolean isDarkTheme() {
        return false;
    }

    public interface Listener {
        void onClick(ContactTileView contactTileView);
    }
}
