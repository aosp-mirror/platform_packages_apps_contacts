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

import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.android.contacts.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.model.dataitem.StructuredNameDataItem;

/**
 * A dedicated editor for phonetic name. It is similar to {@link StructuredNameEditorView}.
 */
public class PhoneticNameEditorView extends TextFieldsEditorView {

    private static class PhoneticValuesDelta extends ValuesDelta {
        private ValuesDelta mValues;
        private String mPhoneticName;

        public PhoneticValuesDelta(ValuesDelta values) {
            mValues = values;
            buildPhoneticName();
        }

        @Override
        public void put(String key, String value) {
            if (key.equals(DataKind.PSEUDO_COLUMN_PHONETIC_NAME)) {
                mPhoneticName = value;
                parsePhoneticName(value);
            } else {
                mValues.put(key, value);
                buildPhoneticName();
            }
        }

        @Override
        public String getAsString(String key) {
            if (key.equals(DataKind.PSEUDO_COLUMN_PHONETIC_NAME)) {
                return mPhoneticName;
            } else {
                return mValues.getAsString(key);
            }
        }

        private void parsePhoneticName(String value) {
            StructuredNameDataItem dataItem = PhoneticNameEditorView.parsePhoneticName(value, null);
            mValues.setPhoneticFamilyName(dataItem.getPhoneticFamilyName());
            mValues.setPhoneticMiddleName(dataItem.getPhoneticMiddleName());
            mValues.setPhoneticGivenName(dataItem.getPhoneticGivenName());
        }

        private void buildPhoneticName() {
            String family = mValues.getPhoneticFamilyName();
            String middle = mValues.getPhoneticMiddleName();
            String given = mValues.getPhoneticGivenName();
            mPhoneticName = PhoneticNameEditorView.buildPhoneticName(family, middle, given);
        }

        @Override
        public Long getId() {
            return mValues.getId();
        }

        @Override
        public boolean isVisible() {
            return mValues.isVisible();
        }
    }

    /**
     * Parses phonetic name and returns parsed data (family, middle, given) as ContentValues.
     * Parsed data should be {@link StructuredName#PHONETIC_FAMILY_NAME},
     * {@link StructuredName#PHONETIC_MIDDLE_NAME}, and
     * {@link StructuredName#PHONETIC_GIVEN_NAME}.
     * If this method cannot parse given phoneticName, null values will be stored.
     *
     * @param phoneticName Phonetic name to be parsed
     * @param values ContentValues to be used for storing data. If null, new instance will be
     * created.
     * @return ContentValues with parsed data. Those data can be null.
     */
    public static StructuredNameDataItem parsePhoneticName(String phoneticName,
            StructuredNameDataItem item) {
        String family = null;
        String middle = null;
        String given = null;

        if (!TextUtils.isEmpty(phoneticName)) {
            String[] strings = phoneticName.split(" ", 3);
            switch (strings.length) {
                case 1:
                    family = strings[0];
                    break;
                case 2:
                    family = strings[0];
                    given = strings[1];
                    break;
                case 3:
                    family = strings[0];
                    middle = strings[1];
                    given = strings[2];
                    break;
            }
        }

        if (item == null) {
            item = new StructuredNameDataItem();
        }
        item.setPhoneticFamilyName(family);
        item.setPhoneticMiddleName(middle);
        item.setPhoneticGivenName(given);
        return item;
    }

    /**
     * Constructs and returns a phonetic full name from given parts.
     */
    public static String buildPhoneticName(String family, String middle, String given) {
        if (!TextUtils.isEmpty(family) || !TextUtils.isEmpty(middle)
                || !TextUtils.isEmpty(given)) {
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(family)) {
                sb.append(family.trim()).append(' ');
            }
            if (!TextUtils.isEmpty(middle)) {
                sb.append(middle.trim()).append(' ');
            }
            if (!TextUtils.isEmpty(given)) {
                sb.append(given.trim()).append(' ');
            }
            sb.setLength(sb.length() - 1);  // Yank the last space
            return sb.toString();
        } else {
            return null;
        }
    }

    public static boolean isUnstructuredPhoneticNameColumn(String column) {
        return DataKind.PSEUDO_COLUMN_PHONETIC_NAME.equals(column);
    }

    public PhoneticNameEditorView(Context context) {
        super(context);
    }

    public PhoneticNameEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhoneticNameEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setValues(DataKind kind, ValuesDelta entry, RawContactDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        if (!(entry instanceof PhoneticValuesDelta)) {
            entry = new PhoneticValuesDelta(entry);
        }
        super.setValues(kind, entry, state, readOnly, vig);
    }

    @Override
    public void onFieldChanged(String column, String value) {
        if (!isFieldChanged(column, value)) {
            return;
        }

        if (hasShortAndLongForms()) {
            PhoneticValuesDelta entry = (PhoneticValuesDelta) getEntry();

            // Determine whether the user is modifying the structured or unstructured phonetic
            // name field. See a similar approach in {@link StructuredNameEditor#onFieldChanged}.
            // This is because on device rotation, a hidden TextView's onRestoreInstanceState() will
            // be called and incorrectly restore a null value for the hidden field, which ultimately
            // modifies the underlying phonetic name. Hence, ignore onFieldChanged() update requests
            // from fields that aren't visible.
            boolean isEditingUnstructuredPhoneticName = !areOptionalFieldsVisible();

            if (isEditingUnstructuredPhoneticName == isUnstructuredPhoneticNameColumn(column)) {
                // Call into the superclass to update the field and rebuild the underlying
                // phonetic name.
                super.onFieldChanged(column, value);
            }
        } else {
            // All fields are always visible, so we don't have to worry about blocking updates
            // from onRestoreInstanceState() from hidden fields. Always call into the superclass
            // to update the field and rebuild the underlying phonetic name.
            super.onFieldChanged(column, value);
        }
    }

    public boolean hasData() {
        ValuesDelta entry = getEntry();

        String family = entry.getPhoneticFamilyName();
        String middle = entry.getPhoneticMiddleName();
        String given = entry.getPhoneticGivenName();

        return !TextUtils.isEmpty(family) || !TextUtils.isEmpty(middle)
                || !TextUtils.isEmpty(given);
    }
}
