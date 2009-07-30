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

import com.android.contacts.model.AugmentedEntity.AugmentedValues;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditType;

import android.content.ContentValues;
import android.provider.ContactsContract.Data;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper methods for modifying an {@link AugmentedEntity}, such as inserting
 * new rows, or enforcing {@link ContactsSource}.
 */
public class EntityModifier {
    /**
     * For the given {@link AugmentedEntity}, determine if the given
     * {@link DataKind} could be inserted under specific
     * {@link ContactsSource}.
     */
    public static boolean canInsert(AugmentedEntity state, DataKind kind) {
        // Insert possible when have valid types and under overall maximum
        final boolean validTypes = hasValidTypes(state, kind);
        final boolean validOverall = (kind.typeOverallMax == -1)
                || (state.getEntryCount(true) < kind.typeOverallMax);
        return (validTypes && validOverall);
    }

    public static boolean hasValidTypes(AugmentedEntity state, DataKind kind) {
        return (getValidTypes(state, kind).size() > 0);
    }

    /**
     * For the given {@link AugmentedEntity} and {@link DataKind}, return the
     * list possible {@link EditType} options available based on
     * {@link ContactsSource}.
     */
    public static ArrayList<EditType> getValidTypes(AugmentedEntity state, DataKind kind) {
        return getValidTypes(state, kind, null, true, null);
    }

    /**
     * For the given {@link AugmentedEntity} and {@link DataKind}, return the
     * list possible {@link EditType} options available based on
     * {@link ContactsSource}.
     *
     * @param forceInclude Always include this {@link EditType} in the returned
     *            list, even when an otherwise-invalid choice. This is useful
     *            when showing a dialog that includes the current type.
     */
    public static ArrayList<EditType> getValidTypes(AugmentedEntity state, DataKind kind,
            EditType forceInclude) {
        return getValidTypes(state, kind, forceInclude, true, null);
    }

    /**
     * For the given {@link AugmentedEntity} and {@link DataKind}, return the
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
     *            {@link #getTypeFrequencies(AugmentedEntity, DataKind)}.
     */
    private static ArrayList<EditType> getValidTypes(AugmentedEntity state, DataKind kind,
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
            final boolean validUnlimited = (type.specificMax == -1 && overallCount < kind.typeOverallMax);
            final boolean validSpecific = (typeCount.get(type.rawValue) < type.specificMax);
            final boolean validSecondary = (includeSecondary ? true : !type.secondary);
            final boolean forcedInclude = type.equals(forceInclude);
            if (forcedInclude || (validSecondary && (validUnlimited || validSpecific))) {
                // Type is valid when no limit, under limit, or forced include
                validTypes.add(type);
            }
        }

        return validTypes;
    }

    private static final int FREQUENCY_TOTAL = Integer.MIN_VALUE;

    /**
     * Count up the frequency that each {@link EditType} appears in the given
     * {@link AugmentedEntity}. The returned {@link SparseIntArray} maps from
     * {@link EditType#rawValue} to counts, with the total overall count stored
     * as {@link #FREQUENCY_TOTAL}.
     */
    private static SparseIntArray getTypeFrequencies(AugmentedEntity state, DataKind kind) {
        final SparseIntArray typeCount = new SparseIntArray();

        // Find all entries for this kind, bailing early if none found
        final List<AugmentedValues> mimeEntries = state.getMimeEntries(kind.mimeType);
        if (mimeEntries == null) return typeCount;

        int totalCount = 0;
        for (AugmentedValues entry : mimeEntries) {
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
     * {@link AugmentedValues} row, assuming the given {@link DataKind} dictates
     * the possible types.
     */
    public static EditType getCurrentType(AugmentedValues entry, DataKind kind) {
        final Long rawValue = entry.getAsLong(kind.typeColumn);
        if (rawValue == null) return null;
        return getType(kind, rawValue.intValue());
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
    public static EditType getBestValidType(AugmentedEntity state, DataKind kind,
            boolean includeSecondary) {
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
     * {@link AugmentedEntity}. Tries using the best {@link EditType} found
     * using {@link #getBestValidType(AugmentedEntity, DataKind, boolean)}.
     */
    public static void insertChild(AugmentedEntity state, DataKind kind) {
        // First try finding a valid primary
        EditType bestType = getBestValidType(state, kind, false);
        if (bestType == null) {
            // No valid primary found, so expand search to secondary
            bestType = getBestValidType(state, kind, true);
        }
        insertChild(state, kind, bestType);
    }

    /**
     * Insert a new child of kind {@link DataKind} into the given
     * {@link AugmentedEntity}, marked with the given {@link EditType}.
     */
    public static void insertChild(AugmentedEntity state, DataKind kind, EditType type) {
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

        state.addEntry(AugmentedValues.fromAfter(after));
    }

}
