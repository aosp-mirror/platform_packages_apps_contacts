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

import com.android.contacts.views.ContactSaveService;
import com.android.contacts.views.editor.DisplayRawContact;
import com.android.internal.util.ArrayUtils;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ContentProviderOperation.Builder;
import android.net.Uri;
import android.provider.ContactsContract.Data;

import java.util.ArrayList;

public abstract class DataViewModel extends BaseViewModel {
    private final long mDataId;
    private final ContentValues mContentValues;
    private final String mMimeType;
    private final Uri mDataUri;

    protected DataViewModel(Context context, DisplayRawContact rawContact, long dataId,
            ContentValues contentValues, String mimeType) {
        super(context, rawContact);
        mDataId = dataId;
        mContentValues = contentValues;
        mMimeType = mimeType;
        mDataUri = ContentUris.withAppendedId(Data.CONTENT_URI, mDataId);
    }

    public long getDataId() {
        return mDataId;
    }

    public Uri getDataUri() {
        return mDataUri;
    }

    protected ContentValues getContentValues() {
        return mContentValues;
    }

    public String getMimeType() {
        return mMimeType;
    }

    /**
     * Uncoditionally saves the current state to the database. No difference analysis is performed
     */
    public void saveData() {
        final ContentResolver resolver = getContext().getContentResolver();

        final ArrayList<ContentProviderOperation> operations =
            new ArrayList<ContentProviderOperation>();

        final Builder builder;
        if (getDataUri() == null) {
            // INSERT
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValue(Data.MIMETYPE, getMimeType());
            builder.withValue(Data.RAW_CONTACT_ID, getRawContact().getId());
            writeToBuilder(builder, true);
        } else {
            // UPDATE
            builder = ContentProviderOperation.newUpdate(getDataUri());
            writeToBuilder(builder, false);
        }
        operations.add(builder.build());

        // Tell the Service to save
        // TODO: Handle the case where the data element has been removed in the background
        final Intent serviceIntent = new Intent();
        final ContentProviderOperation[] operationsArray =
                operations.toArray(ArrayUtils.emptyArray(ContentProviderOperation.class));
        serviceIntent.putExtra(ContactSaveService.EXTRA_OPERATIONS, operationsArray);
        serviceIntent.setClass(getContext().getApplicationContext(), ContactSaveService.class);

        getContext().startService(serviceIntent);
    }

    protected abstract void writeToBuilder(final Builder builder, boolean isInsert);
}
