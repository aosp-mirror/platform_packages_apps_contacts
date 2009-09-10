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

import com.google.android.collect.Lists;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.ContentProviderOperation.Builder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;

import java.util.ArrayList;

/**
 * Container for multiple {@link EntityDelta} objects, usually when editing
 * together as an entire aggregate. Provides convenience methods for parceling
 * and applying another {@link EntitySet} over it.
 */
public class EntitySet extends ArrayList<EntityDelta> implements Parcelable {
    private EntitySet() {
    }

    /**
     * Create an {@link EntitySet} that contains the given {@link EntityDelta},
     * usually when inserting a new {@link Contacts} entry.
     */
    public static EntitySet fromSingle(EntityDelta delta) {
        final EntitySet state = new EntitySet();
        state.add(delta);
        return state;
    }

    /**
     * Create an {@link EntitySet} based on {@link Contacts} specified by the
     * given query parameters. This closes the {@link EntityIterator} when
     * finished, so it doesn't subscribe to updates.
     */
    public static EntitySet fromQuery(ContentResolver resolver, String selection,
            String[] selectionArgs, String sortOrder) {
        EntityIterator iterator = null;
        final EntitySet state = new EntitySet();
        try {
            // Perform background query to pull contact details
            iterator = resolver.queryEntities(RawContacts.CONTENT_URI, selection, selectionArgs,
                    sortOrder);
            while (iterator.hasNext()) {
                // Read all contacts into local deltas to prepare for edits
                final Entity before = iterator.next();
                final EntityDelta entity = EntityDelta.fromBefore(before);
                state.add(entity);
            }
        } catch (RemoteException e) {
            throw new IllegalStateException("Problem querying contact details", e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
        return state;
    }

    /**
     * Merge the "after" values from the given {@link EntitySet}.
     */
    public void mergeAfter(EntitySet remote) {
        // TODO: write this folding logic to re-parent
        throw new UnsupportedOperationException();
    }

    /**
     * Build a list of {@link ContentProviderOperation} that will transform all
     * the "before" {@link Entity} states into the modified state which all
     * {@link EntityDelta} objects represent. This method specifically creates
     * any {@link AggregationExceptions} rules needed to groups edits together.
     */
    public ArrayList<ContentProviderOperation> buildDiff() {
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();

        final long rawContactId = this.findRawContactId();
        int firstInsertRow = -1;

        // First pass enforces versions remain consistent
        for (EntityDelta delta : this) {
            delta.buildAssert(diff);
        }

        final int assertMark = diff.size();

        // Second pass builds actual operations
        for (EntityDelta delta : this) {
            final int firstBatch = diff.size();
            delta.buildDiff(diff);

            // Only create rules for inserts
            if (!delta.isContactInsert()) continue;

            if (rawContactId != -1) {
                // Has existing contact, so bind to it strongly
                final Builder builder = beginKeepTogether();
                builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId);
                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, firstBatch);
                diff.add(builder.build());

            } else if (firstInsertRow == -1) {
                // First insert case, so record row
                firstInsertRow = firstBatch;

            } else {
                // Additional insert case, so point at first insert
                final Builder builder = beginKeepTogether();
                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID1, firstInsertRow);
                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, firstBatch);
                diff.add(builder.build());
            }
        }

        // No real changes if only left with asserts
        if (diff.size() == assertMark) {
            diff.clear();
        }

        return diff;
    }

    /**
     * Start building a {@link ContentProviderOperation} that will keep two
     * {@link RawContacts} together.
     */
    protected Builder beginKeepTogether() {
        final Builder builder = ContentProviderOperation
                .newUpdate(AggregationExceptions.CONTENT_URI);
        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
        return builder;
    }

    /**
     * Search all contained {@link EntityDelta} for the first one with an
     * existing {@link RawContacts#_ID} value. Usually used when creating
     * {@link AggregationExceptions} during an update.
     */
    public long findRawContactId() {
        for (EntityDelta delta : this) {
            final Long rawContactId = delta.getValues().getAsLong(RawContacts._ID);
            if (rawContactId != null && rawContactId >= 0) {
                return rawContactId;
            }
        }
        return -1;
    }

    /**
     * Find {@link RawContacts#_ID} of the requested {@link EntityDelta}.
     */
    public long getRawContactId(int index) {
        if (index >=0 && index < this.size()) {
            final EntityDelta delta = this.get(index);
            return delta.getValues().getAsLong(RawContacts._ID);
        } else {
            return -1;
        }
    }

    /** {@inheritDoc} */
    public int describeContents() {
        // Nothing special about this parcel
        return 0;
    }

    /** {@inheritDoc} */
    public void writeToParcel(Parcel dest, int flags) {
        final int size = this.size();
        dest.writeInt(size);
        for (EntityDelta delta : this) {
            dest.writeParcelable(delta, flags);
        }
    }

    public void readFromParcel(Parcel source) {
        final int size = source.readInt();
        for (int i = 0; i < size; i++) {
            this.add(source.<EntityDelta> readParcelable(null));
        }
    }

    public static final Parcelable.Creator<EntitySet> CREATOR = new Parcelable.Creator<EntitySet>() {
        public EntitySet createFromParcel(Parcel in) {
            final EntitySet state = new EntitySet();
            state.readFromParcel(in);
            return state;
        }

        public EntitySet[] newArray(int size) {
            return new EntitySet[size];
        }
    };
}
