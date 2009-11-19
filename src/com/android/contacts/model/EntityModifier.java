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

import com.android.contacts.ContactsUtils;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditField;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.google.android.collect.Lists;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.BaseTypes;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
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
    private static final String TAG = "EntityModifier";

    /**
     * For the given {@link EntityDelta}, determine if the given
     * {@link DataKind} could be inserted under specific
     * {@link ContactsSource}.
     */
    public static boolean canInsert(EntityDelta state, DataKind kind) {
        // Insert possible when have valid types and under overall maximum
        final int visibleCount = state.getMimeEntriesCount(kind.mimeType, true);
        final boolean validTypes = hasValidTypes(state, kind);
        final boolean validOverall = (kind.typeOverallMax == -1)
                || (visibleCount < kind.typeOverallMax);
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
     * Ensure that at least one of the given {@link DataKind} exists in the
     * given {@link EntityDelta} state, and try creating one if none exist.
     */
    public static void ensureKindExists(EntityDelta state, ContactsSource source, String mimeType) {
        final DataKind kind = source.getKindForMimetype(mimeType);
        final boolean hasChild = state.getMimeEntriesCount(mimeType, true) > 0;

        if (!hasChild && kind != null) {
            // Create child when none exists and valid kind
            final ValuesDelta child = insertChild(state, kind);
            if (kind.mimeType.equals(Photo.CONTENT_ITEM_TYPE)) {
                child.setFromTemplate(true);
            }
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
        final ArrayList<EditType> validTypes = Lists.newArrayList();

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
     * Find the {@link EditType} that describes the given {@link ContentValues} row,
     * assuming the given {@link DataKind} dictates the possible types.
     */
    public static EditType getCurrentType(ContentValues entry, DataKind kind) {
        if (kind.typeColumn == null) return null;
        final Integer rawValue = entry.getAsInteger(kind.typeColumn);
        if (rawValue == null) return null;
        return getType(kind, rawValue);
    }

    /**
     * Find the {@link EditType} that describes the given {@link Cursor} row,
     * assuming the given {@link DataKind} dictates the possible types.
     */
    public static EditType getCurrentType(Cursor cursor, DataKind kind) {
        if (kind.typeColumn == null) return null;
        final int index = cursor.getColumnIndex(kind.typeColumn);
        if (index == -1) return null;
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
     * Return the precedence for the the given {@link EditType#rawValue}, where
     * lower numbers are higher precedence.
     */
    public static int getTypePrecedence(DataKind kind, int rawValue) {
        for (int i = 0; i < kind.typeList.size(); i++) {
            final EditType type = kind.typeList.get(i);
            if (type.rawValue == rawValue) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
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

            if (exactValue == type.rawValue) {
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
     * Processing to trim any empty {@link ValuesDelta} and {@link EntityDelta}
     * from the given {@link EntitySet}, assuming the given {@link Sources}
     * dictates the structure for various fields. This method ignores rows not
     * described by the {@link ContactsSource}.
     */
    public static void trimEmpty(EntitySet set, Sources sources) {
        for (EntityDelta state : set) {
            final String accountType = state.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            final ContactsSource source = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_MIMETYPES);
            trimEmpty(state, source);
        }
    }

    /**
     * Processing to trim any empty {@link ValuesDelta} rows from the given
     * {@link EntityDelta}, assuming the given {@link ContactsSource} dictates
     * the structure for various fields. This method ignores rows not described
     * by the {@link ContactsSource}.
     */
    public static void trimEmpty(EntityDelta state, ContactsSource source) {
        boolean hasValues = false;

        // Walk through entries for each well-known kind
        for (DataKind kind : source.getSortedDataKinds()) {
            final String mimeType = kind.mimeType;
            final ArrayList<ValuesDelta> entries = state.getMimeEntries(mimeType);
            if (entries == null) continue;

            for (ValuesDelta entry : entries) {
                // Skip any values that haven't been touched
                final boolean touched = entry.isInsert() || entry.isUpdate();
                if (!touched) {
                    hasValues = true;
                    continue;
                }

                // Test and remove this row if empty and it isn't a photo from google
                final boolean isGoogleSource = TextUtils.equals(GoogleSource.ACCOUNT_TYPE,
                        state.getValues().getAsString(RawContacts.ACCOUNT_TYPE));
                final boolean isPhoto = TextUtils.equals(Photo.CONTENT_ITEM_TYPE, kind.mimeType);
                final boolean isGooglePhoto = isPhoto && isGoogleSource;

                if (EntityModifier.isEmpty(entry, kind) && !isGooglePhoto) {
                    // TODO: remove this verbose logging
                    Log.w(TAG, "Trimming: " + entry.toString());
                    entry.markDeleted();
                } else if (!entry.isFromTemplate()) {
                    hasValues = true;
                }
            }
        }
        if (!hasValues) {
            // Trim overall entity if no children exist
            state.markDeleted();
        }
    }

    /**
     * Test if the given {@link ValuesDelta} would be considered "empty" in
     * terms of {@link DataKind#fieldList}.
     */
    public static boolean isEmpty(ValuesDelta values, DataKind kind) {
        // No defined fields mean this row is always empty
        if (kind.fieldList == null) return true;

        boolean hasValues = false;
        for (EditField field : kind.fieldList) {
            // If any field has values, we're not empty
            final String value = values.getAsString(field.column);
            if (ContactsUtils.isGraphic(value)) {
                hasValues = true;
            }
        }

        return !hasValues;
    }

    /**
     * Parse the given {@link Bundle} into the given {@link EntityDelta} state,
     * assuming the extras defined through {@link Intents}.
     */
    public static void parseExtras(Context context, ContactsSource source, EntityDelta state,
            Bundle extras) {
        if (extras == null || extras.size() == 0) {
            // Bail early if no useful data
            return;
        }

        {
            // StructuredName
            EntityModifier.ensureKindExists(state, source, StructuredName.CONTENT_ITEM_TYPE);
            final ValuesDelta child = state.getPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);

            final String name = extras.getString(Insert.NAME);
            if (ContactsUtils.isGraphic(name)) {
                child.put(StructuredName.GIVEN_NAME, name);
            }

            final String phoneticName = extras.getString(Insert.PHONETIC_NAME);
            if (ContactsUtils.isGraphic(phoneticName)) {
                child.put(StructuredName.PHONETIC_GIVEN_NAME, phoneticName);
            }
        }

        {
            // StructuredPostal
            final DataKind kind = source.getKindForMimetype(StructuredPostal.CONTENT_ITEM_TYPE);
            parseExtras(state, kind, extras, Insert.POSTAL_TYPE, Insert.POSTAL,
                    StructuredPostal.STREET);
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
            final DataKind kind = source.getKindForMimetype(Im.CONTENT_ITEM_TYPE);
            fixupLegacyImType(extras);
            parseExtras(state, kind, extras, Insert.IM_PROTOCOL, Insert.IM_HANDLE, Im.DATA);
        }

        // Organization
        final boolean hasOrg = extras.containsKey(Insert.COMPANY)
                || extras.containsKey(Insert.JOB_TITLE);
        final DataKind kindOrg = source.getKindForMimetype(Organization.CONTENT_ITEM_TYPE);
        if (hasOrg && EntityModifier.canInsert(state, kindOrg)) {
            final ValuesDelta child = EntityModifier.insertChild(state, kindOrg);

            final String company = extras.getString(Insert.COMPANY);
            if (ContactsUtils.isGraphic(company)) {
                child.put(Organization.COMPANY, company);
            }

            final String title = extras.getString(Insert.JOB_TITLE);
            if (ContactsUtils.isGraphic(title)) {
                child.put(Organization.TITLE, title);
            }
        }

        // Notes
        final boolean hasNotes = extras.containsKey(Insert.NOTES);
        final DataKind kindNotes = source.getKindForMimetype(Note.CONTENT_ITEM_TYPE);
        if (hasNotes && EntityModifier.canInsert(state, kindNotes)) {
            final ValuesDelta child = EntityModifier.insertChild(state, kindNotes);

            final String notes = extras.getString(Insert.NOTES);
            if (ContactsUtils.isGraphic(notes)) {
                child.put(Note.NOTE, notes);
            }
        }
    }

    /**
     * Attempt to parse legacy {@link Insert#IM_PROTOCOL} values, replacing them
     * with updated values.
     */
    private static void fixupLegacyImType(Bundle bundle) {
        final String encodedString = bundle.getString(Insert.IM_PROTOCOL);
        if (encodedString == null) return;

        try {
            final Object protocol = android.provider.Contacts.ContactMethods
                    .decodeImProtocol(encodedString);
            if (protocol instanceof Integer) {
                bundle.putInt(Insert.IM_PROTOCOL, (Integer)protocol);
            } else {
                bundle.putString(Insert.IM_PROTOCOL, (String)protocol);
            }
        } catch (IllegalArgumentException e) {
            // Ignore exception when legacy parser fails
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
        final CharSequence value = extras.getCharSequence(valueExtra);

        // Bail early if source doesn't handle this type
        if (kind == null) return;

        // Bail when can't insert type, or value missing
        final boolean canInsert = EntityModifier.canInsert(state, kind);
        final boolean validValue = (value != null && TextUtils.isGraphic(value));
        if (!validValue || !canInsert) return;

        // Find exact type when requested, otherwise best available type
        final boolean hasType = extras.containsKey(typeExtra);
        final int typeValue = extras.getInt(typeExtra, hasType ? BaseTypes.TYPE_CUSTOM
                : Integer.MIN_VALUE);
        final EditType editType = EntityModifier.getBestValidType(state, kind, true, typeValue);

        // Create data row and fill with value
        final ValuesDelta child = EntityModifier.insertChild(state, kind, editType);
        child.put(valueColumn, value.toString());

        if (editType != null && editType.customColumn != null) {
            // Write down label when custom type picked
            final String customType = extras.getString(typeExtra);
            child.put(editType.customColumn, customType);
        }
    }
}
