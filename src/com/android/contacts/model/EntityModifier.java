/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.model;

import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.EntityDelta.ValuesDelta;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Intents.Insert;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper methods for modifying an {@link EntityDelta}, such as inserting
 * new rows, or enforcing {@link ContactsSource}.
 */
public class EntityModifier {
    /**
     * For the given {@link EntityDelta}, determine if the given
     * {@link DataKind} could be inserted under specific
     * {@link ContactsSource}.
     */
    public static boolean canInsert(EntityDelta state, DataKind kind) {
        // Insert possible when have valid types and under overall maximum
        final boolean validTypes = hasValidTypes(state, kind);
        final boolean validOverall = (kind.typeOverallMax == -1)
                || (state.getEntryCount(true) < kind.typeOverallMax);
        return (validTypes && validOverall);
    }

    public static boolean hasValidTypes(EntityDelta state, DataKind kind) {
        if (EntityModifier.hasEditTypes(kind)) {
            return (getValidTypes(state, kind).size() > 0);
        } else {
            return true;
        }
    }

    /**
     * For the given {@link EntityDelta} and {@link DataKind}, return the
     * list possible {@link EditType} options available based on
     * {@link ContactsSource}.
     */
    public static ArrayList<EditType> getValidTypes(EntityDelta state, DataKind kind) {
        return getValidTypes(state, kind, null, true, null);
    }

    /**
     * For the given {@link EntityDelta} and {@link DataKind}, return the
     * list possible {@link EditType} options available based on
     * {@link ContactsSource}.
     *
     * @param forceInclude Always include this {@link EditType} in the returned
     *            list, even when an otherwise-invalid choice. This is useful
     *            when showing a dialog that includes the current type.
     */
    public static ArrayList<EditType> getValidTypes(EntityDelta state, DataKind kind,
            EditType forceInclude) {
        return getValidTypes(state, kind, forceInclude, true, null);
    }

    /**
     * For the given {@link EntityDelta} and {@link DataKind}, return the
     * list possible {@link EditType} options available based on
     * {@link ContactsSource}.
     *
     * @param forceInclude Always include this {@link EditType} in the returned
     *            list, even when an otherwise-invalid choice. This is useful
     *            when showing a dialog that includes the current type.
     * @param includeSecondary If true, include any valid types marked as
     *            {@link EditType#secondary}.
     * @param typeCount When provided, will be used for the frequency count of
     *            each {@link EditType}, otherwise built using
     *            {@link #getTypeFrequencies(EntityDelta, DataKind)}.
     */
    private static ArrayList<EditType> getValidTypes(EntityDelta state, DataKind kind,
            EditType forceInclude, boolean includeSecondary, SparseIntArray typeCount) {
        final ArrayList<EditType> validTypes = new ArrayList<EditType>();

        // Bail early if no types provided
        if (!hasEditTypes(kind)) return validTypes;

        if (typeCount == null) {
            // Build frequency counts if not provided
            typeCount = getTypeFrequencies(state, kind);
        }

        // Build list of valid types
        final int overallCount = typeCount.get(FREQUENCY_TOTAL);
        for (EditType type : kind.typeList) {
            final boolean validOverall = (kind.typeOverallMax == -1 ? true
                    : overallCount < kind.typeOverallMax);
            final boolean validSpecific = (type.specificMax == -1 ? true : typeCount
                    .get(type.rawValue) < type.specificMax);
            final boolean validSecondary = (includeSecondary ? true : !type.secondary);
            final boolean forcedInclude = type.equals(forceInclude);
            if (forcedInclude || (validOverall && validSpecific && validSecondary)) {
                // Type is valid when no limit, under limit, or forced include
                validTypes.add(type);
            }
        }

        return validTypes;
    }

    private static final int FREQUENCY_TOTAL = Integer.MIN_VALUE;

    /**
     * Count up the frequency that each {@link EditType} appears in the given
     * {@link EntityDelta}. The returned {@link SparseIntArray} maps from
     * {@link EditType#rawValue} to counts, with the total overall count stored
     * as {@link #FREQUENCY_TOTAL}.
     */
    private static SparseIntArray getTypeFrequencies(EntityDelta state, DataKind kind) {
        final SparseIntArray typeCount = new SparseIntArray();

        // Find all entries for this kind, bailing early if none found
        final List<ValuesDelta> mimeEntries = state.getMimeEntries(kind.mimeType);
        if (mimeEntries == null) return typeCount;

        int totalCount = 0;
        for (ValuesDelta entry : mimeEntries) {
            // Only count visible entries
            if (!entry.isVisible()) continue;
            totalCount++;

            final EditType type = getCurrentType(entry, kind);
            if (type != null) {
                final int count = typeCount.get(type.rawValue);
                typeCount.put(type.rawValue, count + 1);
            }
        }
        typeCount.put(FREQUENCY_TOTAL, totalCount);
        return typeCount;
    }

