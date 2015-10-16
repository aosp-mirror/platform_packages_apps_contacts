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
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.DataKind;

import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Container for multiple {@link KindSectionData} objects.  Provides convenience methods for
 * interrogating the collection for a certain KindSectionData item (e.g. the first writable, or
 * "primary", one.  Also enforces that only items with the same DataKind/mime-type are added.
 */
public class KindSectionDataList extends ArrayList<KindSectionData> {

    private static final String TAG = CompactRawContactsEditorView.TAG;

    /**
     * Returns the mime type for all DataKinds in this List.
     */
    public String getMimeType() {
        if (isEmpty()) return null;
        return get(0).getDataKind().mimeType;
    }

    /**
     * Returns the DataKind for all entries in this List.
     */
    public DataKind getDataKind() {
        return isEmpty() ? null : get(0).getDataKind();
    }

    /**
     * Returns the "primary" KindSectionData and ValuesDelta that should be written for this List.
     */
    public Pair<KindSectionData,ValuesDelta> getEntryToWrite(AccountWithDataSet primaryAccount,
            boolean hasNewContact) {
        // Use the first writable contact that matches the primary account
        if (primaryAccount != null && !hasNewContact) {
            for (KindSectionData kindSectionData : this) {
                if (kindSectionData.getAccountType().areContactsWritable()
                        && !kindSectionData.getValuesDeltas().isEmpty()) {
                    if (matchesAccount(primaryAccount, kindSectionData.getRawContactDelta())) {
                        return new Pair<>(kindSectionData,
                                kindSectionData.getValuesDeltas().get(0));
                    }
                }
            }
        }

        // If no writable raw contact matched the primary account, or we're editing a read-only
        // contact, just return the first writable entry.
        for (KindSectionData kindSectionData : this) {
            if (kindSectionData.getAccountType().areContactsWritable()) {
                if (!kindSectionData.getValuesDeltas().isEmpty()) {
                    return new Pair<>(kindSectionData, kindSectionData.getValuesDeltas().get(0));
                }
            }
        }

        return null;
    }

    /** Whether the given RawContactDelta belong to the given account. */
    private static boolean matchesAccount(AccountWithDataSet accountWithDataSet,
            RawContactDelta rawContactDelta) {
        if (accountWithDataSet == null) return false;
        return Objects.equals(accountWithDataSet.name, rawContactDelta.getAccountName())
                && Objects.equals(accountWithDataSet.type, rawContactDelta.getAccountType())
                && Objects.equals(accountWithDataSet.dataSet, rawContactDelta.getDataSet());
    }

    /**
     * Returns the "primary" KindSectionData and ValuesDelta that should be displayed to the user.
     */
    public Pair<KindSectionData,ValuesDelta> getEntryToDisplay(long id) {
        final String mimeType = getMimeType();
        if (mimeType == null) return null;

        KindSectionData resultKindSectionData = null;
        ValuesDelta resultValuesDelta = null;
        if (id > 0) {
            // Look for a match for the ID that was passed in
            for (KindSectionData kindSectionData : this) {
                resultValuesDelta = kindSectionData.getValuesDeltaById(id);
                if (resultValuesDelta != null) {
                    vlog(mimeType + ": matched kind section data by ID");
                    resultKindSectionData = kindSectionData;
                    break;
                }
            }
        }
        if (resultKindSectionData == null) {
            // Look for a super primary entry
            for (KindSectionData kindSectionData : this) {
                resultValuesDelta = kindSectionData.getSuperPrimaryValuesDelta();
                if (resultValuesDelta != null) {
                    vlog(mimeType + ": matched super primary kind section data");
                    resultKindSectionData = kindSectionData;
                    break;
                }
            }
        }
        if (resultKindSectionData == null) {
            // Fall back to the first non-empty value
            for (KindSectionData kindSectionData : this) {
                resultValuesDelta = kindSectionData.getFirstNonEmptyValuesDelta();
                if (resultValuesDelta != null) {
                    vlog(mimeType + ": using first non empty value");
                    resultKindSectionData = kindSectionData;
                    break;
                }
            }
        }
        if (resultKindSectionData == null || resultValuesDelta == null) {
            final List<ValuesDelta> valuesDeltaList = get(0).getValuesDeltas();
            if (valuesDeltaList != null && !valuesDeltaList.isEmpty()) {
                vlog(mimeType + ": falling back to first empty entry");
                resultValuesDelta = valuesDeltaList.get(0);
                resultKindSectionData = get(0);
            }
        }
        return resultKindSectionData != null && resultValuesDelta != null
                ? new Pair<>(resultKindSectionData, resultValuesDelta) : null;
    }

    @Override
    public boolean add(KindSectionData kindSectionData) {
        if (kindSectionData == null) throw new NullPointerException();

        // Enforce that only entries of the same type are added to this list
        final String listMimeType = getMimeType();
        if (listMimeType != null) {
            final String newEntryMimeType = kindSectionData.getDataKind().mimeType;
            if (!listMimeType.equals(newEntryMimeType)) {
                throw new IllegalArgumentException(
                        "Can't add " + newEntryMimeType + " to list with type " + listMimeType);
            }
        }
        return super.add(kindSectionData);
    }

    private static void vlog(String message) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, message);
        }
    }
}
