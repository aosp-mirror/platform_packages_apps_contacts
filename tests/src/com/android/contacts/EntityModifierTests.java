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
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditType;
import com.google.android.collect.Lists;

import android.content.ContentValues;
import android.content.Entity;
import android.provider.ContactsContract.CommonDataKinds.Phone;
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

    public EntityModifierTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
    }

    /**
     * Build a {@link ContactsSource} that has various odd constraints for
     * testing purposes.
     */
    protected ContactsSource getSource() {
        final ContactsSource list = new ContactsSource();

        {
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

            list.add(kind);
        }

        return list;
    }

    /**
     * Build an {@link Entity} with the requested set of phone numbers.
     */
    protected EntityDelta getEntity() {
        final ContentValues contact = new ContentValues();
        final Entity before = new Entity(contact);
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
        final EntityDelta state = getEntity();
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
        final EntityDelta state = getEntity();
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
        final EntityDelta state = getEntity();
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
}
