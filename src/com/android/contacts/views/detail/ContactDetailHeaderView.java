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

package com.android.contacts.views.detail;

import com.android.contacts.R;
import com.android.contacts.util.ContactBadgeUtil;
import com.android.contacts.views.ContactLoader;
import com.android.contacts.views.ContactLoader.Result;

import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.StatusUpdates;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Header for displaying a title bar with contact info. You
 * can bind specific values by calling
 * {@link ContactDetailHeaderView#loadData(com.android.contacts.views.ContactLoader.Result)}
 */
public class ContactDetailHeaderView extends FrameLayout implements View.OnClickListener {
    private static final String TAG = "ContactDetailHeaderView";

    private static final int PHOTO_FADE_IN_ANIMATION_DURATION_MILLIS = 100;

    private TextView mDisplayNameView;
    private TextView mPhoneticNameView;
    private TextView mOrganizationTextView;
    private CheckBox mStarredView;
    private ImageView mPhotoView;
    private ImageView mPresenceView;
    private View mStatusContainerView;
    private TextView mStatusView;
    private TextView mStatusDateView;
    private TextView mDirectoryNameView;

    private Uri mContactUri;
    private Listener mListener;

    /**
     * Interface for callbacks invoked when the user interacts with a header.
     */
    public interface Listener {
        public void onPhotoClick(View view);
        public void onDisplayNameClick(View view);
    }

    public ContactDetailHeaderView(Context context) {
        this(context, null);
    }

    public ContactDetailHeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContactDetailHeaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater =
            (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.contact_detail_header_view, this);

        mDisplayNameView = (TextView) findViewById(R.id.name);

        mPhoneticNameView = (TextView) findViewById(R.id.phonetic_name);

        mOrganizationTextView = (TextView) findViewById(R.id.organization);

        mStarredView = (CheckBox)findViewById(R.id.star);
        mStarredView.setOnClickListener(this);

        mPhotoView = (ImageView) findViewById(R.id.photo);

        mPresenceView = (ImageView) findViewById(R.id.presence);
        mStatusContainerView = findViewById(R.id.status_container);
        mStatusView = (TextView)findViewById(R.id.status);
        mStatusDateView = (TextView)findViewById(R.id.status_date);

        mDirectoryNameView = (TextView) findViewById(R.id.directory_name);
    }

    /**
     * Loads the data from the Loader-Result. This is the only function that has to be called
     * from the outside to fully setup the View
     */
    public void loadData(ContactLoader.Result contactData) {
        mContactUri = contactData.getLookupUri();

        setDisplayName(contactData.getDisplayName(), contactData.getPhoneticName());
        setCompany(contactData);
        if (contactData.isLoadingPhoto()) {
            setPhoto(null, false);
        } else {
            byte[] photo = contactData.getPhotoBinaryData();
            setPhoto(photo != null ? BitmapFactory.decodeByteArray(photo, 0, photo.length)
                            : ContactBadgeUtil.loadPlaceholderPhoto(mContext),
                    contactData.isDirectoryEntry());
        }

        setStared(!contactData.isDirectoryEntry(), contactData.getStarred());
        setPresence(contactData.getPresence());
        setSocialSnippet(contactData.getSocialSnippet());
        setSocialDate(ContactBadgeUtil.getSocialDate(contactData, getContext()));
        setDirectoryName(contactData.isDirectoryEntry(), contactData.getDirectoryDisplayName(),
                contactData.getDirectoryType(), contactData.getDirectoryAccountName());
    }

    /**
     * Set the given {@link Listener} to handle header events.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    private void performPhotoClick() {
        if (mListener != null) {
            mListener.onPhotoClick(mPhotoView);
        }
    }

    private void performDisplayNameClick() {
        if (mListener != null) {
            mListener.onDisplayNameClick(mDisplayNameView);
        }
    }

    /**
     * Set the starred state of this header widget.
     */
    private void setStared(boolean visible, boolean starred) {
        if (visible) {
            mStarredView.setVisibility(View.VISIBLE);
            mStarredView.setChecked(starred);
        } else {
            mStarredView.setVisibility(View.GONE);
        }
    }

    /**
     * Set the presence. If presence is null, it is hidden.
     */
    private void setPresence(Integer presence) {
        if (presence == null) {
            mPresenceView.setVisibility(View.GONE);
        } else {
            mPresenceView.setVisibility(View.VISIBLE);
            mPresenceView.setImageResource(StatusUpdates.getPresenceIconResourceId(
                    presence.intValue()));
        }
    }

    /**
     * Set the photo to display in the header. If bitmap is null, the default placeholder
     * image is shown
     */
    private void setPhoto(Bitmap bitmap, boolean fadeIn) {
        if (fadeIn) {
            AlphaAnimation animation = new AlphaAnimation(0, 1);
            animation.setDuration(PHOTO_FADE_IN_ANIMATION_DURATION_MILLIS);
            animation.setInterpolator(new AccelerateInterpolator());
            mPhotoView.startAnimation(animation);
        }
        mPhotoView.setImageBitmap(bitmap);
    }

    /**
     * Set the display name and phonetic name to show in the header.
     */
    private void setDisplayName(CharSequence displayName, CharSequence phoneticName) {
        mDisplayNameView.setText(displayName);
        if (TextUtils.isEmpty(phoneticName)) {
            mPhoneticNameView.setVisibility(View.GONE);
        } else {
            mPhoneticNameView.setText(phoneticName);
            mPhoneticNameView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets the organization info. If several organizations are given, the first one is used
     */
    private void setCompany(Result contactData) {
        final boolean displayNameIsOrganization =
            contactData.getDisplayNameSource() == DisplayNameSources.ORGANIZATION;
        for (Entity entity : contactData.getEntities()) {
            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);

                if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final String company = entryValues.getAsString(Organization.COMPANY);
                    final String title = entryValues.getAsString(Organization.TITLE);
                    final String combined;
                    // We need to show company and title in a combined string. However, if the
                    // DisplayName is already the organization, it mirrors company or (if company
                    // is empty title). Make sure we don't show what's already shown as DisplayName
                    if (TextUtils.isEmpty(company)) {
                        combined = displayNameIsOrganization ? null : title;
                    } else {
                        if (TextUtils.isEmpty(title)) {
                            combined = displayNameIsOrganization ? null : company;
                        } else {
                            if (displayNameIsOrganization) {
                                combined = title;
                            } else {
                                combined = getResources().getString(
                                        R.string.organization_company_and_title,
                                        company, title);
                            }
                        }
                    }

                    if (TextUtils.isEmpty(combined)) {
                        mOrganizationTextView.setVisibility(GONE);
                    } else {
                        mOrganizationTextView.setVisibility(VISIBLE);
                        mOrganizationTextView.setText(combined);
                    }

                    return;
                }
            }
        }
        mOrganizationTextView.setVisibility(GONE);
    }

    /**
     * Set the social snippet text to display in the header.
     */
    private void setSocialSnippet(CharSequence snippet) {
        if (TextUtils.isEmpty(snippet)) {
            // No status info. Hide everything
            if (mStatusContainerView != null) mStatusContainerView.setVisibility(View.GONE);
            mStatusView.setVisibility(View.GONE);
            mStatusDateView.setVisibility(View.GONE);
        } else {
            // We have status info. Show the bubble
            if (mStatusContainerView != null) mStatusContainerView.setVisibility(View.VISIBLE);
            mStatusView.setVisibility(View.VISIBLE);
            mStatusView.setText(snippet);
        }
    }

    /**
     * Set the status attribution text to display in the header.
     */

    private void setSocialDate(CharSequence dateText) {
        if (TextUtils.isEmpty(dateText)) {
            mStatusDateView.setVisibility(View.GONE);
        } else {
            mStatusDateView.setText(dateText);
            mStatusDateView.setVisibility(View.VISIBLE);
        }
    }

    private void setDirectoryName(boolean isDirectoryEntry, String directoryDisplayName,
            String directoryType, String directoryAccountName) {
        if (isDirectoryEntry) {
            String name = TextUtils.isEmpty(directoryDisplayName)
                    ? directoryAccountName
                    : directoryDisplayName;
            String text;
            if (TextUtils.isEmpty(name)) {
                text = getContext().getString(
                        R.string.contact_directory_description, directoryType);
            } else {
                text = getContext().getString(
                        R.string.contact_directory_account_description, directoryType, name);
            }
            mDirectoryNameView.setText(text);
            mDirectoryNameView.setVisibility(View.VISIBLE);
        } else {
            mDirectoryNameView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.star: {
                // Toggle "starred" state
                // Make sure there is a contact
                if (mContactUri != null) {
                    // TODO: This should be done in the background
                    final ContentValues values = new ContentValues(1);
                    values.put(Contacts.STARRED, mStarredView.isChecked());
                    mContext.getContentResolver().update(mContactUri, values, null, null);
                }
                break;
            }
            case R.id.photo: {
                performPhotoClick();
                break;
            }
            case R.id.name: {
                performDisplayNameClick();
                break;
            }
        }
    }
}
