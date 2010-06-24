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
import com.android.contacts.model.ContactsSource.EditField;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.views.editor.DisplayRawContact;
import com.android.contacts.views.editor.view.EditorItemView;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContentProviderOperation.Builder;
import android.provider.ContactsContract.CommonDataKinds.BaseTypes;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

public class GenericViewModel extends DataViewModel {
    private static final String TAG = "GenericViewModel";

    private final int mLabelResId;
    private final Field[] mFields;
    private final int[] mTypeResIds;
    private final int mCustomTypeIndex;
    private final String mTypeColumn;
    private final String mLabelColumn;

    private EditorItemView mEditorItemView;

    /**
     * Overload without custom types
     */
    protected GenericViewModel(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, String mimeType, int labelResId,
            Field[] fields) {
        this(context, rawContact, dataId, contentValues, mimeType, labelResId, fields,
                null, -1, null, null);
    }

    protected GenericViewModel(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, String mimeType, int labelResId,
            Field[] fields, int[] typeResIds, int customTypeIndex,
            String typeColumn, String labelColumn) {
        super(context, rawContact, dataId, contentValues, mimeType);
        mLabelResId = labelResId;
        mFields = fields;
        mTypeResIds = typeResIds;
        mCustomTypeIndex = customTypeIndex;
        mTypeColumn = typeColumn;
        mLabelColumn = labelColumn;
    }

    public static GenericViewModel fromDataKind(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, DataKind dataKind) {
        final Field[] fields = new Field[dataKind.fieldList.size()];
        for (int i = 0; i < fields.length; i++) {
            final EditField editField = dataKind.fieldList.get(i);
            fields[i] = new Field(editField.titleRes, editField.column);
        }
        final int[] typeResIds;
        if (dataKind.typeList == null) {
            typeResIds = null;
        } else {
            typeResIds = new int[dataKind.typeList.size()];
            for (int i = 0; i < typeResIds.length; i++) {
                final EditType editType = dataKind.typeList.get(i);
                typeResIds[i] = editType.labelRes;
            }
        }
        return new GenericViewModel(context, rawContact, dataId, contentValues, dataKind.mimeType,
                dataKind.titleRes, fields, typeResIds, BaseTypes.TYPE_CUSTOM,
                dataKind.typeColumn, "");
    }

    @Override
    public EditorItemView createAndAddView(LayoutInflater inflater, ViewGroup parent) {
        if (mEditorItemView == null) {
            final EditorItemView result = new EditorItemView(getContext());
            result.setListener(mViewListener);

            final int[] fieldResIds = new int[mFields.length];
            for (int i = 0; i < mFields.length; i++) {
                fieldResIds[i] = mFields[i].getResId();
            }

            result.configure(mLabelResId, fieldResIds, mTypeResIds, mCustomTypeIndex);

            parent.addView(result);

            mEditorItemView = result;
        }

        // Set fields
        final ContentValues contentValues = getContentValues();
        for (int i = 0; i < mFields.length; i++) {
            mEditorItemView.setFieldValue(i, contentValues.getAsString(mFields[i].getName()));
        }

        // Set type if required
        if (mTypeColumn != null) {
            mEditorItemView.setType(getTypeValue(), getLabelValue());
        }

        return mEditorItemView;
    }

    @Override
    protected void writeToBuilder(Builder builder, boolean isInsert) {
        for (int i = 0; i < mFields.length; i++) {
            final String fieldName = mFields[i].getName();
            builder.withValue(fieldName, getContentValues().getAsString(fieldName));
        }
        if (mTypeColumn != null) {
            builder.withValue(mTypeColumn, getTypeValue());
            builder.withValue(mLabelColumn, getLabelValue());
        }
    }

    private int getTypeValue() {
        return getContentValues().getAsInteger(mTypeColumn);
    }

    private String getLabelValue() {
        return getContentValues().getAsString(mLabelColumn);
    }

    private EditorItemView.Listener mViewListener = new EditorItemView.Listener() {
        public void onFocusLost() {
            Log.v(TAG, "Received FocusLost. Checking for changes");
            boolean hasChanged = false;

            final ContentValues contentValues = getContentValues();

            for (int i = 0; i < mFields.length; i++) {
                final String oldValue = contentValues.getAsString(mFields[i].getName());
                final String newValue = mEditorItemView.getFieldValue(i);
                if (!TextUtils.equals(oldValue, newValue)) {
                    contentValues.put(mFields[i].getName(), newValue);
                    hasChanged = true;
                }
            }
            if (hasChanged) {
                Log.v(TAG, "Found changes. Updating DB");
                saveData();
            }
        }

        @Override
        public void onTypeChanged(int newIndex, String customText) {
            // TODO Auto-generated method stub

        }
    };

    /* package */ static class Field {
        private int mResId;
        private String mName;

        public int getResId() {
            return mResId;
        }

        public String getName() {
            return mName;
        }

        public Field(int resourceId, String databaseField) {
            mResId = resourceId;
            mName = databaseField;
        }
    }
}
