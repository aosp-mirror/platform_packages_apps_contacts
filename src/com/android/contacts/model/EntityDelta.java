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
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Contains an {@link Entity} and records any modifications separately so the
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
public class EntityDelta implements Parcelable {
    // TODO: optimize by using contentvalues pool, since we allocate so many of them

    /**
     * Direct values from {@link Entity#getEntityValues()}.
     */
    private ValuesDelta mValues;

    /**
     * Internal map of children values from {@link Entity#getSubValues()}, which
     * we store here sorted into {@link Data#MIMETYPE} bins.
     */
    private HashMap<String, ArrayList<ValuesDelta>> mEntries;

    private EntityDelta() {
        mEntries = new HashMap<String, ArrayList<ValuesDelta>>();
    }

    public EntityDelta(ValuesDelta values) {
        this();
        mValues = values;
    }

    /**
     * Build an {@link EntityDelta} using the given {@link Entity} as a
     * starting point; the "before" snapshot.
     */
    public static EntityDelta fromBefore(Entity before) {
        final EntityDelta entity = new EntityDelta();
        entity.mValues = ValuesDelta.fromBefore(before.getEntityValues());
        entity.mValues.setIdColumn(RawContacts._ID);
        for (NamedContentValues namedValues : before.getSubValues()) {
            entity.addEntry(ValuesDelta.fromBefore(namedValues.values));
        }
        return entity;
    }

    /**
     * Merge the "after" values from the given {@link EntityDelta} onto the
     * "before" state represented by this {@link EntityDelta}, discarding any
     * existing "after" states. This is typically used when re-parenting changes
     * onto an updated {@link Entity}.
     */
    public void mergeAfter(EntityDelta remote) {
        // Always take after values from new state
        this.mValues.mAfter = remote.mValues.mAfter;

        // Find matching local entry for each remote values, or create
        for (ArrayList<ValuesDelta> mimeEntries : remote.mEntries.values()) {
            for (ValuesDelta remoteEntry : mimeEntries) {
                final Long childId = remoteEntry.getId();

                ValuesDelta localEntry = this.getEntry(childId);
                if (localEntry == null) {
                    // Is "insert", or "before" record is missing, so now "insert"
                    localEntry = ValuesDelta.fromAfter(remoteEntry.mAfter);
                    this.addEntry(localEntry);
                } else {
                    // Existing entry "update"
                    localEntry.mAfter = remoteEntry.mAfter;
                }
            }
        }
    }

    public ValuesDelta getValues() {
        return mValues;
    }

    /**
     * Get the {@link ValuesDelta} child marked as {@link Data#IS_PRIMARY}.
     */
    public ValuesDelta getPrimaryEntry(String mimeType) {
        final ArrayList<ValuesDelta> mimeEntries = getMimeEntries(mimeType, false);

        // TODO: handle the case where the caller must have a non-null value,
        // for example inserting a displayname automatically
        if (mimeEntries == null) return null;

        for (ValuesDelta entry : mimeEntries) {
            if (entry.isPrimary()) {
                return entry;
            }
        }

        // When no direct primary, return something
        return mimeEntries.size() > 0 ? mimeEntries.get(0) : null;
    }

    /**
     * Return the list of child {@link ValuesDelta} from our optimized map,
     * creating the list if requested.
     */
    private ArrayList<ValuesDelta> getMimeEntries(String mimeType, boolean lazyCreate) {
        ArrayList<ValuesDelta> mimeEntries = mEntries.get(mimeType);
        if (mimeEntries == null && lazyCreate) {
            mimeEntries = new ArrayList<ValuesDelta>();
            mEntries.put(mimeType, mimeEntries);
        }
        return mimeEntries;
    }

    public ArrayList<ValuesDelta> getMimeEntries(String mimeType) {
        return getMimeEntries(mimeType, false);
    }

    public boolean hasMimeEntries(String mimeType) {
        return mEntries.containsKey(mimeType);
    }

    public void addEntry(ValuesDelta entry) {
        final String mimeType = entry.getMimetype();
        getMimeEntries(mimeType, true).add(entry);
    }