    /**
     * Check if the given {@link DataKind} has multiple types that should be
     * displayed for users to pick.
     */
    public static boolean hasEditTypes(DataKind kind) {
        return kind.typeList != null && kind.typeList.size() > 0;
    }

    /**
     * Find the {@link EditType} that describes the given
     * {@link ValuesDelta} row, assuming the given {@link DataKind} dictates
     * the possible types.
     */
    public static EditType getCurrentType(ValuesDelta entry, DataKind kind) {
        final Long rawValue = entry.getAsLong(kind.typeColumn);
        if (rawValue == null) return null;
        return getType(kind, rawValue.intValue());
    }

    /**
     * Find the {@link EditType} that describes the given {@link Cursor} row,
     * assuming the given {@link DataKind} dictates the possible types.
     */
    public static EditType getCurrentType(Cursor cursor, DataKind kind) {
        final int index = cursor.getColumnIndex(kind.typeColumn);
        final int rawValue = cursor.getInt(index);
        return getType(kind, rawValue);
    }

    /**
     * Find the {@link EditType} with the given {@link EditType#rawValue}.
     */
    public static EditType getType(DataKind kind, int rawValue) {
        for (EditType type : kind.typeList) {
            if (type.rawValue == rawValue) {
                return type;
            }
        }
        return null;
    }

    /**
     * Find the best {@link EditType} for a potential insert. The "best" is the
     * first primary type that doesn't already exist. When all valid types
     * exist, we pick the last valid option.
     */
    public static EditType getBestValidType(EntityDelta state, DataKind kind,
            boolean includeSecondary, int exactValue) {
        // Shortcut when no types
        if (kind.typeColumn == null) return null;

        // Find type counts and valid primary types, bail if none
        final SparseIntArray typeCount = getTypeFrequencies(state, kind);
        final ArrayList<EditType> validTypes = getValidTypes(state, kind, null, includeSecondary,
                typeCount);
        if (validTypes.size() == 0) return null;

        // Keep track of the last valid type
        final EditType lastType = validTypes.get(validTypes.size() - 1);

        // Remove any types that already exist
        Iterator<EditType> iterator = validTypes.iterator();
        while (iterator.hasNext()) {
            final EditType type = iterator.next();
            final int count = typeCount.get(type.rawValue);

            if (count == exactValue) {
                // Found exact value match
                return type;
            }

            if (count > 0) {
                // Type already appears, so don't consider
                iterator.remove();
            }
        }

        // Use the best remaining, otherwise the last valid
        if (validTypes.size() > 0) {
            return validTypes.get(0);
        } else {
            return lastType;
        }
    }

    /**
     * Insert a new child of kind {@link DataKind} into the given
     * {@link EntityDelta}. Tries using the best {@link EditType} found using
     * {@link #getBestValidType(EntityDelta, DataKind, boolean, int)}.
     */
    public static ValuesDelta insertChild(EntityDelta state, DataKind kind) {
        // First try finding a valid primary
        EditType bestType = getBestValidType(state, kind, false, Integer.MIN_VALUE);
        if (bestType == null) {
            // No valid primary found, so expand search to secondary
            bestType = getBestValidType(state, kind, true, Integer.MIN_VALUE);
        }
        return insertChild(state, kind, bestType);
    }

    /**
     * Insert a new child of kind {@link DataKind} into the given
     * {@link EntityDelta}, marked with the given {@link EditType}.
     */
    public static ValuesDelta insertChild(EntityDelta state, DataKind kind, EditType type) {
        // Bail early if invalid kind
        if (kind == null) return null;

        final ContentValues after = new ContentValues();

        // Our parent CONTACT_ID is provided later
        after.put(Data.MIMETYPE, kind.mimeType);

        // Fill-in with any requested default values
        if (kind.defaultValues != null) {
            after.putAll(kind.defaultValues);
        }

        if (kind.typeColumn != null && type != null) {
            // Set type, if provided
            after.put(kind.typeColumn, type.rawValue);
        }

        final ValuesDelta child = ValuesDelta.fromAfter(after);
        state.addEntry(child);
        return child;
    }

