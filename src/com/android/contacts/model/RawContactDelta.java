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
import android.content.ContentProviderOperation.Builder;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.dataitem.DataItem;
import com.android.contacts.test.NeededForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
/**
 * Contains a {@link RawContact} and records any modifications separately so the
 * original {@link RawContact} can be swapped out with a newer version and the
 * changes still cleanly applied.
 * <p>
 * One benefit of this approach is that we can build changes entirely on an
 * empty {@link RawContact}, which then becomes an insert {@link RawContacts} case.
 * <p>
 * When applying modifications over an {@link RawContact}, we try finding the
 * original {@link Data#_ID} rows where the modifications took place. If those
 * rows are missing from the new {@link RawContact}, we know the original data must
 * be deleted, but to preserve the user modifications we treat as an insert.
 */
public class RawContactDelta implements Parcelable {
    // TODO: optimize by using contentvalues pool, since we allocate so many of them

    private static final String TAG = "EntityDelta";
    private static final boolean LOGV = false;

    /**
     * Direct values from {@link Entity#getEntityValues()}.
     */
    private ValuesDelta mValues;

    /**
     * URI used for contacts queries, by default it is set to query raw contacts.
     * It can be set to query the profile's raw contact(s).
     */
    private Uri mContactsQueryUri = RawContacts.CONTENT_URI;

    /**
     * Internal map of children values from {@link Entity#getSubValues()}, which
     * we store here sorted into {@link Data#MIMETYPE} bins.
     */
    private final HashMap<String, ArrayList<ValuesDelta>> mEntries = Maps.newHashMap();

    public RawContactDelta() {
    }

    public RawContactDelta(ValuesDelta values) {
        mValues = values;
    }

    /**
     * Build an {@link RawContactDelta} using the given {@link RawContact} as a
     * starting point; the "before" snapshot.
     */
    public static RawContactDelta fromBefore(RawContact before) {
        final RawContactDelta rawContactDelta = new RawContactDelta();
        rawContactDelta.mValues = ValuesDelta.fromBefore(before.getValues());
        rawContactDelta.mValues.setIdColumn(RawContacts._ID);
        for (DataItem dataItem : before.getDataItems()) {
            rawContactDelta.addEntry(ValuesDelta.fromBefore(dataItem.getContentValues()));
        }
        return rawContactDelta;
    }

    /**
     * Merge the "after" values from the given {@link RawContactDelta} onto the
     * "before" state represented by this {@link RawContactDelta}, discarding any
     * existing "after" states. This is typically used when re-parenting changes
     * onto an updated {@link Entity}.
     */
    public static RawContactDelta mergeAfter(RawContactDelta local, RawContactDelta remote) {
        // Bail early if trying to merge delete with missing local
        final ValuesDelta remoteValues = remote.mValues;
        if (local == null && (remoteValues.isDelete() || remoteValues.isTransient())) return null;

        // Create local version if none exists yet
        if (local == null) local = new RawContactDelta();

        if (LOGV) {
            final Long localVersion = (local.mValues == null) ? null : local.mValues
                    .getAsLong(RawContacts.VERSION);
            final Long remoteVersion = remote.mValues.getAsLong(RawContacts.VERSION);
            Log.d(TAG, "Re-parenting from original version " + remoteVersion + " to "
                    + localVersion);
        }

        // Create values if needed, and merge "after" changes
        local.mValues = ValuesDelta.mergeAfter(local.mValues, remote.mValues);

        // Find matching local entry for each remote values, or create
        for (ArrayList<ValuesDelta> mimeEntries : remote.mEntries.values()) {
            for (ValuesDelta remoteEntry : mimeEntries) {
                final Long childId = remoteEntry.getId();

                // Find or create local match and merge
                final ValuesDelta localEntry = local.getEntry(childId);
                final ValuesDelta merged = ValuesDelta.mergeAfter(localEntry, remoteEntry);

                if (localEntry == null && merged != null) {
                    // No local entry before, so insert
                    local.addEntry(merged);
                }
            }
        }

        return local;
    }

