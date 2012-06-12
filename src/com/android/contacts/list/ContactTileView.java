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
import android.graphics.Rect;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.list.ContactTileAdapter.ContactEntry;

/**
 * A ContactTile displays a contact's picture and name
 */
public abstract class ContactTileView extends FrameLayout {
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
    protected Listener mListener;

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

        OnClickListener listener = createClickListener();

        if(mPushState != null) {
            mPushState.setOnClickListener(listener);
        } else {
            setOnClickListener(listener);
        }
    }

    protected OnClickListener createClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener == null) return;
                mListener.onContactSelected(
                        getLookupUri(),
                        ContactsUtils.getTargetRectFromView(mContext, ContactTileView.this));
            }
        };
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
                    mPhotoManager.loadPhoto(mPhoto, entry.photoUri, getApproximateImageSize(),
                            isDarkTheme());

                    if (mQuickContact != null) {
                        mQuickContact.assignContactUri(mLookupUri);
                    }
                } else if (mQuickContact != null) {
                    mQuickContact.assignContactUri(mLookupUri);
                    mPhotoManager.loadPhoto(mQuickContact, entry.photoUri,
                            getApproximateImageSize(), isDarkTheme());
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

    protected QuickContactBadge getQuickContact() {
        return mQuickContact;
    }

    /**
     * Implemented by subclasses to estimate the size of the picture. This can return -1 if only
     * a thumbnail is shown anyway
     */
    protected abstract int getApproximateImageSize();

    protected abstract boolean isDarkTheme();

    public interface Listener {
        /**
         * Notification that the contact was selected; no specific action is dictated.
         */
        void onContactSelected(Uri contactLookupUri, Rect viewRect);
        /**
         * Notification that the specified number is to be called.
         */
        void onCallNumberDirectly(String phoneNumber);
        /**
         * @return The width of each tile. This doesn't have to be a precise number (e.g. paddings
         *         can be ignored), but is used to load the correct picture size from the database
         */
        int getApproximateTileWidth();
    }
}
