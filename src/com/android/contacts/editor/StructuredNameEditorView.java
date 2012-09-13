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

package com.android.contacts.editor;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.android.contacts.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.model.dataitem.StructuredNameDataItem;
import com.android.contacts.util.NameConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * A dedicated editor for structured name.  When the user collapses/expands
 * the structured name, it will reparse or recompose the name, but only
 * if the user has made changes.  This distinction will be particularly
 * obvious if the name has a non-standard structure. Consider this structure:
 * first name="John Doe", family name="".  As long as the user does not change
 * the full name, expand and collapse will preserve this.  However, if the user
 * changes "John Doe" to "Jane Doe" and then expands the view, we will reparse
 * and show first name="Jane", family name="Doe".
 */
public class StructuredNameEditorView extends TextFieldsEditorView {

    private StructuredNameDataItem mSnapshot;
    private boolean mChanged;

    public StructuredNameEditorView(Context context) {
        super(context);
    }

    public StructuredNameEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StructuredNameEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setValues(DataKind kind, ValuesDelta entry, RawContactDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        super.setValues(kind, entry, state, readOnly, vig);
        if (mSnapshot == null) {
            mSnapshot = (StructuredNameDataItem) DataItem.createFrom(
                    new ContentValues(getValues().getCompleteValues()));
            mChanged = entry.isInsert();
        } else {
            mChanged = false;
        }
    }

    @Override
    public void onFieldChanged(String column, String value) {
        if (!isFieldChanged(column, value)) {
            return;
        }

        // First save the new value for the column.
        saveValue(column, value);
        mChanged = true;

        // Next make sure the display name and the structured name are synced
        if (hasShortAndLongForms()) {
            if (areOptionalFieldsVisible()) {
                rebuildFullName(getValues());
            } else {
                rebuildStructuredName(getValues());
            }
        }

        // Then notify the listener, which will rely on the display and structured names to be
        // synced (in order to provide aggregate suggestions).
        notifyEditorListener();
    }

    @Override
    protected void onOptionalFieldVisibilityChange() {
        if (hasShortAndLongForms()) {
            if (areOptionalFieldsVisible()) {
                switchFromFullNameToStructuredName();
            } else {
                switchFromStructuredNameToFullName();
            }
        }

        super.onOptionalFieldVisibilityChange();
    }

    private void switchFromFullNameToStructuredName() {
        ValuesDelta values = getValues();

        if (!mChanged) {
            for (String field : NameConverter.STRUCTURED_NAME_FIELDS) {
                values.put(field, mSnapshot.getContentValues().getAsString(field));
            }
            return;
        }

        String displayName = values.getDisplayName();
        Map<String, String> structuredNameMap = NameConverter.displayNameToStructuredName(
                getContext(), displayName);
        if (!structuredNameMap.isEmpty()) {
            eraseFullName(values);
            for (String field : structuredNameMap.keySet()) {
                values.put(field, structuredNameMap.get(field));
            }
        }

        mSnapshot.getContentValues().clear();
        mSnapshot.getContentValues().putAll(values.getCompleteValues());
        mSnapshot.setDisplayName(displayName);
    }

    private void switchFromStructuredNameToFullName() {
        ValuesDelta values = getValues();

        if (!mChanged) {
            values.setDisplayName(mSnapshot.getDisplayName());
            return;
        }

        Map<String, String> structuredNameMap = valuesToStructuredNameMap(values);
        String displayName = NameConverter.structuredNameToDisplayName(getContext(),
                structuredNameMap);
        if (!TextUtils.isEmpty(displayName)) {
            eraseStructuredName(values);
            values.put(StructuredName.DISPLAY_NAME, displayName);
        }

        mSnapshot.getContentValues().clear();
        mSnapshot.setDisplayName(values.getDisplayName());
        for (String field : structuredNameMap.keySet()) {
            mSnapshot.getContentValues().put(field, structuredNameMap.get(field));
        }
    }

    private Map<String, String> valuesToStructuredNameMap(ValuesDelta values) {
        Map<String, String> structuredNameMap = new HashMap<String, String>();
        for (String key : NameConverter.STRUCTURED_NAME_FIELDS) {
            structuredNameMap.put(key, values.getAsString(key));
        }
        return structuredNameMap;
    }

    private void eraseFullName(ValuesDelta values) {
        values.setDisplayName(null);
    }

    private void rebuildFullName(ValuesDelta values) {
        Map<String, String> structuredNameMap = valuesToStructuredNameMap(values);
        String displayName = NameConverter.structuredNameToDisplayName(getContext(),
                structuredNameMap);
        values.setDisplayName(displayName);
    }

    private void eraseStructuredName(ValuesDelta values) {
        for (String field : NameConverter.STRUCTURED_NAME_FIELDS) {
            values.putNull(field);
        }
    }

    private void rebuildStructuredName(ValuesDelta values) {
        String displayName = values.getDisplayName();
        Map<String, String> structuredNameMap = NameConverter.displayNameToStructuredName(
                getContext(), displayName);
        for (String field : structuredNameMap.keySet()) {
            values.put(field, structuredNameMap.get(field));
        }
    }

    private static void appendQueryParameter(Uri.Builder builder, String field, String value) {
        if (!TextUtils.isEmpty(value)) {
            builder.appendQueryParameter(field, value);
        }
    }

    /**
     * Set the display name onto the text field directly.  This does not affect the underlying
     * data structure so it is similar to the user typing the value in on the field directly.
     *
     * @param name The name to set on the text field.
     */
    public void setDisplayName(String name) {
        // For now, assume the first text field is the name.
        // TODO: Find a better way to get a hold of the name field.
        super.setValue(0, name);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.mChanged = mChanged;
        state.mSnapshot = mSnapshot.getContentValues();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.mSuperState);

        mChanged = ss.mChanged;
        mSnapshot = (StructuredNameDataItem) DataItem.createFrom(ss.mSnapshot);
    }

    private static class SavedState implements Parcelable {
        public boolean mChanged;
        public ContentValues mSnapshot;
        public Parcelable mSuperState;

        SavedState(Parcelable superState) {
            mSuperState = superState;
        }

        private SavedState(Parcel in) {
            ClassLoader loader = getClass().getClassLoader();
            mSuperState = in.readParcelable(loader);

            mChanged = in.readInt() != 0;
            mSnapshot = in.readParcelable(loader);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeParcelable(mSuperState, 0);

            out.writeInt(mChanged ? 1 : 0);
            out.writeParcelable(mSnapshot, 0);
        }

        @SuppressWarnings({"unused"})
        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
