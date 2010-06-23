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
import com.android.contacts.views.editor.view.SimpleOrStructuredView;
import com.android.contacts.views.editor.view.ViewTypes;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContentProviderOperation.Builder;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Editor for the StructuredName. Handles both the structured representation as well the
 * single field display
 */
public class StructuredNameViewModel extends DataViewModel {
    protected static final String TAG = "StructuredNameViewModel";
    private final int mLabelResId;

    private StructuredNameViewModel(Context context, DisplayRawContact rawContact, long dataId,
            ContentValues contentValues, int labelResId) {
        super(context, rawContact, dataId, contentValues, StructuredName.CONTENT_ITEM_TYPE);
        mLabelResId = labelResId;
    }

    public static StructuredNameViewModel createForExisting(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, int labelResId) {
        return new StructuredNameViewModel(context, rawContact, dataId, contentValues, labelResId);
    }

    @Override
    protected void writeToBuilder(Builder builder, boolean isInsert) {
        // TODO: Handle both structured and unstructured inputs.
        // if (structuredEntered()) {
        // } else {
        builder.withValue(StructuredName.DISPLAY_NAME, getDisplayName());
        builder.withValue(StructuredName.GIVEN_NAME, null);
        builder.withValue(StructuredName.FAMILY_NAME, null);
        builder.withValue(StructuredName.PREFIX, null);
        builder.withValue(StructuredName.MIDDLE_NAME, null);
        builder.withValue(StructuredName.SUFFIX, null);
        // }
    }

    @Override
    public int getEntryType() {
        return ViewTypes.SIMPLE_OR_STRUCTURED;
    }

    @Override
    public View getView(LayoutInflater inflater, View convertView, ViewGroup parent) {
        final SimpleOrStructuredView result = convertView != null
                ? (SimpleOrStructuredView) convertView
                : SimpleOrStructuredView.inflate(inflater, parent, false);

        result.setListener(mViewListener);
        result.setLabelText(mLabelResId);
        result.setDisplayName(getDisplayName());

        return result;
    }

    private String getDisplayName() {
        return getContentValues().getAsString(StructuredName.DISPLAY_NAME);
    }

    private void putDisplayName(String value) {
        getContentValues().put(StructuredName.DISPLAY_NAME, value);
    }

    private SimpleOrStructuredView.Listener mViewListener = new SimpleOrStructuredView.Listener() {
        public void onFocusLost(SimpleOrStructuredView view) {
            Log.v(TAG, "Received FocusLost. Checking for changes");
            boolean hasChanged = false;

            final String oldValue = getDisplayName();
            final String newValue = view.getDisplayName().toString();
            if (!TextUtils.equals(oldValue, newValue)) {
                putDisplayName(newValue);
                hasChanged = true;
            }
            if (hasChanged) {
                Log.v(TAG, "Found changes. Updating DB");
                saveData();
            }
        }

        public void onStructuredEditorRequested(SimpleOrStructuredView view) {
            // TODO
        }
    };
}
