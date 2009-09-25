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

package com.android.contacts;

import static android.content.ContentProviderOperation.TYPE_ASSERT;
import static android.content.ContentProviderOperation.TYPE_DELETE;
import static android.content.ContentProviderOperation.TYPE_INSERT;
import static android.content.ContentProviderOperation.TYPE_UPDATE;

import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntitySet;
import com.android.contacts.model.EntityDelta.ValuesDelta;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Entity;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import java.util.ArrayList;

/**
 * Tests for {@link EntitySet} which focus on "diff" operations that should
 * create {@link AggregationExceptions} in certain cases.
 */
@LargeTest
public class EntitySetTests extends AndroidTestCase {
    public static final String TAG = "EntitySetTests";

    private static final long CONTACT_FIRST = 1;
    private static final long CONTACT_SECOND = 2;

    public static final long CONTACT_BOB = 10;
    public static final long CONTACT_MARY = 11;

    public static final long PHONE_RED = 20;
    public static final long PHONE_GREEN = 21;
    public static final long PHONE_BLUE = 22;

    public static final long VER_FIRST = 100;
    public static final long VER_SECOND = 200;

    public EntitySetTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
    }

    protected static EntityDelta getUpdate(long rawContactId) {
        final Entity before = EntityDeltaTests.getEntity(rawContactId,
                EntityDeltaTests.TEST_PHONE_ID);
        return EntityDelta.fromBefore(before);
    }

    protected static EntityDelta getInsert() {
        final ContentValues after = new ContentValues();
        after.put(RawContacts.ACCOUNT_NAME, EntityDeltaTests.TEST_ACCOUNT_NAME);
        after.put(RawContacts.SEND_TO_VOICEMAIL, 1);

        final ValuesDelta values = ValuesDelta.fromAfter(after);
        return new EntityDelta(values);
    }

    protected static EntitySet buildSet(EntityDelta... deltas) {
        final EntitySet set = EntitySet.fromSingle(deltas[0]);
        for (int i = 1; i < deltas.length; i++) {
            set.add(deltas[i]);
        }
        return set;
    }

    protected static EntityDelta buildBeforeEntity(long rawContactId, long version,
            ContentValues... entries) {
        // Build an existing contact read from database
        final ContentValues contact = new ContentValues();
        contact.put(RawContacts.VERSION, version);
        contact.put(RawContacts._ID, rawContactId);
        final Entity before = new Entity(contact);
        for (ContentValues entry : entries) {
            before.addSubValue(Data.CONTENT_URI, entry);
        }
        return EntityDelta.fromBefore(before);
    }

    protected static EntityDelta buildAfterEntity(ContentValues... entries) {
        // Build an existing contact read from database
        final ContentValues contact = new ContentValues();
        contact.putNull(RawContacts.ACCOUNT_NAME);
        final EntityDelta after = new EntityDelta(ValuesDelta.fromAfter(contact));
        for (ContentValues entry : entries) {
            after.addEntry(ValuesDelta.fromAfter(entry));
        }
        return after;
    }

    protected static ContentValues buildPhone(long phoneId) {
        return buildPhone(phoneId, Long.toString(phoneId));
    }

    protected static ContentValues buildPhone(long phoneId, String value) {
        final ContentValues values = new ContentValues();
        values.put(Data._ID, phoneId);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, value);
        values.put(Phone.TYPE, Phone.TYPE_HOME);
        return values;
    }

    protected static void insertPhone(EntitySet set, long rawContactId, ContentValues values) {
        final EntityDelta match = set.getByRawContactId(rawContactId);
        match.addEntry(ValuesDelta.fromAfter(values));
    }

    protected static ValuesDelta getPhone(EntitySet set, long rawContactId, long dataId) {
        final EntityDelta match = set.getByRawContactId(rawContactId);
        return match.getEntry(dataId);
    }

    protected void assertDiffPattern(EntitySet set, ContentProviderOperation... pattern) {
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();

        assertEquals("Unexpected operations", pattern.length, diff.size());
        for (int i = 0; i < pattern.length; i++) {
            final ContentProviderOperation expected = pattern[i];
            final ContentProviderOperation found = diff.get(i);

            final String expectedType = getStringForType(expected.getType());
            final String foundType = getStringForType(found.getType());

            assertEquals("Unexpected type", expectedType, foundType);
            assertEquals("Unexpected uri", expected.getUri(), found.getUri());
        }
    }

    protected static String getStringForType(int type) {
        switch (type) {
            case TYPE_ASSERT: return "TYPE_ASSERT";
            case TYPE_INSERT: return "TYPE_INSERT";
            case TYPE_UPDATE: return "TYPE_UPDATE";
            case TYPE_DELETE: return "TYPE_DELETE";
            default: return Integer.toString(type);
        }
    }

    protected static ContentProviderOperation buildOper(Uri uri, int type) {
        final ContentValues values = new ContentValues();
        values.put(BaseColumns._ID, 4);
        switch (type) {
            case TYPE_ASSERT:
                return ContentProviderOperation.newAssertQuery(uri).withValues(values).build();
            case TYPE_INSERT:
                return ContentProviderOperation.newInsert(uri).withValues(values).build();
            case TYPE_UPDATE:
                return ContentProviderOperation.newUpdate(uri).withValues(values).build();
            case TYPE_DELETE:
                return ContentProviderOperation.newDelete(uri).build();
        }
        return null;
    }

    protected static Long getVersion(EntitySet set, Long rawContactId) {
        return set.getByRawContactId(rawContactId).getValues().getAsLong(RawContacts.VERSION);
    }

    /**
     * Count number of {@link AggregationExceptions} updates contained in the
     * given list of {@link ContentProviderOperation}.
     */
    protected static int countExceptionUpdates(ArrayList<ContentProviderOperation> diff) {
        int updateCount = 0;
        for (ContentProviderOperation oper : diff) {
            if (AggregationExceptions.CONTENT_URI.equals(oper.getUri())
                    && oper.getType() == ContentProviderOperation.TYPE_UPDATE) {
                updateCount++;
            }
        }
        return updateCount;
    }

    public void testInsert() {
        final EntityDelta insert = getInsert();
        final EntitySet set = buildSet(insert);

        // Inserting single shouldn't create rules
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 0, exceptionCount);
    }

    public void testUpdateUpdate() {
        final EntityDelta updateFirst = getUpdate(CONTACT_FIRST);
        final EntityDelta updateSecond = getUpdate(CONTACT_SECOND);
        final EntitySet set = buildSet(updateFirst, updateSecond);

        // Updating two existing shouldn't create rules
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 0, exceptionCount);
    }

    public void testUpdateInsert() {
        final EntityDelta update = getUpdate(CONTACT_FIRST);
        final EntityDelta insert = getInsert();
        final EntitySet set = buildSet(update, insert);

        // New insert should only create one rule
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 1, exceptionCount);
    }

    public void testInsertUpdateInsert() {
        final EntityDelta insertFirst = getInsert();
        final EntityDelta update = getUpdate(CONTACT_FIRST);
        final EntityDelta insertSecond = getInsert();
        final EntitySet set = buildSet(insertFirst, update, insertSecond);

        // Two inserts should create two rules to bind against single existing
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 2, exceptionCount);
    }

    public void testInsertInsertInsert() {
        final EntityDelta insertFirst = getInsert();
        final EntityDelta insertSecond = getInsert();
        final EntityDelta insertThird = getInsert();
        final EntitySet set = buildSet(insertFirst, insertSecond, insertThird);

        // Three new inserts should create only two binding rules
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 2, exceptionCount);
    }

    public void testMergeDataRemoteInsert() {
        final EntitySet first = buildSet(buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(buildBeforeEntity(CONTACT_BOB, VER_SECOND,
                buildPhone(PHONE_RED), buildPhone(PHONE_GREEN)));

        // Merge in second version, verify they match
        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertEquals("Unexpected change when merging", second, merged);
    }

    public void testMergeDataLocalUpdateRemoteInsert() {
        final EntitySet first = buildSet(buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(buildBeforeEntity(CONTACT_BOB, VER_SECOND,
                buildPhone(PHONE_RED), buildPhone(PHONE_GREEN)));

        // Change the local number to trigger update
        getPhone(first, CONTACT_BOB, PHONE_RED).put(Phone.NUMBER, "555-1212");
        assertDiffPattern(first,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_UPDATE),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));

        // Merge in the second version, verify diff matches
        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_UPDATE),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));
    }

    public void testMergeDataLocalUpdateRemoteDelete() {
        final EntitySet first = buildSet(buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(buildBeforeEntity(CONTACT_BOB, VER_SECOND, buildPhone(PHONE_GREEN)));

        // Change the local number to trigger update
        getPhone(first, CONTACT_BOB, PHONE_RED).put(Phone.NUMBER, "555-1212");
        assertDiffPattern(first,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_UPDATE),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));

        // Merge in the second version, verify that our update changed to
        // insert, since RED was deleted on remote side
        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_INSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));
    }

    public void testMergeDataLocalDeleteRemoteUpdate() {
        final EntitySet first = buildSet(buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(buildBeforeEntity(CONTACT_BOB, VER_SECOND, buildPhone(
                PHONE_RED, "555-1212")));

        // Delete phone locally
        getPhone(first, CONTACT_BOB, PHONE_RED).markDeleted();
        assertDiffPattern(first,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_DELETE),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));

        // Merge in the second version, verify that our delete remains
        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_DELETE),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));
    }

    public void testMergeDataLocalInsertRemoteInsert() {
        final EntitySet first = buildSet(buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(buildBeforeEntity(CONTACT_BOB, VER_SECOND,
                buildPhone(PHONE_RED), buildPhone(PHONE_GREEN)));

        // Insert new phone locally
        final ValuesDelta bluePhone = ValuesDelta.fromAfter(buildPhone(PHONE_BLUE));
        first.getByRawContactId(CONTACT_BOB).addEntry(bluePhone);
        assertDiffPattern(first,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_INSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));

        // Merge in the second version, verify that our insert remains
        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_INSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));
    }

    public void testMergeRawContactLocalInsertRemoteInsert() {
        final EntitySet first = buildSet(buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(buildBeforeEntity(CONTACT_BOB, VER_SECOND, buildPhone(PHONE_RED)),
                buildBeforeEntity(CONTACT_MARY, VER_SECOND, buildPhone(PHONE_RED)));

        // Add new contact locally, should remain insert
        final EntityDelta joeContact = buildAfterEntity(buildPhone(PHONE_BLUE));
        first.add(joeContact);
        assertDiffPattern(first,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_INSERT),
                buildOper(Data.CONTENT_URI, TYPE_INSERT),
                buildOper(AggregationExceptions.CONTENT_URI, TYPE_UPDATE));

        // Merge in the second version, verify that our insert remains
        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_INSERT),
                buildOper(Data.CONTENT_URI, TYPE_INSERT),
                buildOper(AggregationExceptions.CONTENT_URI, TYPE_UPDATE));
    }

    public void testMergeRawContactLocalDeleteRemoteDelete() {
        final EntitySet first = buildSet(
                buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)),
                buildBeforeEntity(CONTACT_MARY, VER_SECOND, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(
                buildBeforeEntity(CONTACT_BOB, VER_SECOND, buildPhone(PHONE_RED)));

        // Remove contact locally
        first.getByRawContactId(CONTACT_MARY).markDeleted();
        assertDiffPattern(first,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_DELETE));

        // Merge in the second version, verify that our delete isn't needed
        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertDiffPattern(merged);
    }

    public void testMergeRawContactLocalUpdateRemoteDelete() {
        final EntitySet first = buildSet(
                buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)),
                buildBeforeEntity(CONTACT_MARY, VER_SECOND, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(
                buildBeforeEntity(CONTACT_BOB, VER_SECOND, buildPhone(PHONE_RED)));

        // Perform local update
        getPhone(first, CONTACT_MARY, PHONE_RED).put(Phone.NUMBER, "555-1212");
        assertDiffPattern(first,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE),
                buildOper(Data.CONTENT_URI, TYPE_UPDATE),
                buildOper(RawContacts.CONTENT_URI, TYPE_UPDATE));

        // Merge and verify that update turned into insert
        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildOper(RawContacts.CONTENT_URI, TYPE_ASSERT),
                buildOper(RawContacts.CONTENT_URI, TYPE_INSERT),
                buildOper(Data.CONTENT_URI, TYPE_INSERT),
                buildOper(AggregationExceptions.CONTENT_URI, TYPE_UPDATE));
    }

    public void testMergeUsesNewVersion() {
        final EntitySet first = buildSet(buildBeforeEntity(CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)));
        final EntitySet second = buildSet(buildBeforeEntity(CONTACT_BOB, VER_SECOND, buildPhone(PHONE_RED)));

        assertEquals((Long)VER_FIRST, getVersion(first, CONTACT_BOB));
        assertEquals((Long)VER_SECOND, getVersion(second, CONTACT_BOB));

        final EntitySet merged = EntitySet.mergeAfter(second, first);
        assertEquals((Long)VER_SECOND, getVersion(merged, CONTACT_BOB));
    }
}
