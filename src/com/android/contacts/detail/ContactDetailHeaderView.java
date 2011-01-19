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

package com.android.contacts.detail;

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactLoader.Result;
import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.util.ContactBadgeUtil;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
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
import android.widget.Toast;

/**
 * Header for displaying a title bar with contact info. You
 * can bind specific values by calling
 * {@link ContactDetailHeaderView#loadData(com.android.contacts.ContactLoader.Result)}
 */
public class ContactDetailHeaderView extends FrameLayout
        implements View.OnClickListener, View.OnLongClickListener {
    private static final String TAG = "ContactDetailHeaderView";

    private static final int PHOTO_FADE_IN_ANIMATION_DURATION_MILLIS = 100;

    private TextView mDisplayNameView;
    private TextView mPhoneticNameView;
    private TextView mOrganizationTextView;
    private CheckBox mStarredView;
    private ImageView mPhotoView;
    private View mStatusContainerView;
    private TextView mStatusView;
    private TextView mStatusDateView;
    private TextView mAttributionView;

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
        mDisplayNameView.setOnLongClickListener(this);

        mPhoneticNameView = (TextView) findViewById(R.id.phonetic_name);
        mPhoneticNameView.setOnLongClickListener(this);

        mOrganizationTextView = (TextView) findViewById(R.id.organization);
        mOrganizationTextView.setOnLongClickListener(this);

        mStarredView = (CheckBox)findViewById(R.id.star);
        mStarredView.setOnClickListener(this);

        mPhotoView = (ImageView) findViewById(R.id.photo);

        mStatusContainerView = findViewById(R.id.status_container);
        mStatusView = (TextView)findViewById(R.id.status);
        mStatusDateView = (TextView)findViewById(R.id.status_date);

        mAttributionView = (TextView) findViewById(R.id.attribution);
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
        setSocialSnippet(contactData.getSocialSnippet());
        setSocialDate(ContactBadgeUtil.getSocialDate(contactData, getContext()));
        setAttribution(contactData.getEntities().size() > 1, contactData.isDirectoryEntry(),
                contactData.getDirectoryDisplayName(), contactData.getDirectoryType());
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
     * Set the photo to display in the header. If bitmap is null, the default placeholder
     * image is shown
     */
    private void setPhoto(Bitmap bitmap, boolean fadeIn) {
        if (mPhotoView.getDrawable() == null && fadeIn) {
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

    private void setAttribution(boolean isJoinedContact, boolean isDirectoryEntry,
            String directoryDisplayName, String directoryType) {
        if (isJoinedContact) {
            mAttributionView.setText(R.string.indicator_joined_contact);
            mAttributionView.setVisibility(View.VISIBLE);
        } else if (isDirectoryEntry) {
            String text = getContext().getString(R.string.contact_directory_description,
                    buildDirectoryName(directoryType, directoryDisplayName));
            mAttributionView.setText(text);
            mAttributionView.setVisibility(View.VISIBLE);
        } else {
            mAttributionView.setVisibility(View.INVISIBLE);
        }
    }

    private CharSequence buildDirectoryName(String directoryType, String directoryName) {
        String title;
        if (!TextUtils.isEmpty(directoryName)) {
            title = directoryName;
            // TODO: STOPSHIP - remove this once this is done by both directory providers
            int atIndex = title.indexOf('@');
            if (atIndex != -1 && atIndex < title.length() - 2) {
                final char firstLetter = Character.toUpperCase(title.charAt(atIndex + 1));
                title = firstLetter + title.substring(atIndex + 2);
            }
        } else {
            title = directoryType;
        }

        return title;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.star: {
                // Toggle "starred" state
                // Make sure there is a contact
                if (mContactUri != null) {
                    Intent intent = ContactSaveService.createSetStarredIntent(
                            getContext(), mContactUri, mStarredView.isChecked());
                    getContext().startService(intent);
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

    @Override
    public boolean onLongClick(View v) {
        if (!(v instanceof TextView)) {
            return false;
        }

        CharSequence text = ((TextView)v).getText();

        if (TextUtils.isEmpty(text)) {
            return false;
        }

        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(
                Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(null, text));
        Toast.makeText(getContext(), R.string.toast_text_copied, Toast.LENGTH_SHORT).show();
        return true;
    }
}