    /**
     * Find entry with the given {@link BaseColumns#_ID} value.
     */
    public ValuesDelta getEntry(Long childId) {
        if (childId == null) {
            // Requesting an "insert" entry, which has no "before"
            return null;
        }

        // Search all children for requested entry
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta entry : mimeEntries) {
                if (entry.getId() == childId) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * Return the total number of {@link ValuesDelta} contained.
     */
    public int getEntryCount(boolean onlyVisible) {
        int count = 0;
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                // Skip deleted items when requesting only visible
                if (onlyVisible && child.isVisible()) continue;
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof EntityDelta) {
            final EntityDelta other = (EntityDelta)object;

            // Equality failed if parent values different
            if (!other.mValues.equals(mValues)) return false;

            for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
                for (ValuesDelta child : mimeEntries) {
                    // Equality failed if any children unmatched
                    if (!other.containsEntry(child)) return false;
                }
            }

            // Passed all tests, so equal
            return true;
        }
        return false;
    }

    private boolean containsEntry(ValuesDelta entry) {
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
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
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                builder.append("\n\t");
                child.toString(builder);
            }
        }
        builder.append("\n}\n");
        return builder.toString();
    }

    /**
     * Consider building the given {@link ContentProviderOperation.Builder} and
     * appending it to the given list, which only happens if builder is valid.
     */
    private void possibleAdd(ArrayList<ContentProviderOperation> diff,
            ContentProviderOperation.Builder builder) {
        if (builder != null) {
            diff.add(builder.build());
        }
    }

    /**
     * Build a list of {@link ContentProviderOperation} that will transform the
     * current "before" {@link Entity} state into the modified state which this
     * {@link EntityDelta} represents.
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
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
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
            builder = ContentProviderOperation.newAssertQuery(RawContacts.CONTENT_URI);
            builder.withSelection(RawContacts._ID + "=" + beforeId, null);
            builder.withValue(RawContacts.VERSION, beforeVersion);
            // Sneak version check at beginning of list
            diff.add(0, builder.build());
        }

        return diff;
    }

    /** {@inheritDoc} */
    public int describeContents() {
        // Nothing special about this parcel
        return 0;
    }

    /** {@inheritDoc} */
    public void writeToParcel(Parcel dest, int flags) {
        final int size = this.getEntryCount(false);
        dest.writeInt(size);
        dest.writeParcelable(mValues, flags);
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                dest.writeParcelable(child, flags);
            }
        }
    }

    public void readFromParcel(Parcel source) {
        final int size = source.readInt();
        mValues = source.<ValuesDelta> readParcelable(null);
        for (int i = 0; i < size; i++) {
            final ValuesDelta child = source.<ValuesDelta> readParcelable(null);
            this.addEntry(child);
        }
    }

    public static final Parcelable.Creator<EntityDelta> CREATOR = new Parcelable.Creator<EntityDelta>() {
        public EntityDelta createFromParcel(Parcel in) {
            final EntityDelta state = new EntityDelta();
            state.readFromParcel(in);
            return state;
        }

        public EntityDelta[] newArray(int size) {
            return new EntityDelta[size];
        }
    };

    /**
     * Type of {@link ContentValues} that maintains both an original state and a
     * modified version of that state. This allows us to build insert, update,
     * or delete operations based on a "before" {@link Entity} snapshot.
     */
    public static class ValuesDelta implements Parcelable {
        private ContentValues mBefore;
        private ContentValues mAfter;
        private String mIdColumn = BaseColumns._ID;

        private ValuesDelta() {
        }

        /**
         * Create {@link ValuesDelta}, using the given object as the
         * "before" state, usually from an {@link Entity}.
         */
        public static ValuesDelta fromBefore(ContentValues before) {
            final ValuesDelta entry = new ValuesDelta();
            entry.mBefore = before;
            entry.mAfter = new ContentValues();
            return entry;
        }

        /**
         * Create {@link ValuesDelta}, using the given object as the "after"
         * state, usually when we are inserting a row instead of updating.
         */
        public static ValuesDelta fromAfter(ContentValues after) {
            final ValuesDelta entry = new ValuesDelta();
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

        public byte[] getAsByteArray(String key) {
            if (mAfter != null && mAfter.containsKey(key)) {
                return mAfter.getAsByteArray(key);
            } else if (mBefore != null && mBefore.containsKey(key)) {
                return mBefore.getAsByteArray(key);
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

        public boolean beforeExists() {
            return (mBefore != null && mBefore.containsKey(mIdColumn));
        }

        public boolean isVisible() {
            // When "after" is present, then visible
            return (mAfter != null);
        }

        public boolean isDelete() {
            // When "after" is wiped, action is "delete"
            return beforeExists() && (mAfter == null);
        }

        public boolean isUpdate() {
            // When "after" has some changes, action is "update"
            return beforeExists() && (mAfter.size() > 0);
        }

        public boolean isInsert() {
            // When no "before" id, and has "after", action is "insert"
            return !beforeExists() && (mAfter != null);
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
            if (object instanceof ValuesDelta) {
                // Only exactly equal with both are identical subsets
                final ValuesDelta other = (ValuesDelta)object;
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
         * Check if the given {@link ValuesDelta} is both a subset of this
         * object, and any defined keys have equal values.
         */
        public boolean subsetEquals(ValuesDelta other) {
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

        /** {@inheritDoc} */
        public int describeContents() {
            // Nothing special about this parcel
            return 0;
        }

        /** {@inheritDoc} */
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(mBefore, flags);
            dest.writeParcelable(mAfter, flags);
            dest.writeString(mIdColumn);
        }

        public void readFromParcel(Parcel source) {
            mBefore = source.<ContentValues> readParcelable(null);
            mAfter = source.<ContentValues> readParcelable(null);
            mIdColumn = source.readString();
        }

        public static final Parcelable.Creator<ValuesDelta> CREATOR = new Parcelable.Creator<ValuesDelta>() {
            public ValuesDelta createFromParcel(Parcel in) {
                final ValuesDelta values = new ValuesDelta();
                values.readFromParcel(in);
                return values;
            }

            public ValuesDelta[] newArray(int size) {
                return new ValuesDelta[size];
            }
        };
    }
}
