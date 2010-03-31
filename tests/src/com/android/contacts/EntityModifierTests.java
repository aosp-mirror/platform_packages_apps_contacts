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

import static android.content.ContentProviderOperation.TYPE_DELETE;
import static android.content.ContentProviderOperation.TYPE_INSERT;
import static android.content.ContentProviderOperation.TYPE_UPDATE;

import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.EntitySet;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.google.android.collect.Lists;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.os.Bundle;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link EntityModifier} to verify that {@link ContactsSource}
 * constraints are being enforced correctly.
 */
@LargeTest
public class EntityModifierTests extends AndroidTestCase {
    public static final String TAG = "EntityModifierTests";

    public static final long VER_FIRST = 100;

    private static final long TEST_ID = 4;
    private static final String TEST_PHONE = "218-555-1212";
    private static final String TEST_NAME = "Adam Young";
    private static final String TEST_NAME2 = "Breanne Duren";
    private static final String TEST_IM = "example@example.com";
    private static final String TEST_POSTAL = "1600 Amphitheatre Parkway";

    private static final String TEST_ACCOUNT_NAME = "unittest@example.com";
    private static final String TEST_ACCOUNT_TYPE = "com.example.unittest";

    public EntityModifierTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
    }

    public static class MockContactsSource extends ContactsSource {
        @Override
        protected void inflate(Context context, int inflateLevel) {
            this.accountType = TEST_ACCOUNT_TYPE;
            this.setInflatedLevel(ContactsSource.LEVEL_CONSTRAINTS);

            // Phone allows maximum 2 home, 1 work, and unlimited other, with
            // constraint of 5 numbers maximum.
            DataKind kind = new DataKind(Phone.CONTENT_ITEM_TYPE, -1, -1, 10, true);

            kind.typeOverallMax = 5;
            kind.typeColumn = Phone.TYPE;
            kind.typeList = Lists.newArrayList();
            kind.typeList.add(new EditType(Phone.TYPE_HOME, -1).setSpecificMax(2));
            kind.typeList.add(new EditType(Phone.TYPE_WORK, -1).setSpecificMax(1));
            kind.typeList.add(new EditType(Phone.TYPE_FAX_WORK, -1).setSecondary(true));
            kind.typeList.add(new EditType(Phone.TYPE_OTHER, -1));

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Phone.NUMBER, -1, -1));
            kind.fieldList.add(new EditField(Phone.LABEL, -1, -1));

            addKind(kind);

            // Email is unlimited
            kind = new DataKind(Email.CONTENT_ITEM_TYPE, -1, -1, 10, true);
            kind.typeOverallMax = -1;
            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Email.DATA, -1, -1));
            addKind(kind);

            // IM is only one
            kind = new DataKind(Im.CONTENT_ITEM_TYPE, -1, -1, 10, true);
            kind.typeOverallMax = 1;
            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Im.DATA, -1, -1));
            addKind(kind);

            // Organization is only one
            kind = new DataKind(Organization.CONTENT_ITEM_TYPE, -1, -1, 10, true);
            kind.typeOverallMax = 1;
            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Organization.COMPANY, -1, -1));
            kind.fieldList.add(new EditField(Organization.TITLE, -1, -1));
            addKind(kind);
        }

        @Override
        public int getHeaderColor(Context context) {
            return 0;
        }

        @Override
        public int getSideBarColor(Context context) {
            return 0xffffff;
        }
    }

    /**
     * Build a {@link ContactsSource} that has various odd constraints for
     * testing purposes.
     */
    protected ContactsSource getSource() {
        final ContactsSource source = new MockContactsSource();
        source.ensureInflated(getContext(), ContactsSource.LEVEL_CONSTRAINTS);
        return source;
    }

    /**
     * Build {@link Sources} instance.
     */
    protected Sources getSources(ContactsSource... sources) {
        return new Sources(sources);
    }

    /**
     * Build an {@link Entity} with the requested set of phone numbers.
     */
    protected EntityDelta getEntity(Long existingId, ContentValues... entries) {
        final ContentValues contact = new ContentValues();
        if (existingId != null) {
            contact.put(RawContacts._ID, existingId);
        }
        contact.put(RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        contact.put(RawContacts.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE);

        final Entity before = new Entity(contact);
        for (ContentValues values : entries) {
            before.addSubValue(Data.CONTENT_URI, values);
        }
        return EntityDelta.fromBefore(before);
    }

    /**
     * Assert this {@link List} contains the given {@link Object}.
     */
    protected void assertContains(List<?> list, Object object) {
        assertTrue("Missing expected value", list.contains(object));
    }

    /**
     * Assert this {@link List} does not contain the given {@link Object}.
     */
    protected void assertNotContains(List<?> list, Object object) {
        assertFalse("Contained unexpected value", list.contains(object));
    }

    /**
     * Insert various rows to test
     * {@link EntityModifier#getValidTypes(EntityDelta, DataKind, EditType)}
     */
    public void testValidTypes() {
        // Build a source and pull specific types
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);
        final EditType typeWork = EntityModifier.getType(kindPhone, Phone.TYPE_WORK);
        final EditType typeOther = EntityModifier.getType(kindPhone, Phone.TYPE_OTHER);

        List<EditType> validTypes;

        // Add first home, first work
        final EntityDelta state = getEntity(TEST_ID);
        EntityModifier.insertChild(state, kindPhone, typeHome);
        EntityModifier.insertChild(state, kindPhone, typeWork);

        // Expecting home, other
        validTypes = EntityModifier.getValidTypes(state, kindPhone, null);
        assertContains(validTypes, typeHome);
        assertNotContains(validTypes, typeWork);
        assertContains(validTypes, typeOther);

        // Add second home
        EntityModifier.insertChild(state, kindPhone, typeHome);

        // Expecting other
        validTypes = EntityModifier.getValidTypes(state, kindPhone, null);
        assertNotContains(validTypes, typeHome);
        assertNotContains(validTypes, typeWork);
        assertContains(validTypes, typeOther);

        // Add third and fourth home (invalid, but possible)
        EntityModifier.insertChild(state, kindPhone, typeHome);
        EntityModifier.insertChild(state, kindPhone, typeHome);

        // Expecting none
        validTypes = EntityModifier.getValidTypes(state, kindPhone, null);
        assertNotContains(validTypes, typeHome);
        assertNotContains(validTypes, typeWork);
        assertNotContains(validTypes, typeOther);
    }

    /**
     * Test {@link EntityModifier#canInsert(EntityDelta, DataKind)} by
     * inserting various rows.
     */
    public void testCanInsert() {
        // Build a source and pull specific types
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);
        final EditType typeWork = EntityModifier.getType(kindPhone, Phone.TYPE_WORK);
        final EditType typeOther = EntityModifier.getType(kindPhone, Phone.TYPE_OTHER);

        // Add first home, first work
        final EntityDelta state = getEntity(TEST_ID);
        EntityModifier.insertChild(state, kindPhone, typeHome);
        EntityModifier.insertChild(state, kindPhone, typeWork);
        assertTrue("Unable to insert", EntityModifier.canInsert(state, kindPhone));

        // Add two other, which puts us just under "5" overall limit
        EntityModifier.insertChild(state, kindPhone, typeOther);
        EntityModifier.insertChild(state, kindPhone, typeOther);
        assertTrue("Unable to insert", EntityModifier.canInsert(state, kindPhone));

        // Add second home, which should push to snug limit
        EntityModifier.insertChild(state, kindPhone, typeHome);
        assertFalse("Able to insert", EntityModifier.canInsert(state, kindPhone));
    }

    /**
     * Test
     * {@link EntityModifier#getBestValidType(EntityDelta, DataKind, boolean, int)}
     * by asserting expected best options in various states.
     */
    public void testBestValidType() {
        // Build a source and pull specific types
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);
        final EditType typeWork = EntityModifier.getType(kindPhone, Phone.TYPE_WORK);
        final EditType typeFaxWork = EntityModifier.getType(kindPhone, Phone.TYPE_FAX_WORK);
        final EditType typeOther = EntityModifier.getType(kindPhone, Phone.TYPE_OTHER);

        EditType suggested;

        // Default suggestion should be home
        final EntityDelta state = getEntity(TEST_ID);
        suggested = EntityModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeHome, suggested);

        // Add first home, should now suggest work
        EntityModifier.insertChild(state, kindPhone, typeHome);
        suggested = EntityModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeWork, suggested);

        // Add work fax, should still suggest work
        EntityModifier.insertChild(state, kindPhone, typeFaxWork);
        suggested = EntityModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeWork, suggested);

        // Add other, should still suggest work
        EntityModifier.insertChild(state, kindPhone, typeOther);
        suggested = EntityModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeWork, suggested);

        // Add work, now should suggest other
        EntityModifier.insertChild(state, kindPhone, typeWork);
        suggested = EntityModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeOther, suggested);
    }

    public void testIsEmptyEmpty() {
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);

        // Test entirely empty row
        final ContentValues after = new ContentValues();
        final ValuesDelta values = ValuesDelta.fromAfter(after);

        assertTrue("Expected empty", EntityModifier.isEmpty(values, kindPhone));
    }

    public void testIsEmptyDirectFields() {
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Test row that has type values, but core fields are empty
        final EntityDelta state = getEntity(TEST_ID);
        final ValuesDelta values = EntityModifier.insertChild(state, kindPhone, typeHome);

        assertTrue("Expected empty", EntityModifier.isEmpty(values, kindPhone));

        // Insert some data to trigger non-empty state
        values.put(Phone.NUMBER, TEST_PHONE);

        assertFalse("Expected non-empty", EntityModifier.isEmpty(values, kindPhone));
    }

    public void testTrimEmptySingle() {
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Test row that has type values, but core fields are empty
        final EntityDelta state = getEntity(TEST_ID);
        final ValuesDelta values = EntityModifier.insertChild(state, kindPhone, typeHome);

        // Build diff, expecting insert for data row and update enforcement
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Trim empty rows and try again, expecting delete of overall contact
        EntityModifier.trimEmpty(state, source);
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 1, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_DELETE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testTrimEmptySpaces() {
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Test row that has type values, but values are spaces
        final EntityDelta state = EntitySetTests.buildBeforeEntity(TEST_ID, VER_FIRST);
        final ValuesDelta values = EntityModifier.insertChild(state, kindPhone, typeHome);
        values.put(Phone.NUMBER, "   ");

        // Build diff, expecting insert for data row and update enforcement
        EntitySetTests.assertDiffPattern(state,
                EntitySetTests.buildAssertVersion(VER_FIRST),
                EntitySetTests.buildUpdateAggregationSuspended(),
                EntitySetTests.buildOper(Data.CONTENT_URI, TYPE_INSERT,
                        EntitySetTests.buildDataInsert(values, TEST_ID)),
                EntitySetTests.buildUpdateAggregationDefault());

        // Trim empty rows and try again, expecting delete of overall contact
        EntityModifier.trimEmpty(state, source);
        EntitySetTests.assertDiffPattern(state,
                EntitySetTests.buildAssertVersion(VER_FIRST),
                EntitySetTests.buildDelete(RawContacts.CONTENT_URI));
    }

    public void testTrimLeaveValid() {
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Test row that has type values with valid number
        final EntityDelta state = EntitySetTests.buildBeforeEntity(TEST_ID, VER_FIRST);
        final ValuesDelta values = EntityModifier.insertChild(state, kindPhone, typeHome);
        values.put(Phone.NUMBER, TEST_PHONE);

        // Build diff, expecting insert for data row and update enforcement
        EntitySetTests.assertDiffPattern(state,
                EntitySetTests.buildAssertVersion(VER_FIRST),
                EntitySetTests.buildUpdateAggregationSuspended(),
                EntitySetTests.buildOper(Data.CONTENT_URI, TYPE_INSERT,
                        EntitySetTests.buildDataInsert(values, TEST_ID)),
                EntitySetTests.buildUpdateAggregationDefault());

        // Trim empty rows and try again, expecting no differences
        EntityModifier.trimEmpty(state, source);
        EntitySetTests.assertDiffPattern(state,
                EntitySetTests.buildAssertVersion(VER_FIRST),
                EntitySetTests.buildUpdateAggregationSuspended(),
                EntitySetTests.buildOper(Data.CONTENT_URI, TYPE_INSERT,
                        EntitySetTests.buildDataInsert(values, TEST_ID)),
                EntitySetTests.buildUpdateAggregationDefault());
    }

    public void testTrimEmptyUntouched() {
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Build "before" that has empty row
        final EntityDelta state = getEntity(TEST_ID);
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_ID);
        before.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        state.addEntry(ValuesDelta.fromBefore(before));

        // Build diff, expecting no changes
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 0, diff.size());

        // Try trimming existing empty, which we shouldn't touch
        EntityModifier.trimEmpty(state, source);
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 0, diff.size());
    }

    public void testTrimEmptyAfterUpdate() {
        final ContactsSource source = getSource();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Build "before" that has row with some phone number
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_ID);
        before.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        before.put(kindPhone.typeColumn, typeHome.rawValue);
        before.put(Phone.NUMBER, TEST_PHONE);
        final EntityDelta state = getEntity(TEST_ID, before);

        // Build diff, expecting no changes
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 0, diff.size());

        // Now update row by changing number to empty string, expecting single update
        final ValuesDelta child = state.getEntry(TEST_ID);
        child.put(Phone.NUMBER, "");
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Incorrect type", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Now run trim, which should turn that update into delete
        EntityModifier.trimEmpty(state, source);
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 1, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_DELETE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testTrimInsertEmpty() {
        final ContactsSource source = getSource();
        final Sources sources = getSources(source);
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Try creating a contact without any child entries
        final EntityDelta state = getEntity(null);
        final EntitySet set = EntitySet.fromSingle(state);

        // Build diff, expecting single insert
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_INSERT, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Trim empty rows and try again, expecting no insert
        EntityModifier.trimEmpty(set, sources);
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 0, diff.size());
    }

    public void testTrimInsertInsert() {
        final ContactsSource source = getSource();
        final Sources sources = getSources(source);
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Try creating a contact with single empty entry
        final EntityDelta state = getEntity(null);
        final ValuesDelta values = EntityModifier.insertChild(state, kindPhone, typeHome);
        final EntitySet set = EntitySet.fromSingle(state);

        // Build diff, expecting two insert operations
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        state.buildDiff(diff);
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

        // Trim empty rows and try again, expecting silence
        EntityModifier.trimEmpty(set, sources);
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 0, diff.size());
    }

    public void testTrimUpdateRemain() {
        final ContactsSource source = getSource();
        final Sources sources = getSources(source);
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Build "before" with two phone numbers
        final ContentValues first = new ContentValues();
        first.put(Data._ID, TEST_ID);
        first.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        first.put(kindPhone.typeColumn, typeHome.rawValue);
        first.put(Phone.NUMBER, TEST_PHONE);

        final ContentValues second = new ContentValues();
        second.put(Data._ID, TEST_ID);
        second.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        second.put(kindPhone.typeColumn, typeHome.rawValue);
        second.put(Phone.NUMBER, TEST_PHONE);

        final EntityDelta state = getEntity(TEST_ID, first, second);
        final EntitySet set = EntitySet.fromSingle(state);

        // Build diff, expecting no changes
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 0, diff.size());

        // Now update row by changing number to empty string, expecting single update
        final ValuesDelta child = state.getEntry(TEST_ID);
        child.put(Phone.NUMBER, "");
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Incorrect type", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Now run trim, which should turn that update into delete
        EntityModifier.trimEmpty(set, sources);
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Incorrect type", TYPE_DELETE, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testTrimUpdateUpdate() {
        final ContactsSource source = getSource();
        final Sources sources = getSources(source);
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = EntityModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Build "before" with two phone numbers
        final ContentValues first = new ContentValues();
        first.put(Data._ID, TEST_ID);
        first.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        first.put(kindPhone.typeColumn, typeHome.rawValue);
        first.put(Phone.NUMBER, TEST_PHONE);

        final EntityDelta state = getEntity(TEST_ID, first);
        final EntitySet set = EntitySet.fromSingle(state);

        // Build diff, expecting no changes
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 0, diff.size());

        // Now update row by changing number to empty string, expecting single update
        final ValuesDelta child = state.getEntry(TEST_ID);
        child.put(Phone.NUMBER, "");
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(1);
            assertEquals("Incorrect type", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final ContentProviderOperation oper = diff.get(2);
            assertEquals("Expected aggregation mode change", TYPE_UPDATE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Now run trim, which should turn into deleting the whole contact
        EntityModifier.trimEmpty(set, sources);
        diff.clear();
        state.buildDiff(diff);
        assertEquals("Unexpected operations", 1, diff.size());
        {
            final ContentProviderOperation oper = diff.get(0);
            assertEquals("Incorrect type", TYPE_DELETE, oper.getType());
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testParseExtrasExistingName() {
        final ContactsSource source = getSource();
        final DataKind kindName = source.getKindForMimetype(StructuredName.CONTENT_ITEM_TYPE);

        // Build "before" name
        final ContentValues first = new ContentValues();
        first.put(Data._ID, TEST_ID);
        first.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        first.put(StructuredName.GIVEN_NAME, TEST_NAME);

        // Parse extras, making sure we keep single name
        final EntityDelta state = getEntity(TEST_ID, first);
        final Bundle extras = new Bundle();
        extras.putString(Insert.NAME, TEST_NAME2);
        EntityModifier.parseExtras(mContext, source, state, extras);

        final int nameCount = state.getMimeEntriesCount(StructuredName.CONTENT_ITEM_TYPE, true);
        assertEquals("Unexpected names", 1, nameCount);
    }

    public void testParseExtrasIgnoreLimit() {
        final ContactsSource source = getSource();
        final DataKind kindIm = source.getKindForMimetype(Im.CONTENT_ITEM_TYPE);

        // Build "before" IM
        final ContentValues first = new ContentValues();
        first.put(Data._ID, TEST_ID);
        first.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        first.put(Im.DATA, TEST_IM);

        final EntityDelta state = getEntity(TEST_ID, first);
        final int beforeCount = state.getMimeEntries(Im.CONTENT_ITEM_TYPE).size();

        // We should ignore data that doesn't fit source rules, since source
        // only allows single Im
        final Bundle extras = new Bundle();
        extras.putInt(Insert.IM_PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        extras.putString(Insert.IM_HANDLE, TEST_IM);
        EntityModifier.parseExtras(mContext, source, state, extras);

        final int afterCount = state.getMimeEntries(Im.CONTENT_ITEM_TYPE).size();
        assertEquals("Broke source rules", beforeCount, afterCount);
    }

    public void testParseExtrasIgnoreUnhandled() {
        final ContactsSource source = getSource();
        final EntityDelta state = getEntity(TEST_ID);

        // We should silently ignore types unsupported by source
        final Bundle extras = new Bundle();
        extras.putString(Insert.POSTAL, TEST_POSTAL);
        EntityModifier.parseExtras(mContext, source, state, extras);

        assertNull("Broke source rules", state.getMimeEntries(StructuredPostal.CONTENT_ITEM_TYPE));
    }

    public void testParseExtrasJobTitle() {
        final ContactsSource source = getSource();
        final EntityDelta state = getEntity(TEST_ID);

        // Make sure that we create partial Organizations
        final Bundle extras = new Bundle();
        extras.putString(Insert.JOB_TITLE, TEST_NAME);
        EntityModifier.parseExtras(mContext, source, state, extras);

        final int count = state.getMimeEntries(Organization.CONTENT_ITEM_TYPE).size();
        assertEquals("Expected to create organization", 1, count);
    }
}