    /**
     * Parse the given {@link Bundle} into the given {@link EntityDelta} state,
     * assuming the extras defined through {@link Intents}.
     */
    public static void parseExtras(Context context, ContactsSource source, EntityDelta state, Bundle extras) {
        if (extras == null || extras.size() == 0) {
            // Bail early if no useful data
            return;
        }

        {
            // StructuredName
            final DataKind kind = source.getKindForMimetype(StructuredName.CONTENT_ITEM_TYPE);
            final String name = extras.getString(Insert.NAME);
            final String phoneticName = extras.getString(Insert.PHONETIC_NAME);
            if (kind != null && (TextUtils.isGraphic(name) || TextUtils.isGraphic(phoneticName))) {
                // TODO: handle the case where name already exists and limited to one
                // TODO: represent phonetic name as structured fields
                final ValuesDelta child = EntityModifier.insertChild(state, kind, null);
                child.put(StructuredName.DISPLAY_NAME, name);
                child.put(StructuredName.PHONETIC_GIVEN_NAME, phoneticName);
            }
        }

        {
            // StructuredPostal
            final DataKind kind = source.getKindForMimetype(StructuredPostal.CONTENT_ITEM_TYPE);
            parseExtras(state, kind, extras, Insert.POSTAL_TYPE, Insert.POSTAL,
                    StructuredPostal.FORMATTED_ADDRESS);
        }

        {
            // Phone
            final DataKind kind = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
            parseExtras(state, kind, extras, Insert.PHONE_TYPE, Insert.PHONE, Phone.NUMBER);
            parseExtras(state, kind, extras, Insert.SECONDARY_PHONE_TYPE, Insert.SECONDARY_PHONE,
                    Phone.NUMBER);
            parseExtras(state, kind, extras, Insert.TERTIARY_PHONE_TYPE, Insert.TERTIARY_PHONE,
                    Phone.NUMBER);
        }

        {
            // Email
            final DataKind kind = source.getKindForMimetype(Email.CONTENT_ITEM_TYPE);
            parseExtras(state, kind, extras, Insert.EMAIL_TYPE, Insert.EMAIL, Email.DATA);
            parseExtras(state, kind, extras, Insert.SECONDARY_EMAIL_TYPE, Insert.SECONDARY_EMAIL,
                    Email.DATA);
            parseExtras(state, kind, extras, Insert.TERTIARY_EMAIL_TYPE, Insert.TERTIARY_EMAIL,
                    Email.DATA);
        }

        {
            // Im
            // TODO: handle decodeImProtocol for legacy reasons
            final DataKind kind = source.getKindForMimetype(Im.CONTENT_ITEM_TYPE);
            parseExtras(state, kind, extras, Insert.IM_PROTOCOL, Insert.IM_HANDLE, Im.DATA);
        }
    }

    /**
     * Parse a specific entry from the given {@link Bundle} and insert into the
     * given {@link EntityDelta}. Silently skips the insert when missing value
     * or no valid {@link EditType} found.
     *
     * @param typeExtra {@link Bundle} key that holds the incoming
     *            {@link EditType#rawValue} value.
     * @param valueExtra {@link Bundle} key that holds the incoming value.
     * @param valueColumn Column to write value into {@link ValuesDelta}.
     */
    public static void parseExtras(EntityDelta state, DataKind kind, Bundle extras,
            String typeExtra, String valueExtra, String valueColumn) {
        final String value = extras.getString(valueExtra);

        // Bail when can't insert type, or value missing
        final boolean canInsert = EntityModifier.canInsert(state, kind);
        final boolean validValue = (value != null && TextUtils.isGraphic(value));
        if (!validValue || !canInsert) return;

        // Find exact type, or otherwise best type
        final int typeValue = extras.getInt(typeExtra, Integer.MIN_VALUE);
        final EditType editType = EntityModifier.getBestValidType(state, kind, true, typeValue);

        // Create data row and fill with value
        final ValuesDelta child = EntityModifier.insertChild(state, kind, editType);
        child.put(valueColumn, value);

        if (editType != null && editType.customColumn != null) {
            // Write down label when custom type picked
            child.put(editType.customColumn, extras.getString(typeExtra));
        }
    }
}
