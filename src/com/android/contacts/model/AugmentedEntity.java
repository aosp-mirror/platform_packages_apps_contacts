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
import android.content.Entity.NamedContentValues;
import android.os.Parcel;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Contains an {@link Entity} that records any modifications separately so the
 * original {@link Entity} can be swapped out with a newer version and the
 * changes still cleanly applied.
 * <p>
 * One benefit of this approach is that we can build changes entirely on an
 * empty {@link Entity}, which then becomes an insert {@link Contacts} case.
 * <p>
 * When applying modifications over an {@link Entity}, we try finding the
 * original {@link Data#_ID} rows where the modifications took place. If those
 * rows are missing from the new {@link Entity}, we know the original data must
 * be deleted, but to preserve the user modifications we treat as an insert.
 */
public class AugmentedEntity {
    // TODO: optimize by using contentvalues pool, since we allocate so many of them
    // TODO: write unit tests to make sure that getDiff() is performing correctly

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

    /**
     * Build an {@link AugmentedEntity} using the given {@link Entity} as a
     * starting point; the "before" snapshot.
     */
    public static AugmentedEntity fromBefore(Entity before) {
        final AugmentedEntity entity = new AugmentedEntity();
        entity.mValues = AugmentedValues.fromBefore(before.getEntityValues());
        for (NamedContentValues namedValues : before.getSubValues()) {
            entity.addEntry(AugmentedValues.fromBefore(namedValues.values));
        }
        return entity;
    }

    /**
     * Get the {@link AugmentedValues} child marked as {@link Data#IS_PRIMARY}.
     */
    public AugmentedValues getPrimaryEntry(String mimeType) {
        // TODO: handle the case where the caller must have a non-null value,
        // for example displayname
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
    public AugmentedValues getEntry(long anchorId) {
        if (anchorId < 0) {
            // Requesting an "insert" entry, which has no "before"
            return null;
        }

        // Search all children for requested entry
        for (ArrayList<AugmentedValues> mimeEntries : mEntries.values()) {
            for (AugmentedValues entry : mimeEntries) {
                if (entry.getId() == anchorId) {
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
            final long anchorId = parcel.readLong();
            final ContentValues after = (ContentValues)parcel.readValue(null);

            AugmentedValues entry = getEntry(anchorId);
            if (anchorId < 0 || entry == null) {
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
                parcel.writeLong(child.getId());
                parcel.writeValue(child.mAfter);
            }
        }
        parcel.writeInt(MODE_DONE);
    }

    /**
     * Build a list of {@link ContentProviderOperation} that will transform the
     * current "before" {@link Entity} state into the modified state which this
     * {@link AugmentedEntity} represents.
     */
    public ArrayList<ContentProviderOperation> getDiff() {
        // TODO: assert that existing contact exists, and provide CONTACT_ID to children inserts
        // TODO: mostly calling through to children for diff operations
        return null;
    }

    /**
     * Type of {@link ContentValues} that maintains both an original state and a
     * modified version of that state. This allows us to build insert, update,
     * or delete operations based on a "before" {@link Entity} snapshot.
     */
    public static class AugmentedValues {
        private ContentValues mBefore;
        private ContentValues mAfter;

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
            return getAsLong(BaseColumns._ID);
        }

        public boolean isPrimary() {
            return (getAsLong(Data.IS_PRIMARY) != 0);
        }

        public boolean isDeleted() {
            return (mAfter == null);
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
         * Build a {@link ContentProviderOperation} that will transform our
         * "before" state into our "after" state, using insert, update, or
         * delete as needed.
         */
        public ContentProviderOperation getDiff() {
            // TODO: build insert/update/delete based on internal state
            // any _id under zero are inserts
            return null;
        }

    }

}
