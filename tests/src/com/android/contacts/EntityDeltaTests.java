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

import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.google.android.collect.Lists;

import static android.content.ContentProviderOperation.TYPE_INSERT;
import static android.content.ContentProviderOperation.TYPE_UPDATE;
import static android.content.ContentProviderOperation.TYPE_DELETE;
import static android.content.ContentProviderOperation.TYPE_ASSERT;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Entity;
import android.content.ContentProviderOperation.Builder;
import android.os.Parcel;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import java.util.ArrayList;

/**
 * Tests for {@link EntityDelta} and {@link ValuesDelta}. These tests
 * focus on passing changes across {@link Parcel}, and verifying that they
 * correctly build expected "diff" operations.
 */
@LargeTest
public class EntityDeltaTests extends AndroidTestCase {
    public static final String TAG = "EntityDeltaTests";

    public static final long TEST_CONTACT_ID = 12;
    public static final long TEST_PHONE_ID = 24;

    public static final String TEST_PHONE_NUMBER_1 = "218-555-1111";
    public static final String TEST_PHONE_NUMBER_2 = "218-555-2222";

    public static final String TEST_ACCOUNT_NAME = "TEST";

    public EntityDeltaTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
    }

    public static Entity getEntity(long contactId, long phoneId) {
        // Build an existing contact read from database
        final ContentValues contact = new ContentValues();
        contact.put(RawContacts.VERSION, 43);
        contact.put(RawContacts._ID, contactId);

        final ContentValues phone = new ContentValues();
        phone.put(Data._ID, phoneId);
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);
        phone.put(Phone.TYPE, Phone.TYPE_HOME);

        final Entity before = new Entity(contact);
        before.addSubValue(Data.CONTENT_URI, phone);
        return before;
    }

    /**
     * Test that {@link EntityDelta#mergeAfter(EntityDelta)} correctly passes
     * any changes through the {@link Parcel} object. This enforces that
     * {@link EntityDelta} should be identical when serialized against the same
     * "before" {@link Entity}.
     */
    public void testParcelChangesNone() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);
        final EntityDelta dest = EntityDelta.fromBefore(before);

        // Merge modified values and assert they match
        final EntityDelta merged = EntityDelta.mergeAfter(dest, source);
        assertEquals("Unexpected change when merging", source, merged);
    }

    public void testParcelChangesInsert() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);
        final EntityDelta dest = EntityDelta.fromBefore(before);

        // Add a new row and pass across parcel, should be same
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(ValuesDelta.fromAfter(phone));

        // Merge modified values and assert they match
        final EntityDelta merged = EntityDelta.mergeAfter(dest, source);
        assertEquals("Unexpected change when merging", source, merged);
    }

    public void testParcelChangesUpdate() {
        // Update existing row and pass across parcel, should be same
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);
        final EntityDelta dest = EntityDelta.fromBefore(before);

        final ValuesDelta child = source.getEntry(TEST_PHONE_ID);
        child.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        // Merge modified values and assert they match
        final EntityDelta merged = EntityDelta.mergeAfter(dest, source);
        assertEquals("Unexpected change when merging", source, merged);
    }

    public void testParcelChangesDelete() {
        // Delete a row and pass across parcel, should be same
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);
        final EntityDelta dest = EntityDelta.fromBefore(before);

        final ValuesDelta child = source.getEntry(TEST_PHONE_ID);
        child.markDeleted();

        // Merge modified values and assert they match
        final EntityDelta merged = EntityDelta.mergeAfter(dest, source);
        assertEquals("Unexpected change when merging", source, merged);
    }

    /**
     * Test that {@link ValuesDelta#buildDiff(android.net.Uri)} is correctly
     * built for insert, update, and delete cases. Note this only tests behavior
     * for individual {@link Data} rows.
     */
    public void testValuesDiffNone() {
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_PHONE_ID);
        before.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);

        final ValuesDelta values = ValuesDelta.fromBefore(before);

        // None action shouldn't produce a builder
        final Builder builder = values.buildDiff(Data.CONTENT_URI);
        assertNull("None action produced a builder", builder);
    }

    public void testValuesDiffInsert() {
        final ContentValues after = new ContentValues();
        after.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        final ValuesDelta values = ValuesDelta.fromAfter(after);

        // Should produce an insert action
        final Builder builder = values.buildDiff(Data.CONTENT_URI);
        final int type = builder.build().getType();
        assertEquals("Didn't produce insert action", TYPE_INSERT, type);
    }

    public void testValuesDiffUpdate() {
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_PHONE_ID);
        before.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);

        final ValuesDelta values = ValuesDelta.fromBefore(before);
        values.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        // Should produce an update action
        final Builder builder = values.buildDiff(Data.CONTENT_URI);
        final int type = builder.build().getType();
        assertEquals("Didn't produce update action", TYPE_UPDATE, type);
    }

    public void testValuesDiffDelete() {
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_PHONE_ID);
        before.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);

        final ValuesDelta values = ValuesDelta.fromBefore(before);
        values.markDeleted();

        // Should produce a delete action
        final Builder builder = values.buildDiff(Data.CONTENT_URI);
        final int type = builder.build().getType();
        assertEquals("Didn't produce delete action", TYPE_DELETE, type);
    }

    /**
     * Test that {@link EntityDelta#buildDiff(ArrayList)} is correctly built for
     * insert, update, and delete cases. This only tests a subset of possible
     * {@link Data} row changes.
     */
    public void testEntityDiffNone() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);

        // Assert that writing unchanged produces few operations
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        source.buildDiff(diff);

        assertTrue("Created changes when none needed", (diff.size() == 0));
    }

    public void testEntityDiffNoneInsert() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(ValuesDelta.fromAfter(phone));

        // Assert two operations: insert Data row and enforce version
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        source.buildAssert(diff);
        source.buildDiff(diff);
        assertEquals("Unexpected operations", 4, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected version enforcement", TYPE_ASSERT, oper.getType());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(3);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffUpdateInsert() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);

        // Update parent contact values
        source.getValues().put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(ValuesDelta.fromAfter(phone));

        // Assert three operations: update Contact, insert Data row, enforce version
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        source.buildAssert(diff);
        source.buildDiff(diff);
        assertEquals("Unexpected operations", 5, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected version enforcement", TYPE_ASSERT, oper.getType());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Incorrect type", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(3);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(4);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffNoneUpdate() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);

        // Update existing phone number
        final ValuesDelta child = source.getEntry(TEST_PHONE_ID);
        child.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        // Assert that version is enforced
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        source.buildAssert(diff);
        source.buildDiff(diff);
        assertEquals("Unexpected operations", 4, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected version enforcement", TYPE_ASSERT, oper.getType());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Incorrect type", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(3);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffDelete() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final EntityDelta source = EntityDelta.fromBefore(before);

        // Delete entire entity
        source.getValues().markDeleted();

        // Assert two operations: delete Contact and enforce version
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        source.buildAssert(diff);
        source.buildDiff(diff);
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected version enforcement", TYPE_ASSERT, oper.getType());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Incorrect type", TYPE_DELETE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffInsert() {
        // Insert a RawContact
        final ContentValues after = new ContentValues();
        after.put(RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        after.put(RawContacts.SEND_TO_VOICEMAIL, 1);

        final ValuesDelta values = ValuesDelta.fromAfter(after);
        final EntityDelta source = new EntityDelta(values);

        // Assert two operations: delete Contact and enforce version
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        source.buildAssert(diff);
        source.buildDiff(diff);
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffInsertInsert() {
        // Insert a RawContact
        final ContentValues after = new ContentValues();
        after.put(RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        after.put(RawContacts.SEND_TO_VOICEMAIL, 1);

        final ValuesDelta values = ValuesDelta.fromAfter(after);
        final EntityDelta source = new EntityDelta(values);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(ValuesDelta.fromAfter(phone));

        // Assert two operations: delete Contact and enforce version
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        source.buildAssert(diff);
        source.buildDiff(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());

        }
    }
}
