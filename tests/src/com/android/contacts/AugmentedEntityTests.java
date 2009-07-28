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

import com.android.contacts.model.AugmentedEntity;
import com.android.contacts.model.AugmentedEntity.AugmentedValues;

import static android.content.ContentProviderOperation.TYPE_INSERT;
import static android.content.ContentProviderOperation.TYPE_UPDATE;
import static android.content.ContentProviderOperation.TYPE_DELETE;
import static android.content.ContentProviderOperation.TYPE_COUNT;

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
 * Tests for {@link AugmentedEntity} and {@link AugmentedValues}. These tests
 * focus on passing changes across {@link Parcel}, and verifying that they
 * correctly build expected "diff" operations.
 */
@LargeTest
public class AugmentedEntityTests extends AndroidTestCase {
    public static final String TAG = "AugmentedEntityTests";

    private static final long TEST_CONTACT_ID = 12;
    private static final long TEST_PHONE_ID = 24;

    private static final String TEST_PHONE_NUMBER_1 = "218-555-1111";
    private static final String TEST_PHONE_NUMBER_2 = "218-555-2222";

    private static final String TEST_ACCOUNT_NAME = "TEST";

    public AugmentedEntityTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
    }

    public Entity getEntity(long contactId, long phoneId) {
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
     * Test that {@link AugmentedEntity#augmentTo(Parcel)} correctly passes any
     * changes through the {@link Parcel} object. This enforces that
     * {@link AugmentedEntity} should be identical when serialized against the
     * same "before" {@link Entity}.
     */
    public void testParcelChangesNone() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);

        // No changes, should be same
        final Parcel parcel = Parcel.obtain();
        source.augmentTo(parcel);

        final AugmentedEntity dest = AugmentedEntity.fromBefore(before);
        parcel.setDataPosition(0);
        dest.augmentFrom(parcel);

        // Assert that we have same data rows
        assertEquals("Changed when passing through Parcel", source, dest);
        parcel.recycle();
    }

    public void testParcelChangesInsert() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);

        // Add a new row and pass across parcel, should be same
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.MIMETYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(AugmentedValues.fromAfter(phone));

        final Parcel parcel = Parcel.obtain();
        source.augmentTo(parcel);

        final AugmentedEntity dest = AugmentedEntity.fromBefore(before);
        parcel.setDataPosition(0);
        dest.augmentFrom(parcel);

        // Assert that we have same data rows
        assertEquals("Changed when passing through Parcel", source, dest);
        parcel.recycle();
    }

    public void testParcelChangesUpdate() {
        // Update existing row and pass across parcel, should be same
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);
        final AugmentedValues child = source.getEntry(TEST_PHONE_ID);
        child.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        final Parcel parcel = Parcel.obtain();
        source.augmentTo(parcel);

        final AugmentedEntity dest = AugmentedEntity.fromBefore(before);
        parcel.setDataPosition(0);
        dest.augmentFrom(parcel);

        // Assert that we have same data rows
        assertEquals("Changed when passing through Parcel", source, dest);
        parcel.recycle();
    }

    public void testParcelChangesDelete() {
        // Delete a row and pass across parcel, should be same
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);
        final AugmentedValues child = source.getEntry(TEST_PHONE_ID);
        child.markDeleted();

        final Parcel parcel = Parcel.obtain();
        source.augmentTo(parcel);

        final AugmentedEntity dest = AugmentedEntity.fromBefore(before);
        parcel.setDataPosition(0);
        dest.augmentFrom(parcel);

        // Assert that we have same data rows
        assertEquals("Changed when passing through Parcel", source, dest);
        parcel.recycle();
    }

    /**
     * Test that {@link AugmentedValues#buildDiff()} is correctly built for
     * insert, update, and delete cases. Note this only tests behavior for
     * individual {@link Data} rows.
     */
    public void testValuesDiffNone() {
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_PHONE_ID);
        before.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);

        final AugmentedValues values = AugmentedValues.fromBefore(before);

        // None action shouldn't produce a builder
        final Builder builder = values.buildDiff(Data.CONTENT_URI);
        assertNull("None action produced a builder", builder);
    }

    public void testValuesDiffInsert() {
        final ContentValues after = new ContentValues();
        after.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        final AugmentedValues values = AugmentedValues.fromAfter(after);

        // Should produce an insert action
        final Builder builder = values.buildDiff(Data.CONTENT_URI);
        final int type = builder.build().getType();
        assertEquals("Didn't produce insert action", TYPE_INSERT, type);
    }

    public void testValuesDiffUpdate() {
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_PHONE_ID);
        before.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);

        final AugmentedValues values = AugmentedValues.fromBefore(before);
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

        final AugmentedValues values = AugmentedValues.fromBefore(before);
        values.markDeleted();

        // Should produce a delete action
        final Builder builder = values.buildDiff(Data.CONTENT_URI);
        final int type = builder.build().getType();
        assertEquals("Didn't produce delete action", TYPE_DELETE, type);
    }

    /**
     * Test that {@link AugmentedEntity#buildDiff()} is correctly built for
     * insert, update, and delete cases. This only tests a subset of possible
     * {@link Data} row changes.
     */
    public void testEntityDiffNone() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);

        // Assert that writing unchanged produces few operations
        final ArrayList<ContentProviderOperation> diff = source.buildDiff();

        assertTrue("Created changes when none needed", (diff.size() == 0));
    }

    public void testEntityDiffNoneInsert() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.MIMETYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(AugmentedValues.fromAfter(phone));

        // Assert two operations: insert Data row and enforce version
        final ArrayList<ContentProviderOperation> diff = source.buildDiff();
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Expected version enforcement", TYPE_COUNT, oper.getType());
        }
    }

    public void testEntityDiffUpdateInsert() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);

        // Update parent contact values
        source.getValues().put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.MIMETYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(AugmentedValues.fromAfter(phone));

        // Assert three operations: update Contact, insert Data row, enforce version
        final ArrayList<ContentProviderOperation> diff = source.buildDiff();
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Expected version enforcement", TYPE_COUNT, oper.getType());
        }
    }

    public void testEntityDiffNoneUpdate() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);

        // Update existing phone number
        final AugmentedValues child = source.getEntry(TEST_PHONE_ID);
        child.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        // Assert two operations: update Data and enforce version
        final ArrayList<ContentProviderOperation> diff = source.buildDiff();
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Expected version enforcement", TYPE_COUNT, oper.getType());
        }
    }

    public void testEntityDiffDelete() {
        final Entity before = getEntity(TEST_CONTACT_ID, TEST_PHONE_ID);
        final AugmentedEntity source = AugmentedEntity.fromBefore(before);

        // Delete entire entity
        source.getValues().markDeleted();

        // Assert two operations: delete Contact and enforce version
        final ArrayList<ContentProviderOperation> diff = source.buildDiff();
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_DELETE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Expected version enforcement", TYPE_COUNT, oper.getType());
        }
    }

    public void testEntityDiffInsert() {
        // Insert a RawContact
        final ContentValues after = new ContentValues();
        after.put(RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        after.put(RawContacts.SEND_TO_VOICEMAIL, 1);

        final AugmentedValues values = AugmentedValues.fromAfter(after);
        final AugmentedEntity source = new AugmentedEntity(values);

        // Assert two operations: delete Contact and enforce version
        final ArrayList<ContentProviderOperation> diff = source.buildDiff();
        assertEquals("Unexpected operations", 1, diff.size());
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

        final AugmentedValues values = AugmentedValues.fromAfter(after);
        final AugmentedEntity source = new AugmentedEntity(values);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.MIMETYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(AugmentedValues.fromAfter(phone));

        // Assert two operations: delete Contact and enforce version
        final ArrayList<ContentProviderOperation> diff = source.buildDiff();
        assertEquals("Unexpected operations", 2, diff.size());
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
