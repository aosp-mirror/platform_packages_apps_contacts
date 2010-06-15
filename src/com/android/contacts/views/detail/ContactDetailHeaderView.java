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
import com.android.contacts.views.ContactLoader;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.StatusUpdates;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.QuickContactBadge;
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

    private TextView mDisplayNameView;
    private TextView mPhoneticNameView;
    private CheckBox mStarredView;
    private QuickContactBadge mPhotoView;
    private ImageView mPresenceView;
    private TextView mStatusView;
    private TextView mStatusAttributionView;
    private ImageButton mEditButton;

    private Uri mContactUri;
    private Listener mListener;

    /**
     * Interface for callbacks invoked when the user interacts with a header.
     */
    public interface Listener {
        public void onPhotoClick(View view);
        public void onDisplayNameClick(View view);
        public void onEditClicked();
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

        mStarredView = (CheckBox)findViewById(R.id.star);
        mStarredView.setOnClickListener(this);

        mEditButton = (ImageButton) findViewById(R.id.edit);
        mEditButton.setOnClickListener(this);

        mPhotoView = (QuickContactBadge) findViewById(R.id.photo);

        mPresenceView = (ImageView) findViewById(R.id.presence);

        mStatusView = (TextView)findViewById(R.id.status);
        mStatusAttributionView = (TextView)findViewById(R.id.status_date);
    }

    /**
     * Loads the data from the Loader-Result. This is the only function that has to be called
     * from the outside to fully setup the View
     */
    public void loadData(ContactLoader.Result contactData) {
        mContactUri = contactData.getLookupUri();
        mPhotoView.assignContactUri(contactData.getLookupUri());

        setDisplayName(contactData.getDisplayName(), contactData.getPhoneticName());
        setPhoto(findPhoto(contactData));
        setStared(contactData.getStarred());
        setPresence(contactData.getPresence());
        setStatus(
                contactData.getStatus(), contactData.getStatusTimestamp(),
                contactData.getStatusLabel(), contactData.getStatusResPackage());
    }

    /**
     * Looks for the photo data item in entities. If found, creates a new Bitmap instance. If
     * not found, returns null
     */
    private Bitmap findPhoto(ContactLoader.Result contactData) {
        final long photoId = contactData.getPhotoId();

        for (Entity entity : contactData.getEntities()) {
            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                final long dataId = entryValues.getAsLong(Data._ID);
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);

                if (dataId == photoId) {
                    // Correct Data Id but incorrect MimeType? Don't load
                    if (!Photo.CONTENT_ITEM_TYPE.equals(mimeType)) return null;
                    final byte[] binaryData = entryValues.getAsByteArray(Photo.PHOTO);
                    if (binaryData == null) return null;
                    return BitmapFactory.decodeByteArray(binaryData, 0, binaryData.length);
                }
            }
        }

        return null;
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

    private void performEditClick() {
        if (mListener != null) {
            mListener.onEditClicked();
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
    private void setStared(boolean starred) {
        mStarredView.setChecked(starred);
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
    private void setPhoto(Bitmap bitmap) {
        mPhotoView.setImageBitmap(bitmap == null ? loadPlaceholderPhoto() : bitmap);
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
     * Set the social snippet text to display in the header.
     */
    private void setSocialSnippet(CharSequence snippet) {
        if (snippet == null) {
            mStatusView.setVisibility(View.GONE);
            mStatusAttributionView.setVisibility(View.GONE);
        } else {
            mStatusView.setText(snippet);
            mStatusView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Set the status attribution text to display in the header.
     */
    private void setStatusAttribution(CharSequence attribution) {
        if (attribution == null) {
            mStatusAttributionView.setVisibility(View.GONE);
        } else {
            mStatusAttributionView.setText(attribution);
            mStatusAttributionView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Set a list of specific MIME-types to exclude and not display. For
     * example, this can be used to hide the {@link Contacts#CONTENT_ITEM_TYPE}
     * profile icon.
     */
    public void setExcludeMimes(String[] excludeMimes) {
        mPhotoView.setExcludeMimes(excludeMimes);
    }

    /**
     * Set all the status values to display in the header.
     * @param status             The status of the contact. If this is either null or empty,
     *                           the status is cleared and the other parameters are ignored.
     * @param statusTimestamp    The timestamp (retrieved via a call to
     *                           {@link System#currentTimeMillis()}) of the last status update.
     *                           This value can be null if it is not known.
     * @param statusLabel        The id of a resource string that specifies the current
     *                           status. This value can be null if no Label should be used.
     * @param statusResPackage   The name of the resource package containing the resource string
     *                           referenced in the parameter statusLabel.
     */
    private void setStatus(final String status, final Long statusTimestamp,
            final Integer statusLabel, final String statusResPackage) {
        if (TextUtils.isEmpty(status)) {
            setSocialSnippet(null);
            return;
        }

        setSocialSnippet(status);

        final CharSequence timestampDisplayValue;

        if (statusTimestamp != null) {
            // Set the date/time field by mixing relative and absolute
            // times.
            int flags = DateUtils.FORMAT_ABBREV_RELATIVE;

            timestampDisplayValue = DateUtils.getRelativeTimeSpanString(
                    statusTimestamp.longValue(), System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, flags);
        } else {
            timestampDisplayValue = null;
        }


        String labelDisplayValue = null;

        if (statusLabel != null) {
            Resources resources;
            if (TextUtils.isEmpty(statusResPackage)) {
                resources = getResources();
            } else {
                PackageManager pm = getContext().getPackageManager();
                try {
                    resources = pm.getResourcesForApplication(statusResPackage);
                } catch (NameNotFoundException e) {
                    Log.w(TAG, "Contact status update resource package not found: "
                            + statusResPackage);
                    resources = null;
                }
            }

            if (resources != null) {
                try {
                    labelDisplayValue = resources.getString(statusLabel.intValue());
                } catch (NotFoundException e) {
                    Log.w(TAG, "Contact status update resource not found: " + statusResPackage + "@"
                            + statusLabel.intValue());
                }
            }
        }

        final CharSequence attribution;
        if (timestampDisplayValue != null && labelDisplayValue != null) {
            attribution = getContext().getString(
                    R.string.contact_status_update_attribution_with_date,
                    timestampDisplayValue, labelDisplayValue);
        } else if (timestampDisplayValue == null && labelDisplayValue != null) {
            attribution = getContext().getString(
                    R.string.contact_status_update_attribution,
                    labelDisplayValue);
        } else if (timestampDisplayValue != null) {
            attribution = timestampDisplayValue;
        } else {
            attribution = null;
        }
        setStatusAttribution(attribution);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.edit: {
                performEditClick();
                break;
            }
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

    private Bitmap loadPlaceholderPhoto() {
        // Set the photo with a random "no contact" image
        final long now = SystemClock.elapsedRealtime();
        final int num = (int) now & 0xf;
        final int resourceId;
        if (num < 9) {
            // Leaning in from right, common
            resourceId = R.drawable.ic_contact_picture;
        } else if (num < 14) {
            // Leaning in from left uncommon
            resourceId = R.drawable.ic_contact_picture_2;
        } else {
            // Coming in from the top, rare
            resourceId = R.drawable.ic_contact_picture_3;
        }

        return BitmapFactory.decodeResource(mContext.getResources(), resourceId);
    }
}
