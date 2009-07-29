/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;


import java.io.ByteArrayInputStream;
import android.provider.ContactsContract.Data;
import java.io.InputStream;

import android.net.Uri;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.Contacts;
import android.provider.Contacts.Photos;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.Im.ProviderNames;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

public class ContactsUtils {

    /**
     * Build the display title for the {@link Data#CONTENT_URI} entry in the
     * provided cursor, assuming the given mimeType.
     */
    public static final CharSequence getDisplayLabel(Context context,
            String mimeType, Cursor cursor) {
        // Try finding the type and label for this mimetype
        int colType;
        int colLabel;

        // TODO: move the SMS mime-type to a central location
        if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)
                || FastTrackWindow.MIME_SMS_ADDRESS.equals(mimeType)) {
            // Reset to phone mimetype so we generate a label for SMS case
            mimeType = Phone.CONTENT_ITEM_TYPE;
            colType = cursor.getColumnIndex(Phone.TYPE);
            colLabel = cursor.getColumnIndex(Phone.LABEL);
        } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            colType = cursor.getColumnIndex(Email.TYPE);
            colLabel = cursor.getColumnIndex(Email.LABEL);
        } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType)) {
            colType = cursor.getColumnIndex(StructuredPostal.TYPE);
            colLabel = cursor.getColumnIndex(StructuredPostal.LABEL);
        } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
            colType = cursor.getColumnIndex(Organization.TYPE);
            colLabel = cursor.getColumnIndex(Organization.LABEL);
        } else {
            return null;
        }

        final int type = cursor.getInt(colType);
        final CharSequence label = cursor.getString(colLabel);

        return getDisplayLabel(context, mimeType, type, label);
    }

    public static final CharSequence getDisplayLabel(Context context, String mimetype, int type,
            CharSequence label) {
        CharSequence display = "";
        final int customType;
        final int defaultType;
        final int arrayResId;

        if (Phone.CONTENT_ITEM_TYPE.equals(mimetype)) {
            defaultType = Phone.TYPE_HOME;
            customType = Phone.TYPE_CUSTOM;
            arrayResId = com.android.internal.R.array.phoneTypes;
        } else if (Email.CONTENT_ITEM_TYPE.equals(mimetype)) {
            defaultType = Email.TYPE_HOME;
            customType = Email.TYPE_CUSTOM;
            arrayResId = com.android.internal.R.array.emailAddressTypes;
        } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimetype)) {
            defaultType = StructuredPostal.TYPE_HOME;
            customType = StructuredPostal.TYPE_CUSTOM;
            arrayResId = com.android.internal.R.array.postalAddressTypes;
        } else if (Organization.CONTENT_ITEM_TYPE.equals(mimetype)) {
            defaultType = Organization.TYPE_WORK;
            customType = Organization.TYPE_CUSTOM;
            arrayResId = com.android.internal.R.array.organizationTypes;
        } else {
            // Can't return display label for given mimetype.
            return display;
        }

        if (type != customType) {
            CharSequence[] labels = context.getResources().getTextArray(arrayResId);
            try {
                display = labels[type - 1];
            } catch (ArrayIndexOutOfBoundsException e) {
                display = labels[defaultType - 1];
            }
        } else {
            if (!TextUtils.isEmpty(label)) {
                display = label;
            }
        }
        return display;
    }

    /**
     * Opens an InputStream for the person's photo and returns the photo as a Bitmap.
     * If the person's photo isn't present returns null.
     *
     * @param aggCursor the Cursor pointing to the data record containing the photo.
     * @param bitmapColumnIndex the column index where the photo Uri is stored.
     * @param options the decoding options, can be set to null
     * @return the photo Bitmap
     */
    public static Bitmap loadContactPhoto(Cursor cursor, int bitmapColumnIndex,
            BitmapFactory.Options options) {
        if (cursor == null) {
            return null;
        }

        byte[] data = cursor.getBlob(bitmapColumnIndex);
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    /**
     * Loads a placeholder photo.
     *
     * @param placeholderImageResource the resource to use for the placeholder image
     * @param context the Context
     * @param options the decoding options, can be set to null
     * @return the placeholder Bitmap.
     */
    public static Bitmap loadPlaceholderPhoto(int placeholderImageResource, Context context,
            BitmapFactory.Options options) {
        if (placeholderImageResource == 0) {
            return null;
        }
        return BitmapFactory.decodeResource(context.getResources(),
                placeholderImageResource, options);
    }

    public static Bitmap loadContactPhoto(Context context, long photoId,
            BitmapFactory.Options options) {
        Cursor photoCursor = null;
        Bitmap photoBm = null;

        try {
            photoCursor = context.getContentResolver().query(
                    ContentUris.withAppendedId(Data.CONTENT_URI, photoId),
                    new String[] { Photo.PHOTO },
                    null, null, null);

            if (photoCursor.moveToFirst() && !photoCursor.isNull(0)) {
                byte[] photoData = photoCursor.getBlob(0);
                photoBm = BitmapFactory.decodeByteArray(photoData, 0,
                        photoData.length, options);
            }
        } finally {
            if (photoCursor != null) {
                photoCursor.close();
            }
        }

        return photoBm;
    }

    /**
     * This looks up the provider name defined in
     * {@link android.provider.Im.ProviderNames} from the predefined IM protocol id.
     * This is used for interacting with the IM application.
     *
     * @param protocol the protocol ID
     * @return the provider name the IM app uses for the given protocol, or null if no
     * provider is defined for the given protocol
     * @hide
     */
    public static String lookupProviderNameFromId(int protocol) {
        switch (protocol) {
            case Im.PROTOCOL_GOOGLE_TALK:
                return ProviderNames.GTALK;
            case Im.PROTOCOL_AIM:
                return ProviderNames.AIM;
            case Im.PROTOCOL_MSN:
                return ProviderNames.MSN;
            case Im.PROTOCOL_YAHOO:
                return ProviderNames.YAHOO;
            case Im.PROTOCOL_ICQ:
                return ProviderNames.ICQ;
            case Im.PROTOCOL_JABBER:
                return ProviderNames.JABBER;
            case Im.PROTOCOL_SKYPE:
                return ProviderNames.SKYPE;
            case Im.PROTOCOL_QQ:
                return ProviderNames.QQ;
        }
        return null;
    }

}
