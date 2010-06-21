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
import com.android.contacts.views.editor.view.FieldAndTypeView;
import com.android.contacts.views.editor.view.ViewTypes;
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public abstract class FieldAndTypeViewModel extends BaseViewModel {
    private static final String TAG = "FieldAndTypeViewModel";

    private final long mDataId;
    private final ContentValues mContentValues;
    private final int mLabelResId;
    private final Uri mDataUri;
    private final String mFieldColumn;
    private final String mLabelColumn;
    private final String mTypeColumn;

    protected FieldAndTypeViewModel(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, int labelResId, String fieldColumn,
            String typeColumn, String labelColumn) {
        super(context, rawContact);
        mDataId = dataId;
        mDataUri = ContentUris.withAppendedId(Data.CONTENT_URI, mDataId);
        mContentValues = contentValues;
        mLabelResId = labelResId;

        mFieldColumn = fieldColumn;
        mTypeColumn = typeColumn;
        mLabelColumn = labelColumn;
    }

    @Override
    public int getEntryType() {
        return ViewTypes.FIELD_AND_TYPE;
    }

    @Override
    public FieldAndTypeView getView(LayoutInflater inflater, View convertView, ViewGroup parent) {
        final FieldAndTypeView result = convertView != null
                ? (FieldAndTypeView) convertView
                : FieldAndTypeView.inflate(inflater, parent, false);

        result.setListener(mViewListener);
        result.setLabelText(mLabelResId);
        result.setFieldValue(getFieldValue());
        result.setTypeDisplayLabel(getTypeDisplayLabel());

        return result;
    }

    public long getDataId() {
        return mDataId;
    }

    public ContentValues getContentValues() {
        return mContentValues;
    }

    private void saveData() {
        final ContentResolver resolver = getContext().getContentResolver();

        final ArrayList<ContentProviderOperation> operations =
            new ArrayList<ContentProviderOperation>();

        final Builder builder;
//        if (getDataUri() == null) {
//            // INSERT
//            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
//            builder.withValue(Data.MIMETYPE, mMimeType);
//            builder.withValue(Data.RAW_CONTACT_ID, getRawContactId());
//            writeToBuilder(builder);
//        } else {
            // UPDATE
            builder = ContentProviderOperation.newUpdate(mDataUri);
            writeToBuilder(builder);
//        }
        operations.add(builder.build());

        // Tell the Service to save
        final Intent serviceIntent = new Intent();
        final ContentProviderOperation[] operationsArray =
                operations.toArray(ArrayUtils.emptyArray(ContentProviderOperation.class));
        serviceIntent.putExtra(ContactSaveService.EXTRA_OPERATIONS, operationsArray);
        serviceIntent.setClass(getContext().getApplicationContext(), ContactSaveService.class);

        getContext().startService(serviceIntent);
    }

    private void writeToBuilder(final Builder builder) {
        builder.withValue(mFieldColumn, getFieldValue());
        builder.withValue(mTypeColumn, getType());
        builder.withValue(mLabelColumn, getLabel());
    }

    protected String getFieldValue() {
        return getContentValues().getAsString(mFieldColumn);
    }

    private void putFieldValue(String value) {
        getContentValues().put(mFieldColumn, value);
    }

    public int getType() {
        return getContentValues().getAsInteger(mTypeColumn).intValue();
    }

    private void putType(int value) {
        getContentValues().put(mTypeColumn, value);
    }

    public String getLabel() {
        return getContentValues().getAsString(mLabelColumn);
    }

    private void putLabel(String value) {
        getContentValues().put(mLabelColumn, value);
    }

    protected abstract CharSequence getTypeDisplayLabel();

    private FieldAndTypeView.Listener mViewListener = new FieldAndTypeView.Listener() {
        public void onFocusLost(FieldAndTypeView view) {
            Log.v(TAG, "Received FocusLost. Checking for changes");
            boolean hasChanged = false;

            final String oldValue = getFieldValue();
            final String newValue = view.getFieldValue().toString();
            if (!oldValue.equals(newValue)) {
                putFieldValue(newValue);
                hasChanged = true;
            }
            if (hasChanged) {
                Log.v(TAG, "Found changes. Updating DB");
                saveData();
            }
        }
    };
}
