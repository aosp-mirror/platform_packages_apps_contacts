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

package com.android.contacts.util;

import com.android.contacts.R;
import com.android.contacts.views.ContactLoader;

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
import android.os.SystemClock;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

/**
 * Provides static functions to extract summary information for aggregate contacts
 */
public class ContactBadgeUtil {
    private static final String TAG = "ContactBadgeUtil";

    /**
     * Looks for the photo data item in entities. If found, creates a new Bitmap instance. If
     * not found, returns null
     */
    public static Bitmap getPhoto(ContactLoader.Result contactData) {
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
     * Returns the social snippet, including the date
     * @param status             The status of the contact. If this is either null or empty,
     *                            no social snippet is shown
     * @param statusTimestamp    The timestamp (retrieved via a call to
     *                            {@link System#currentTimeMillis()}) of the last status update.
     *                            This value can be null if it is not known.
     * @param statusLabel        The id of a resource string that specifies the current
     *                            status. This value can be null if no Label should be used.
     * @param statusResPackage   The name of the resource package containing the resource string
     *                            referenced in the parameter statusLabel.
     */
    public static CharSequence getSocialDate(ContactLoader.Result contactData,
            Context context) {
        if (TextUtils.isEmpty(contactData.getSocialSnippet())) {
            return null;
        }

        final CharSequence timestampDisplayValue;

        final Long statusTimestamp = contactData.getStatusTimestamp();
        if (statusTimestamp  != null) {
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

        final Integer statusLabel = contactData.getStatusLabel();
        final String statusResPackage = contactData.getStatusResPackage();
        if (statusLabel  != null) {
            Resources resources;
            if (TextUtils.isEmpty(statusResPackage)) {
                resources = context.getResources();
            } else {
                PackageManager pm = context.getPackageManager();
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
            attribution = context.getString(
                    R.string.contact_status_update_attribution_with_date,
                    timestampDisplayValue, labelDisplayValue);
        } else if (timestampDisplayValue == null && labelDisplayValue != null) {
            attribution = context.getString(
                    R.string.contact_status_update_attribution,
                    labelDisplayValue);
        } else if (timestampDisplayValue != null) {
            attribution = timestampDisplayValue;
        } else {
            attribution = null;
        }
        return attribution;
    }

    public static Bitmap loadPlaceholderPhoto(Context context) {
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

        return BitmapFactory.decodeResource(context.getResources(), resourceId);
    }
}