    public ValuesDelta getValues() {
        return mValues;
    }

    public boolean isContactInsert() {
        return mValues.isInsert();
    }

    /**
     * Get the {@link ValuesDelta} child marked as {@link Data#IS_PRIMARY},
     * which may return null when no entry exists.
     */
    public ValuesDelta getPrimaryEntry(String mimeType) {
        final ArrayList<ValuesDelta> mimeEntries = getMimeEntries(mimeType, false);
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
     * calls {@link #getSuperPrimaryEntry(String, boolean)} with true
     * @see #getSuperPrimaryEntry(String, boolean)
     */
    public ValuesDelta getSuperPrimaryEntry(String mimeType) {
        return getSuperPrimaryEntry(mimeType, true);
    }

    /**
     * Returns the super-primary entry for the given mime type
     * @param forceSelection if true, will try to return some value even if a super-primary
     *     doesn't exist (may be a primary, or just a random item
     * @return
     */
    @NeededForTesting
    public ValuesDelta getSuperPrimaryEntry(String mimeType, boolean forceSelection) {
        final ArrayList<ValuesDelta> mimeEntries = getMimeEntries(mimeType, false);
        if (mimeEntries == null) return null;

        ValuesDelta primary = null;
        for (ValuesDelta entry : mimeEntries) {
            if (entry.isSuperPrimary()) {
                return entry;
            } else if (entry.isPrimary()) {
                primary = entry;
            }
        }

        if (!forceSelection) {
            return null;
        }

        // When no direct super primary, return something
        if (primary != null) {
            return primary;
        }
        return mimeEntries.size() > 0 ? mimeEntries.get(0) : null;
    }

    /**
     * Return the AccountType that this raw-contact belongs to.
     */
    public AccountType getRawContactAccountType(Context context) {
        ContentValues entityValues = getValues().getCompleteValues();
        String type = entityValues.getAsString(RawContacts.ACCOUNT_TYPE);
        String dataSet = entityValues.getAsString(RawContacts.DATA_SET);
        return AccountTypeManager.getInstance(context).getAccountType(type, dataSet);
    }

    public Long getRawContactId() {
        return getValues().getAsLong(RawContacts._ID);
    }

    public String getAccountName() {
        return getValues().getAsString(RawContacts.ACCOUNT_NAME);
    }

    public String getAccountType() {
        return getValues().getAsString(RawContacts.ACCOUNT_TYPE);
    }

    public String getDataSet() {
        return getValues().getAsString(RawContacts.DATA_SET);
    }

    public AccountType getAccountType(AccountTypeManager manager) {
        return manager.getAccountType(getAccountType(), getDataSet());
    }

    public boolean isVisible() {
        return getValues().isVisible();
    }

    /**
     * Return the list of child {@link ValuesDelta} from our optimized map,
     * creating the list if requested.
     */
    private ArrayList<ValuesDelta> getMimeEntries(String mimeType, boolean lazyCreate) {
        ArrayList<ValuesDelta> mimeEntries = mEntries.get(mimeType);
        if (mimeEntries == null && lazyCreate) {
            mimeEntries = Lists.newArrayList();
            mEntries.put(mimeType, mimeEntries);
        }
        return mimeEntries;
    }

    public ArrayList<ValuesDelta> getMimeEntries(String mimeType) {
        return getMimeEntries(mimeType, false);
    }

    public int getMimeEntriesCount(String mimeType, boolean onlyVisible) {
        final ArrayList<ValuesDelta> mimeEntries = getMimeEntries(mimeType);
        if (mimeEntries == null) return 0;

        int count = 0;
        for (ValuesDelta child : mimeEntries) {
            // Skip deleted items when requesting only visible
            if (onlyVisible && !child.isVisible()) continue;
            count++;
        }
        return count;
    }

    public boolean hasMimeEntries(String mimeType) {
        return mEntries.containsKey(mimeType);
    }

    public ValuesDelta addEntry(ValuesDelta entry) {
        final String mimeType = entry.getMimetype();
        getMimeEntries(mimeType, true).add(entry);
        return entry;
    }

    public ArrayList<ContentValues> getContentValues() {
        ArrayList<ContentValues> values = Lists.newArrayList();
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta entry : mimeEntries) {
                if (!entry.isDelete()) {
                    values.add(entry.getCompleteValues());
                }
            }
        }
        return values;
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
                if (childId.equals(entry.getId())) {
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
        for (String mimeType : mEntries.keySet()) {
            count += getMimeEntriesCount(mimeType, onlyVisible);
        }
        return count;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof RawContactDelta) {
            final RawContactDelta other = (RawContactDelta)object;

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

    /**
     * Mark this entire object deleted, including any {@link ValuesDelta}.
     */
    public void markDeleted() {
        this.mValues.markDeleted();
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                child.markDeleted();
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("\n(");
        builder.append("Uri=");
        builder.append(mContactsQueryUri);
        builder.append(", Values=");
        builder.append(mValues != null ? mValues.toString() : "null");
        builder.append(", Entries={");
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                builder.append("\n\t");
                child.toString(builder);
            }
        }
        builder.append("\n})\n");
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
     * Build a list of {@link ContentProviderOperation} that will assert any
     * "before" state hasn't changed. This is maintained separately so that all
     * asserts can take place before any updates occur.
     */
    public void buildAssert(ArrayList<ContentProviderOperation> buildInto) {
        final boolean isContactInsert = mValues.isInsert();
        if (!isContactInsert) {
            // Assert version is consistent while persisting changes
            final Long beforeId = mValues.getId();
            final Long beforeVersion = mValues.getAsLong(RawContacts.VERSION);
            if (beforeId == null || beforeVersion == null) return;

            final ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newAssertQuery(mContactsQueryUri);
            builder.withSelection(RawContacts._ID + "=" + beforeId, null);
            builder.withValue(RawContacts.VERSION, beforeVersion);
            buildInto.add(builder.build());
        }
    }

    /**
     * Build a list of {@link ContentProviderOperation} that will transform the
     * current "before" {@link Entity} state into the modified state which this
     * {@link RawContactDelta} represents.
     */
    public void buildDiff(ArrayList<ContentProviderOperation> buildInto) {
        final int firstIndex = buildInto.size();

        final boolean isContactInsert = mValues.isInsert();
        final boolean isContactDelete = mValues.isDelete();
        final boolean isContactUpdate = !isContactInsert && !isContactDelete;

        final Long beforeId = mValues.getId();

        Builder builder;

        if (isContactInsert) {
            // TODO: for now simply disabling aggregation when a new contact is
            // created on the phone.  In the future, will show aggregation suggestions
            // after saving the contact.
            mValues.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        }

        // Build possible operation at Contact level
        builder = mValues.buildDiff(mContactsQueryUri);
        possibleAdd(buildInto, builder);

        // Build operations for all children
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                // Ignore children if parent was deleted
                if (isContactDelete) continue;

                // Use the profile data URI if the contact is the profile.
                if (mContactsQueryUri.equals(Profile.CONTENT_RAW_CONTACTS_URI)) {
                    builder = child.buildDiff(Uri.withAppendedPath(Profile.CONTENT_URI,
                            RawContacts.Data.CONTENT_DIRECTORY));
                } else {
                    builder = child.buildDiff(Data.CONTENT_URI);
                }

                if (child.isInsert()) {
                    if (isContactInsert) {
                        // Parent is brand new insert, so back-reference _id
                        builder.withValueBackReference(Data.RAW_CONTACT_ID, firstIndex);
                    } else {
                        // Inserting under existing, so fill with known _id
                        builder.withValue(Data.RAW_CONTACT_ID, beforeId);
                    }
                } else if (isContactInsert && builder != null) {
                    // Child must be insert when Contact insert
                    throw new IllegalArgumentException("When parent insert, child must be also");
                }
                possibleAdd(buildInto, builder);
            }
        }

