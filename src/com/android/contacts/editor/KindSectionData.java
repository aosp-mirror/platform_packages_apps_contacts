/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountType.EditField;
import com.android.contacts.common.model.dataitem.DataKind;

import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holder for the multi account raw contact data needed to back an editor input field.
 */
public final class KindSectionData {

    private final AccountType mAccountType;
    private final List<ValuesDelta> mValuesDeltas;
    private final DataKind mDataKind;
    private final RawContactDelta mRawContactDelta;

    public KindSectionData(AccountType accountType, DataKind dataKind,
            RawContactDelta rawContactDelta) {
        mAccountType = accountType;
        mDataKind = dataKind;
        mRawContactDelta = rawContactDelta;

        // Note that for phonetic names we use the structured name mime type to look up values
        final String mimeType = DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(dataKind.mimeType)
                ? StructuredName.CONTENT_ITEM_TYPE : dataKind.mimeType;
        mValuesDeltas = mRawContactDelta.hasMimeEntries(mimeType)
                ? mRawContactDelta.getMimeEntries(mimeType)
                : Collections.EMPTY_LIST;
    }

    public AccountType getAccountType() {
        return mAccountType;
    }

    public boolean hasValuesDeltas() {
        return !mValuesDeltas.isEmpty();
    }

    public List<ValuesDelta> getValuesDeltas() {
        return mValuesDeltas;
    }

    /** Returns the super primary ValuesDelta for the data kind this section represents. */
    public ValuesDelta getSuperPrimaryValuesDelta() {
        for (ValuesDelta valuesDelta : mValuesDeltas) {
            if (valuesDelta.isSuperPrimary()) return valuesDelta;
        }
        return null;
    }

    /** Returns the ValuesDelta with the given ID. */
    public ValuesDelta getValuesDeltaById(Long id) {
        for (ValuesDelta valuesDelta : mValuesDeltas) {
            if (valuesDelta.getId().equals(id)) return valuesDelta;
        }
        return null;
    }

    /** Returns the first non empty ValuesDelta for the data kind this section represents. */
    public ValuesDelta getFirstNonEmptyValuesDelta() {
        for (ValuesDelta valuesDelta : mValuesDeltas) {
            if (!isEmpty(valuesDelta)) return valuesDelta;
        }
        return null;
    }

    /** Whether the given ValuesDelta is empty for the data kind this section represents. */
    public boolean isEmpty(ValuesDelta valuesDelta) {
        if (valuesDelta.isNoop()) return true;

        if (mDataKind.fieldList != null) {
            for (EditField editField : mDataKind.fieldList) {
                final String column = editField.column;
                final String value = valuesDelta.getAsString(column);
                if (TextUtils.isEmpty(value)) return true;
            }
        }
        return false;
    }

    public DataKind getDataKind() {
        return mDataKind;
    }

    public RawContactDelta getRawContactDelta() {
        return mRawContactDelta;
    }

    public String toString() {
        return String.format("%s<accountType=%s dataSet=%s values=%s>",
                KindSectionData.class.getSimpleName(),
                mAccountType.accountType,
                mAccountType.dataSet,
                hasValuesDeltas() ? getValuesDeltas().size() : "null");
    }
}