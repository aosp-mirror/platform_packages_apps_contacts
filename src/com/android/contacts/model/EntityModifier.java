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

import java.util.List;

/**
 * Helper methods for modifying an {@link AugmentedEntity}, such as inserting
 * new rows, or enforcing {@link ContactsSource}.
 */
public class EntityModifier {
    // TODO: provide helper to force an augmentedentity into sourceconstraints?

    /**
     * For the given {@link AugmentedEntity}, determine if the given
     * {@link DataKind} could be inserted under specific
     * {@link ContactsSource}.
     */
    public static boolean canInsert(AugmentedEntity contact, DataKind kind) {
        // TODO: compare against constraints to determine if insert is possible
        return true;
    }

    /**
     * For the given {@link AugmentedEntity} and {@link DataKind}, return the
     * list possible {@link EditType} options available based on
     * {@link ContactsSource}.
     */
    public static List<EditType> getValidTypes(AugmentedEntity entity, DataKind kind,
            EditType forceInclude) {
        // TODO: enforce constraints and include any extra provided
        return kind.typeList;
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
        final long rawValue = entry.getAsLong(kind.typeColumn);
        for (EditType type : kind.typeList) {
            if (type.rawValue == rawValue) {
                return type;
            }
        }
        return null;
    }

    /**
     * Insert a new child of kind {@link DataKind} into the given
     * {@link AugmentedEntity}. Assumes the caller has already checked
     * {@link #canInsert(AugmentedEntity, DataKind)}.
     */
    public static void insertChild(AugmentedEntity state, DataKind kind) {
        final ContentValues after = new ContentValues();

        // Our parent CONTACT_ID is provided later
        after.put(Data.MIMETYPE, kind.mimeType);

        // Fill-in with any requested default values
        if (kind.defaultValues != null) {
            after.putAll(kind.defaultValues);
        }

        if (kind.typeColumn != null) {
            // TODO: add the best-kind of entry based on current state machine
            final EditType firstType = kind.typeList.get(0);
            after.put(kind.typeColumn, firstType.rawValue);
        }

        state.addEntry(AugmentedValues.fromAfter(after));
    }

}