        final boolean addedOperations = buildInto.size() > firstIndex;
        if (addedOperations && isContactUpdate) {
            // Suspend aggregation while persisting updates
            builder = buildSetAggregationMode(beforeId, RawContacts.AGGREGATION_MODE_SUSPENDED);
            buildInto.add(firstIndex, builder.build());

            // Restore aggregation mode as last operation
            builder = buildSetAggregationMode(beforeId, RawContacts.AGGREGATION_MODE_DEFAULT);
            buildInto.add(builder.build());
        } else if (isContactInsert) {
            // Restore aggregation mode as last operation
            builder = ContentProviderOperation.newUpdate(mContactsQueryUri);
            builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT);
            builder.withSelection(RawContacts._ID + "=?", new String[1]);
            builder.withSelectionBackReference(0, firstIndex);
            buildInto.add(builder.build());
        }
    }

    /**
     * Build a {@link ContentProviderOperation} that changes
     * {@link RawContacts#AGGREGATION_MODE} to the given value.
     */
    protected Builder buildSetAggregationMode(Long beforeId, int mode) {
        Builder builder = ContentProviderOperation.newUpdate(mContactsQueryUri);
        builder.withValue(RawContacts.AGGREGATION_MODE, mode);
        builder.withSelection(RawContacts._ID + "=" + beforeId, null);
        return builder;
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
        dest.writeParcelable(mContactsQueryUri, flags);
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                dest.writeParcelable(child, flags);
            }
        }
    }

    public void readFromParcel(Parcel source) {
        final ClassLoader loader = getClass().getClassLoader();
        final int size = source.readInt();
        mValues = source.<ValuesDelta> readParcelable(loader);
        mContactsQueryUri = source.<Uri> readParcelable(loader);
        for (int i = 0; i < size; i++) {
            final ValuesDelta child = source.<ValuesDelta> readParcelable(loader);
            this.addEntry(child);
        }
    }

    /**
     * Used to set the query URI to the profile URI to store profiles.
     */
    public void setProfileQueryUri() {
        mContactsQueryUri = Profile.CONTENT_RAW_CONTACTS_URI;
    }

    public static final Parcelable.Creator<RawContactDelta> CREATOR =
            new Parcelable.Creator<RawContactDelta>() {
        public RawContactDelta createFromParcel(Parcel in) {
            final RawContactDelta state = new RawContactDelta();
            state.readFromParcel(in);
            return state;
        }

        public RawContactDelta[] newArray(int size) {
            return new RawContactDelta[size];
        }
    };

    /**
     * Type of {@link ContentValues} that maintains both an original state and a
     * modified version of that state. This allows us to build insert, update,
     * or delete operations based on a "before" {@link Entity} snapshot.
     */
    public static class ValuesDelta implements Parcelable {
        protected ContentValues mBefore;
        protected ContentValues mAfter;
        protected String mIdColumn = BaseColumns._ID;
        private boolean mFromTemplate;

        /**
         * Next value to assign to {@link #mIdColumn} when building an insert
         * operation through {@link #fromAfter(ContentValues)}. This is used so
         * we can concretely reference this {@link ValuesDelta} before it has
         * been persisted.
         */
        protected static int sNextInsertId = -1;

        protected ValuesDelta() {
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

            // Assign temporary id which is dropped before insert.
            entry.mAfter.put(entry.mIdColumn, sNextInsertId--);
            return entry;
        }

        @NeededForTesting
        public ContentValues getAfter() {
            return mAfter;
        }

        public boolean containsKey(String key) {
            return ((mAfter != null && mAfter.containsKey(key)) ||
                    (mBefore != null && mBefore.containsKey(key)));
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

        public Integer getAsInteger(String key) {
            return getAsInteger(key, null);
        }

        public Integer getAsInteger(String key, Integer defaultValue) {
            if (mAfter != null && mAfter.containsKey(key)) {
                return mAfter.getAsInteger(key);
            } else if (mBefore != null && mBefore.containsKey(key)) {
                return mBefore.getAsInteger(key);
            } else {
                return defaultValue;
            }
        }

        public boolean isChanged(String key) {
            if (mAfter == null || !mAfter.containsKey(key)) {
                return false;
            }

            Object newValue = mAfter.get(key);
            Object oldValue = mBefore.get(key);

            if (oldValue == null) {
                return newValue != null;
            }

            return !oldValue.equals(newValue);
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
            final Long isPrimary = getAsLong(Data.IS_PRIMARY);
            return isPrimary == null ? false : isPrimary != 0;
        }

        public void setFromTemplate(boolean isFromTemplate) {
            mFromTemplate = isFromTemplate;
        }

        public boolean isFromTemplate() {
            return mFromTemplate;
        }

        public boolean isSuperPrimary() {
            final Long isSuperPrimary = getAsLong(Data.IS_SUPER_PRIMARY);
            return isSuperPrimary == null ? false : isSuperPrimary != 0;
        }

        public boolean beforeExists() {
            return (mBefore != null && mBefore.containsKey(mIdColumn));
        }

        /**
         * When "after" is present, then visible
         */
        public boolean isVisible() {
            return (mAfter != null);
        }

        /**
         * When "after" is wiped, action is "delete"
         */
        public boolean isDelete() {
            return beforeExists() && (mAfter == null);
        }

        /**
         * When no "before" or "after", is transient
         */
        public boolean isTransient() {
            return (mBefore == null) && (mAfter == null);
        }

        /**
         * When "after" has some changes, action is "update"
         */
        public boolean isUpdate() {
            if (!beforeExists() || mAfter == null || mAfter.size() == 0) {
                return false;
            }
            for (String key : mAfter.keySet()) {
                Object newValue = mAfter.get(key);
                Object oldValue = mBefore.get(key);
                if (oldValue == null) {
                    if (newValue != null) {
                        return true;
                    }
                } else if (!oldValue.equals(newValue)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * When "after" has no changes, action is no-op
         */
        public boolean isNoop() {
            return beforeExists() && (mAfter != null && mAfter.size() == 0);
        }

        /**
         * When no "before" id, and has "after", action is "insert"
         */
        public boolean isInsert() {
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

        public void put(String key, byte[] value) {
            ensureUpdate();
            mAfter.put(key, value);
        }

        public void put(String key, int value) {
            ensureUpdate();
            mAfter.put(key, value);
        }

        public void put(String key, long value) {
            ensureUpdate();
            mAfter.put(key, value);
        }

        public void putNull(String key) {
            ensureUpdate();
            mAfter.putNull(key);
        }

        public void copyStringFrom(ValuesDelta from, String key) {
            ensureUpdate();
            put(key, from.getAsString(key));
        }

        /**
         * Return set of all keys defined through this object.
         */
        public Set<String> keySet() {
            final HashSet<String> keys = Sets.newHashSet();

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

        /**
         * Return complete set of "before" and "after" values mixed together,
         * giving full state regardless of edits.
         */
        public ContentValues getCompleteValues() {
            final ContentValues values = new ContentValues();
            if (mBefore != null) {
                values.putAll(mBefore);
            }
            if (mAfter != null) {
                values.putAll(mAfter);
            }
            if (values.containsKey(GroupMembership.GROUP_ROW_ID)) {
                // Clear to avoid double-definitions, and prefer rows
                values.remove(GroupMembership.GROUP_SOURCE_ID);
            }

            return values;
        }

        /**
         * Merge the "after" values from the given {@link ValuesDelta},
         * discarding any existing "after" state. This is typically used when
         * re-parenting changes onto an updated {@link Entity}.
         */
        public static ValuesDelta mergeAfter(ValuesDelta local, ValuesDelta remote) {
            // Bail early if trying to merge delete with missing local
            if (local == null && (remote.isDelete() || remote.isTransient())) return null;

            // Create local version if none exists yet
            if (local == null) local = new ValuesDelta();

            if (!local.beforeExists()) {
                // Any "before" record is missing, so take all values as "insert"
                local.mAfter = remote.getCompleteValues();
            } else {
                // Existing "update" with only "after" values
                local.mAfter = remote.mAfter;
            }

            return local;
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
            builder.append("IdColumn=");
            builder.append(mIdColumn);
            builder.append(", FromTemplate=");
            builder.append(mFromTemplate);
            builder.append(", ");
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
                mAfter.remove(mIdColumn);
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
            final ClassLoader loader = getClass().getClassLoader();
            mBefore = source.<ContentValues> readParcelable(loader);
            mAfter = source.<ContentValues> readParcelable(loader);
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

        public void setGroupRowId(long groupId) {
            put(GroupMembership.GROUP_ROW_ID, groupId);
        }

        public Long getGroupRowId() {
            return getAsLong(GroupMembership.GROUP_ROW_ID);
        }

        public void setPhoto(byte[] value) {
            put(Photo.PHOTO, value);
        }

        public byte[] getPhoto() {
            return getAsByteArray(Photo.PHOTO);
        }

        public void setSuperPrimary(boolean val) {
            if (val) {
                put(Data.IS_SUPER_PRIMARY, 1);
            } else {
                put(Data.IS_SUPER_PRIMARY, 0);
            }
        }

        public void setPhoneticFamilyName(String value) {
            put(StructuredName.PHONETIC_FAMILY_NAME, value);
        }

        public void setPhoneticMiddleName(String value) {
            put(StructuredName.PHONETIC_MIDDLE_NAME, value);
        }

        public void setPhoneticGivenName(String value) {
            put(StructuredName.PHONETIC_GIVEN_NAME, value);
        }

        public String getPhoneticFamilyName() {
            return getAsString(StructuredName.PHONETIC_FAMILY_NAME);
        }

        public String getPhoneticMiddleName() {
            return getAsString(StructuredName.PHONETIC_MIDDLE_NAME);
        }

        public String getPhoneticGivenName() {
            return getAsString(StructuredName.PHONETIC_GIVEN_NAME);
        }

        public String getDisplayName() {
            return getAsString(StructuredName.DISPLAY_NAME);
        }

        public void setDisplayName(String name) {
            if (name == null) {
                putNull(StructuredName.DISPLAY_NAME);
            } else {
                put(StructuredName.DISPLAY_NAME, name);
            }
        }

        public void copyStructuredNameFieldsFrom(ValuesDelta name) {
            copyStringFrom(name, StructuredName.DISPLAY_NAME);

            copyStringFrom(name, StructuredName.GIVEN_NAME);
            copyStringFrom(name, StructuredName.FAMILY_NAME);
            copyStringFrom(name, StructuredName.PREFIX);
            copyStringFrom(name, StructuredName.MIDDLE_NAME);
            copyStringFrom(name, StructuredName.SUFFIX);

            copyStringFrom(name, StructuredName.PHONETIC_GIVEN_NAME);
            copyStringFrom(name, StructuredName.PHONETIC_MIDDLE_NAME);
            copyStringFrom(name, StructuredName.PHONETIC_FAMILY_NAME);

            copyStringFrom(name, StructuredName.FULL_NAME_STYLE);
            copyStringFrom(name, StructuredName.PHONETIC_NAME_STYLE);
        }

        public String getPhoneNumber() {
            return getAsString(Phone.NUMBER);
        }

        public String getPhoneNormalizedNumber() {
            return getAsString(Phone.NORMALIZED_NUMBER);
        }

        public boolean phoneHasType() {
            return containsKey(Phone.TYPE);
        }

        public int getPhoneType() {
            return getAsInteger(Phone.TYPE);
        }

        public String getPhoneLabel() {
            return getAsString(Phone.LABEL);
        }

        public String getEmailData() {
            return getAsString(Email.DATA);
        }

        public boolean emailHasType() {
            return containsKey(Email.TYPE);
        }

        public int getEmailType() {
            return getAsInteger(Email.TYPE);
        }

        public String getEmailLabel() {
            return getAsString(Email.LABEL);
        }
    }
}
