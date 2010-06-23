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
import com.android.contacts.views.editor.view.SingleFieldView;
import com.android.contacts.views.editor.view.ViewTypes;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContentProviderOperation.Builder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class SingleFieldViewModel extends DataViewModel {
    private static final String TAG = "SingleFieldViewModel";

    private final int mLabelResId;
    private final String mFieldColumn;

    protected SingleFieldViewModel(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, String mimeType, int labelResId,
            String fieldColumn) {
        super(context, rawContact, dataId, contentValues, mimeType);
        mLabelResId = labelResId;
        mFieldColumn = fieldColumn;
    }

    @Override
    public int getEntryType() {
        return ViewTypes.SINGLE_FIELD;
    }

    @Override
    public SingleFieldView getView(LayoutInflater inflater, ViewGroup parent) {
        final SingleFieldView result = SingleFieldView.inflate(inflater, parent, false);

        result.setListener(mViewListener);
        result.setLabelText(mLabelResId);
        result.setFieldValue(getFieldValue());

        return result;
    }

    @Override
    protected void writeToBuilder(Builder builder, boolean isInsert) {
        builder.withValue(mFieldColumn, getFieldValue());
    }

    protected String getFieldValue() {
        return getContentValues().getAsString(mFieldColumn);
    }

    protected void putFieldValue(String value) {
        getContentValues().put(mFieldColumn, value);
    }

    private SingleFieldView.Listener mViewListener = new SingleFieldView.Listener() {
        public void onFocusLost(SingleFieldView view) {
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
