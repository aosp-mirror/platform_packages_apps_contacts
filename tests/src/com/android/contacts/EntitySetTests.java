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
import com.android.contacts.model.EntitySet;
import com.android.contacts.model.EntityDelta.ValuesDelta;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Entity;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.RawContacts;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

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

    public EntitySetTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
    }

    protected EntityDelta getUpdate(long rawContactId) {
        final Entity before = EntityDeltaTests.getEntity(rawContactId,
                EntityDeltaTests.TEST_PHONE_ID);
        return EntityDelta.fromBefore(before);
    }

    protected EntityDelta getInsert() {
        final ContentValues after = new ContentValues();
        after.put(RawContacts.ACCOUNT_NAME, EntityDeltaTests.TEST_ACCOUNT_NAME);
        after.put(RawContacts.SEND_TO_VOICEMAIL, 1);

        final ValuesDelta values = ValuesDelta.fromAfter(after);
        return new EntityDelta(values);
    }

    protected EntitySet setFrom(EntityDelta... deltas) {
        final EntitySet set = EntitySet.fromSingle(deltas[0]);
        for (int i = 1; i < deltas.length; i++) {
            set.add(deltas[i]);
        }
        return set;
    }

    /**
     * Count number of {@link AggregationExceptions} updates contained in the
     * given list of {@link ContentProviderOperation}.
     */
    protected int countExceptionUpdates(ArrayList<ContentProviderOperation> diff) {
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
        final EntitySet set = setFrom(insert);

        // Inserting single shouldn't create rules
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 0, exceptionCount);
    }

    public void testUpdateUpdate() {
        final EntityDelta updateFirst = getUpdate(CONTACT_FIRST);
        final EntityDelta updateSecond = getUpdate(CONTACT_SECOND);
        final EntitySet set = setFrom(updateFirst, updateSecond);

        // Updating two existing shouldn't create rules
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 0, exceptionCount);
    }

    public void testUpdateInsert() {
        final EntityDelta update = getUpdate(CONTACT_FIRST);
        final EntityDelta insert = getInsert();
        final EntitySet set = setFrom(update, insert);

        // New insert should only create one rule
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 1, exceptionCount);
    }

    public void testInsertUpdateInsert() {
        final EntityDelta insertFirst = getInsert();
        final EntityDelta update = getUpdate(CONTACT_FIRST);
        final EntityDelta insertSecond = getInsert();
        final EntitySet set = setFrom(insertFirst, update, insertSecond);

        // Two inserts should create two rules to bind against single existing
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 2, exceptionCount);
    }

    public void testInsertInsertInsert() {
        final EntityDelta insertFirst = getInsert();
        final EntityDelta insertSecond = getInsert();
        final EntityDelta insertThird = getInsert();
        final EntitySet set = setFrom(insertFirst, insertSecond, insertThird);

        // Three new inserts should create only two binding rules
        final ArrayList<ContentProviderOperation> diff = set.buildDiff();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 2, exceptionCount);
    }
}
