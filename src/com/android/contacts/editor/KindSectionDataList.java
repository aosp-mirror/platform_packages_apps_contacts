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
import com.android.contacts.common.model.RawContactModifier;

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
     * Returns the primary KindSectionData and ValuesDelta that should be written for this List.
     */
    public Pair<KindSectionData,ValuesDelta> getEntryToWrite(long id,
            AccountWithDataSet primaryAccount, boolean isUserProfile) {
        final String mimeType = getMimeType();
        if (mimeType == null) return null;

        if (!isUserProfile) {
            if (id > 0) {
                // Look for a match for the ID that was passed in
                for (KindSectionData kindSectionData : this) {
                    if (kindSectionData.getAccountType().areContactsWritable()) {
                        final ValuesDelta valuesDelta = kindSectionData.getValuesDeltaById(id);
                        if (valuesDelta != null) {
                            vlog(mimeType + ": matched kind section data to write by ID");
                            return new Pair<>(kindSectionData, valuesDelta);
                        }
                    }
                }
            }

            // Look for a super primary entry
            for (KindSectionData kindSectionData : this) {
                if (kindSectionData.getAccountType().areContactsWritable()) {
                    final ValuesDelta valuesDelta = kindSectionData.getSuperPrimaryValuesDelta();
                    if (valuesDelta != null) {
                        vlog(mimeType + ": matched kind section data to write by super primary");
                        return new Pair<>(kindSectionData, valuesDelta);
                    }
                }
            }

            // Use the first writable contact that matches the primary account
            if (primaryAccount != null) {
                for (KindSectionData kindSectionData : this) {
                    if (kindSectionData.getAccountType().areContactsWritable()) {
                        if (matchesAccount(primaryAccount, kindSectionData.getRawContactDelta())
                            && !kindSectionData.getValuesDeltas().isEmpty()) {
                            vlog(mimeType + ": matched kind section data to write by primary " +
                                    "account");
                            return new Pair<>(kindSectionData,
                                    kindSectionData.getValuesDeltas().get(0));
                        }
                    }
                }
            }
        }

        // Just return the first writable entry.
        for (KindSectionData kindSectionData : this) {
            if (kindSectionData.getAccountType().areContactsWritable()) {
                // Create an entry if necessary
                RawContactModifier.ensureKindExists(kindSectionData.getRawContactDelta(),
                        kindSectionData.getAccountType(), mimeType);

                if (!kindSectionData.getValuesDeltas().isEmpty()) {
                    vlog(mimeType + ": falling back to first kind section data to write");
                    return new Pair<>(kindSectionData, kindSectionData.getValuesDeltas().get(0));
                }
            }
        }

        wlog(mimeType+ ": no writable kind section data found");
        return null;
    }

    /** Whether the given RawContactDelta belong to the given account. */
    private static boolean matchesAccount(AccountWithDataSet accountWithDataSet,
            RawContactDelta rawContactDelta) {
        return Objects.equals(accountWithDataSet.name, rawContactDelta.getAccountName())
                && Objects.equals(accountWithDataSet.type, rawContactDelta.getAccountType())
                && Objects.equals(accountWithDataSet.dataSet, rawContactDelta.getDataSet());
    }

    /**
     * Returns the KindSectionData and ValuesDelta that should be displayed to the user.
     */
    public Pair<KindSectionData,ValuesDelta> getEntryToDisplay(long id) {
        final String mimeType = getMimeType();
        if (mimeType == null) return null;

        if (id > 0) {
            // Look for a match for the ID that was passed in
            for (KindSectionData kindSectionData : this) {
                final ValuesDelta valuesDelta = kindSectionData.getValuesDeltaById(id);
                if (valuesDelta != null) {
                    vlog(mimeType + ": matched kind section data to display by ID");
                    return new Pair<>(kindSectionData, valuesDelta);
                }
            }
        }
        // Look for a super primary entry
        for (KindSectionData kindSectionData : this) {
            final ValuesDelta valuesDelta = kindSectionData.getSuperPrimaryValuesDelta();
                if (valuesDelta != null) {
                    vlog(mimeType + ": matched kind section data to display by super primary");
                    return new Pair<>(kindSectionData, valuesDelta);
                }
        }

        // Fall back to the first non-empty value
        for (KindSectionData kindSectionData : this) {
            final ValuesDelta valuesDelta = kindSectionData.getFirstNonEmptyValuesDelta();
            if (valuesDelta != null) {
                vlog(mimeType + ": using first non empty value to display");
                return new Pair<>(kindSectionData, valuesDelta);
            }
        }

        for (KindSectionData kindSectionData : this) {
            final List<ValuesDelta> valuesDeltaList = kindSectionData.getValuesDeltas();
            if (!valuesDeltaList.isEmpty()) {
                vlog(mimeType + ": falling back to first empty entry to display");
                final ValuesDelta valuesDelta = valuesDeltaList.get(0);
                return new Pair<>(kindSectionData, valuesDelta);
            }
        }

        wlog(mimeType + ": no kind section data found to display");
        return null;
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

    private static void wlog(String message) {
        if (Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, message);
        }
    }

    private static void vlog(String message) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, message);
        }
    }
}
