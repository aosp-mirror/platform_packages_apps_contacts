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

import com.android.contacts.views.editor.DisplayRawContact;
import com.android.contacts.views.editor.view.FieldAndTypeView;
import com.android.contacts.views.editor.view.ViewTypes;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContentProviderOperation.Builder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class FieldAndTypeViewModel extends DataViewModel {
    private static final String TAG = "FieldAndTypeViewModel";

    private final int mLabelResId;
    private final String mFieldColumn;
    private final String mLabelColumn;
    private final String mTypeColumn;

    protected FieldAndTypeViewModel(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, String mimeType, int labelResId,
            String fieldColumn, String typeColumn, String labelColumn) {
        super(context, rawContact, dataId, contentValues, mimeType);
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
    public FieldAndTypeView getView(LayoutInflater inflater, ViewGroup parent) {
        final FieldAndTypeView result = FieldAndTypeView.inflate(inflater, parent, false);

        result.setListener(mViewListener);
        result.setLabelText(mLabelResId);
        result.setFieldValue(getFieldValue());
        result.setTypeDisplayLabel(getTypeDisplayLabel());

        return result;
    }

    @Override
    protected void writeToBuilder(Builder builder, boolean isInsert) {
        builder.withValue(mFieldColumn, getFieldValue());
        builder.withValue(mTypeColumn, getType());
        builder.withValue(mLabelColumn, getLabel());
    }

    protected String getFieldValue() {
        return getContentValues().getAsString(mFieldColumn);
    }

    protected void putFieldValue(String value) {
        getContentValues().put(mFieldColumn, value);
    }

    protected int getType() {
        return getContentValues().getAsInteger(mTypeColumn).intValue();
    }

    protected void putType(int value) {
        getContentValues().put(mTypeColumn, value);
    }

    protected String getLabel() {
        return getContentValues().getAsString(mLabelColumn);
    }

    protected void putLabel(String value) {
        getContentValues().put(mLabelColumn, value);
    }

    protected abstract CharSequence getTypeDisplayLabel();

    private FieldAndTypeView.Listener mViewListener = new FieldAndTypeView.Listener() {
        public void onFocusLost(FieldAndTypeView view) {
            Log.v(TAG, "Received FocusLost. Checking for changes");
            boolean hasChanged = false;

            final String oldValue = getFieldValue();
            final String newValue = view.getFieldValue().toString();
            if (!TextUtils.equals(oldValue, newValue)) {
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
