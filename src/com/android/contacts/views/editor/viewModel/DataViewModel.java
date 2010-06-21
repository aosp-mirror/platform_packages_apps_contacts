/*
 * Copyright (C) 2010 Google Inc.
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
 * limitations under the License
 */

package com.android.contacts.views.editor.viewModel;

import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.util.Constants;
import com.android.contacts.views.editor.DisplayRawContact;
import com.android.contacts.views.editor.view.DataView;
import com.android.contacts.views.editor.view.PhotoView;
import com.android.contacts.views.editor.view.ViewTypes;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.telephony.PhoneNumberUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class DataViewModel extends BaseViewModel {
    public String label;
    public String data;
    public Uri uri;
    public long id = 0;
    public int maxLines = 1;
    public String mimetype;

    public int actionIcon = -1;
    public boolean isPrimary = false;
    public Intent intent;
    public Intent secondaryIntent = null;
    public int maxLabelLines = 1;
    public byte[] binaryData = null;

    /**
     * Build new {@link DataViewModel} and populate from the given values.
     */
    public DataViewModel(Context context, String mimeType, DataKind kind,
            DisplayRawContact rawContact, long dataId, ContentValues values) {
        super(context, rawContact);
        id = dataId;
        uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);
        mimetype = mimeType;
        label = buildActionString(kind, values, false, context);
        data = buildDataString(kind, values, context);
        binaryData = values.getAsByteArray(Data.DATA15);
    }

    @Override
    public int getEntryType() {
        return Photo.CONTENT_ITEM_TYPE.equals(mimetype) ? ViewTypes.PHOTO : ViewTypes.DATA;
    }

    @Override
    public View getView(LayoutInflater inflater, View convertView, ViewGroup parent) {
        // Special Case: Photo
        if (Photo.CONTENT_ITEM_TYPE.equals(mimetype)) {
            final PhotoView result = convertView != null
                    ? (PhotoView) convertView
                    : PhotoView.inflate(inflater, parent, false);

            final Bitmap bitmap = binaryData != null
                    ? BitmapFactory.decodeByteArray(binaryData, 0, binaryData.length)
                    : null;
            result.setPhoto(bitmap);
            return result;
        }

        // All other cases
        final DataView result = convertView != null
                ? (DataView) convertView
                : DataView.inflate(inflater, parent, false);

        // Set the label
        result.setLabelText(label, maxLabelLines);

        // Set data
        if (data != null) {
            if (Phone.CONTENT_ITEM_TYPE.equals(mimetype)
                    || Constants.MIME_SMS_ADDRESS.equals(mimetype)) {
                result.setDataText(PhoneNumberUtils.formatNumber(data), maxLines);
            } else {
                result.setDataText(data, maxLines);
            }
        } else {
            result.setDataText("", maxLines);
        }

        // Set the primary icon
        result.setPrimary(isPrimary);

        // Set the action icon
        result.setPrimaryIntent(intent, getContext().getResources(), actionIcon);

        // Set the secondary action button
        // TODO: Change this to our new form
        result.setSecondaryIntent(null, null, 0);
        return result;
    }

    private static String buildActionString(DataKind kind, ContentValues values,
            boolean lowerCase, Context context) {
        if (kind.actionHeader == null) {
            return null;
        }
        CharSequence actionHeader = kind.actionHeader.inflateUsing(context, values);
        if (actionHeader == null) {
            return null;
        }
        return lowerCase ? actionHeader.toString().toLowerCase() : actionHeader.toString();
    }

    private static String buildDataString(DataKind kind, ContentValues values,
            Context context) {
        if (kind.actionBody == null) {
            return null;
        }
        CharSequence actionBody = kind.actionBody.inflateUsing(context, values);
        return actionBody == null ? null : actionBody.toString();
    }

}
