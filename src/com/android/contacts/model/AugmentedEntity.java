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

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Entity;
import android.content.ContentProviderOperation.Builder;
import android.content.Entity.NamedContentValues;
import android.net.Uri;
import android.os.Parcel;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Contains an {@link Entity} that records any modifications separately so the
 * original {@link Entity} can be swapped out with a newer version and the
 * changes still cleanly applied.
 * <p>
 * One benefit of this approach is that we can build changes entirely on an
 * empty {@link Entity}, which then becomes an insert {@link RawContacts} case.
 * <p>
 * When applying modifications over an {@link Entity}, we try finding the
 * original {@link Data#_ID} rows where the modifications took place. If those
 * rows are missing from the new {@link Entity}, we know the original data must
 * be deleted, but to preserve the user modifications we treat as an insert.
 */
public class AugmentedEntity {
    // TODO: optimize by using contentvalues pool, since we allocate so many of them

    /**
     * Direct values from {@link Entity#getEntityValues()}.
     */
    private AugmentedValues mValues;

    /**
     * Internal map of children values from {@link Entity#getSubValues()}, which
     * we store here sorted into {@link Data#MIMETYPE} bins.
     */
    private HashMap<String, ArrayList<AugmentedValues>> mEntries;

    private AugmentedEntity() {
        mEntries = new HashMap<String, ArrayList<AugmentedValues>>();
    }

    public AugmentedEntity(AugmentedValues values) {
        this();
        mValues = values;
    }

    /**
     * Build an {@link AugmentedEntity} using the given {@link Entity} as a
     * starting point; the "before" snapshot.
     */
    public static AugmentedEntity fromBefore(Entity before) {
        final AugmentedEntity entity = new AugmentedEntity();
        entity.mValues = AugmentedValues.fromBefore(before.getEntityValues());
        entity.mValues.setIdColumn(RawContacts._ID);
        for (NamedContentValues namedValues : before.getSubValues()) {
            entity.addEntry(AugmentedValues.fromBefore(namedValues.values));
        }
        return entity;
    }

    public AugmentedValues getValues() {
        return mValues;
    }

    /**
     * Get the {@link AugmentedValues} child marked as {@link Data#IS_PRIMARY}.
     */
    public AugmentedValues getPrimaryEntry(String mimeType) {
        // TODO: handle the case where the caller must have a non-null value,
        // for example inserting a displayname automatically
        final ArrayList<AugmentedValues> mimeEntries = getMimeEntries(mimeType, false);
        for (AugmentedValues entry : mimeEntries) {
            if (entry.isPrimary()) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Return the list of child {@link AugmentedValues} from our optimized map,
     * creating the list if requested.
     */
    private ArrayList<AugmentedValues> getMimeEntries(String mimeType, boolean lazyCreate) {
        ArrayList<AugmentedValues> mimeEntries = mEntries.get(mimeType);
        if (mimeEntries == null && lazyCreate) {
            mimeEntries = new ArrayList<AugmentedValues>();
            mEntries.put(mimeType, mimeEntries);
        }
        return mimeEntries;
    }

    public ArrayList<AugmentedValues> getMimeEntries(String mimeType) {
        return getMimeEntries(mimeType, false);
    }

    public boolean hasMimeEntries(String mimeType) {
        return mEntries.containsKey(mimeType);
    }

    public void addEntry(AugmentedValues entry) {
        final String mimeType = entry.getMimetype();
        getMimeEntries(mimeType, true).add(entry);
    }

    /**
     * Find the {@link AugmentedValues} that has a specific
     * {@link BaseColumns#_ID} value, used when {@link #augmentFrom(Parcel)} is
     * inflating a modified state.
     */
    public AugmentedValues getEntry(Long childId) {
        if (childId == null) {
            // Requesting an "insert" entry, which has no "before"
            return null;
        }

        // Search all children for requested entry
        for (ArrayList<AugmentedValues> mimeEntries : mEntries.values()) {
            for (AugmentedValues entry : mimeEntries) {
                if (entry.getId() == childId) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static final int MODE_CONTINUE = 1;
    private static final int MODE_DONE = 2;

    /**
     * Read a set of modifying actions from the given {@link Parcel}, which
     * expects the format written by {@link #augmentTo(Parcel)}. This expects
     * that we already have a base {@link Entity} that we are applying over.
     */
    public void augmentFrom(Parcel parcel) {
        {
            final ContentValues after = (ContentValues)parcel.readValue(null);
            if (mValues == null) {
                // Entity didn't exist before, so "insert"
                mValues = AugmentedValues.fromAfter(after);
            } else {
                // Existing entity "update"
                mValues.mAfter = after;
            }
        }

        // Read in packaged children until finished
        int mode = parcel.readInt();
        while (mode == MODE_CONTINUE) {
            final Long childId = readLong(parcel);
            final ContentValues after = (ContentValues)parcel.readValue(null);

            AugmentedValues entry = getEntry(childId);
            if (entry == null) {
                // Is "insert", or "before" record is missing, so now "insert"
                entry = AugmentedValues.fromAfter(after);
                addEntry(entry);
            } else {
                // Existing entry "update"
                entry.mAfter = after;
            }

            mode = parcel.readInt();
        }
    }

    /**
     * Store all modifying actions into the given {@link Parcel}.
     */
    public void augmentTo(Parcel parcel) {
        parcel.writeValue(mValues.mAfter);

        for (ArrayList<AugmentedValues> mimeEntries : mEntries.values()) {
            for (AugmentedValues child : mimeEntries) {
                parcel.writeInt(MODE_CONTINUE);
                writeLong(parcel, child.getId());
                parcel.writeValue(child.mAfter);
            }
        }
        parcel.writeInt(MODE_DONE);
    }

    private void writeLong(Parcel parcel, Long value) {
        parcel.writeLong(value == null ? -1 : value);
    }

    private Long readLong(Parcel parcel) {
        final long value = parcel.readLong();
        return value == -1 ? null : value;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof AugmentedEntity) {
            final AugmentedEntity other = (AugmentedEntity)object;

            // Equality failed if parent values different
            if (!other.mValues.equals(mValues)) return false;

            for (ArrayList<AugmentedValues> mimeEntries : mEntries.values()) {
                for (AugmentedValues child : mimeEntries) {
                    // Equality failed if any children unmatched
                    if (!other.containsEntry(child)) return false;
                }
            }

            // Passed all tests, so equal
            return true;
        }
        return false;
    }

    private boolean containsEntry(AugmentedValues entry) {
        for (ArrayList<AugmentedValues> mimeEntries : mEntries.values()) {
            for (AugmentedValues child : mimeEntries) {
                // Contained if we find any child that matches
                if (child.equals(entry)) return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("\n(");
        builder.append(mValues.toString());
        builder.append(") = {");
        for (ArrayList<AugmentedValues> mimeEntries : mEntries.values()) {
            for (AugmentedValues child : mimeEntries) {
                builder.append("\n\t");
                child.toString(builder);
            }
        }
        builder.append("\n}\n");
        return builder.toString();
    }

    private void possibleAdd(ArrayList<ContentProviderOperation> diff, ContentProviderOperation.Builder builder) {
        if (builder != null) {
            diff.add(builder.build());
        }
    }

    /**
     * Build a list of {@link ContentProviderOperation} that will transform the
     * current "before" {@link Entity} state into the modified state which this
     * {@link AugmentedEntity} represents.
     */
    public ArrayList<ContentProviderOperation> buildDiff() {
        final ArrayList<ContentProviderOperation> diff = new ArrayList<ContentProviderOperation>();

        final boolean isContactInsert = mValues.isInsert();
        final boolean isContactDelete = mValues.isDelete();

        final Long beforeId = mValues.getId();
        final Long beforeVersion = mValues.getAsLong(RawContacts.VERSION);

        // Build possible operation at Contact level
        Builder builder = mValues.buildDiff(RawContacts.CONTENT_URI);
        possibleAdd(diff, builder);

        // Build operations for all children
        for (ArrayList<AugmentedValues> mimeEntries : mEntries.values()) {
            for (AugmentedValues child : mimeEntries) {
                // Ignore children if parent was deleted
                if (isContactDelete) continue;

                builder = child.buildDiff(Data.CONTENT_URI);
                if (child.isInsert()) {
                    if (isContactInsert) {
                        // Parent is brand new insert, so back-reference _id
                        builder.withValueBackReference(Data.RAW_CONTACT_ID, 0);
                    } else {
                        // Inserting under existing, so fill with known _id
                        builder.withValue(Data.RAW_CONTACT_ID, beforeId);
                    }
                } else if (isContactInsert) {
                    // Child must be insert when Contact insert
                    throw new IllegalArgumentException("When parent insert, child must be also");
                }
                possibleAdd(diff, builder);
            }
        }

        // If any operations, assert that version is identical so we bail if changed
        if (diff.size() > 0 && beforeVersion != null && beforeId != null) {
            builder = ContentProviderOperation.newCountQuery(RawContacts.CONTENT_URI);
            builder.withSelection(RawContacts._ID + "=" + beforeId + " AND " + RawContacts.VERSION
                    + "=" + beforeVersion, null);
            builder.withExpectedCount(1);
            // Sneak version check at beginning of list
            diff.add(0, builder.build());
        }

        return diff;
    }

    /**
     * Type of {@link ContentValues} that maintains both an original state and a
     * modified version of that state. This allows us to build insert, update,
     * or delete operations based on a "before" {@link Entity} snapshot.
     */
    public static class AugmentedValues {
        private ContentValues mBefore;
        private ContentValues mAfter;
        private String mIdColumn = BaseColumns._ID;

        private AugmentedValues() {
        }

        /**
         * Create {@link AugmentedValues}, using the given object as the
         * "before" state, usually from an {@link Entity}.
         */
        public static AugmentedValues fromBefore(ContentValues before) {
            final AugmentedValues entry = new AugmentedValues();
            entry.mBefore = before;
            entry.mAfter = new ContentValues();
            return entry;
        }

        /**
         * Create {@link AugmentedValues}, using the given object as the "after"
         * state, usually when we are inserting a row instead of updating.
         */
        public static AugmentedValues fromAfter(ContentValues after) {
            final AugmentedValues entry = new AugmentedValues();
            entry.mBefore = null;
            entry.mAfter = after;
            return entry;
        }

        public String getAsString(String key) {
            if (mAfter != null && mAfter.containsKey(key)) {
                return mAfter.getAsString(key);
            } else if (mBefore != null && mBefore.containsKey(key)) {
                return mBefore.getAsString(key);
            } else {
                return null;
            }
        }

        public Long getAsLong(String key) {
            if (mAfter != null && mAfter.containsKey(key)) {
                return mAfter.getAsLong(key);
            } else if (mBefore != null && mBefore.containsKey(key)) {
                return mBefore.getAsLong(key);
            } else {
                return null;
            }
        }

        public String getMimetype() {
            return getAsString(Data.MIMETYPE);
        }

        public Long getId() {
            return getAsLong(mIdColumn);
        }

        public void setIdColumn(String idColumn) {
            mIdColumn = idColumn;
        }

        public boolean isPrimary() {
            return (getAsLong(Data.IS_PRIMARY) != 0);
        }

        public boolean isDelete() {
            // When "after" is wiped, action is "delete"
            return (mAfter == null);
        }

        public boolean isUpdate() {
            // When "after" has some changes, action is "update"
            return (mAfter.size() > 0);
        }

        public boolean isInsert() {
            // When no "before" id, action is "insert"
            return mBefore == null || getId() == null;
        }

        public void markDeleted() {
            mAfter = null;
        }

        /**
         * Ensure that our internal structure is ready for storing updates.
         */
        private void ensureUpdate() {
            if (mAfter == null) {
                mAfter = new ContentValues();
            }
        }

        public void put(String key, String value) {
            ensureUpdate();
            mAfter.put(key, value);
        }

        public void put(String key, int value) {
            ensureUpdate();
            mAfter.put(key, value);
        }

        /**
         * Return set of all keys defined through this object.
         */
        public Set<String> keySet() {
            final HashSet<String> keys = new HashSet<String>();

            if (mBefore != null) {
                for (Map.Entry<String, Object> entry : mBefore.valueSet()) {
                    keys.add(entry.getKey());
                }
            }

            if (mAfter != null) {
                for (Map.Entry<String, Object> entry : mAfter.valueSet()) {
                    keys.add(entry.getKey());
                }
            }

            return keys;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof AugmentedValues) {
                // Only exactly equal with both are identical subsets
                final AugmentedValues other = (AugmentedValues)object;
                return this.subsetEquals(other) && other.subsetEquals(this);
            }
            return false;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            toString(builder);
            return builder.toString();
        }

        /**
         * Helper for building string representation, leveraging the given
         * {@link StringBuilder} to minimize allocations.
         */
        public void toString(StringBuilder builder) {
            builder.append("{ ");
            for (String key : this.keySet()) {
                builder.append(key);
                builder.append("=");
                builder.append(this.getAsString(key));
                builder.append(", ");
            }
            builder.append("}");
        }

        /**
         * Check if the given {@link AugmentedValues} is both a subset of this
         * object, and any defined keys have equal values.
         */
        public boolean subsetEquals(AugmentedValues other) {
            for (String key : this.keySet()) {
                final String ourValue = this.getAsString(key);
                final String theirValue = other.getAsString(key);
                if (ourValue == null) {
                    // If they have value when we're null, no match
                    if (theirValue != null) return false;
                } else {
                    // If both values defined and aren't equal, no match
                    if (!ourValue.equals(theirValue)) return false;
                }
            }
            // All values compared and matched
            return true;
        }

        /**
         * Build a {@link ContentProviderOperation} that will transform our
         * "before" state into our "after" state, using insert, update, or
         * delete as needed.
         */
        public ContentProviderOperation.Builder buildDiff(Uri targetUri) {
            Builder builder = null;
            if (isInsert()) {
                // Changed values are "insert" back-referenced to Contact
                builder = ContentProviderOperation.newInsert(targetUri);
                builder.withValues(mAfter);
            } else if (isDelete()) {
                // When marked for deletion and "before" exists, then "delete"
                builder = ContentProviderOperation.newDelete(targetUri);
                builder.withSelection(mIdColumn + "=" + getId(), null);
            } else if (isUpdate()) {
                // When has changes and "before" exists, then "update"
                builder = ContentProviderOperation.newUpdate(targetUri);
                builder.withSelection(mIdColumn + "=" + getId(), null);
                builder.withValues(mAfter);
            }
            return builder;
        }
    }
}
