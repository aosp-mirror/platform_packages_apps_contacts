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

import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockContentProvider;

import android.content.Intent;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.StatusUpdates;
import android.test.ActivityUnitTestCase;

/**
 * Tests for the contact list activity modes.
 *
 * Running all tests:
 *
 *   runtest contacts
 * or
 *   adb shell am instrument \
 *     -w com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
public class ContactListModeTest
        extends ActivityUnitTestCase<ContactsListActivity> {

    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;
    private MockContentProvider mSettingsProvider;

    public ContactListModeTest() {
        super(ContactsListActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
        mSettingsProvider = mContext.getSettingsProvider();
        setActivityContext(mContext);
    }

    public void testDefaultMode() throws Exception {
        mContactsProvider.expectQuery(ProviderStatus.CONTENT_URI)
                .withProjection(ProviderStatus.STATUS, ProviderStatus.DATA1);

        mSettingsProvider.expectQuery(Settings.System.CONTENT_URI)
                .withProjection(Settings.System.VALUE)
                .withSelection(Settings.System.NAME + "=?",
                        ContactsContract.Preferences.SORT_ORDER);

        mSettingsProvider.expectQuery(Settings.System.CONTENT_URI)
                .withProjection(Settings.System.VALUE)
                .withSelection(Settings.System.NAME + "=?",
                        ContactsContract.Preferences.DISPLAY_ORDER);

        mContactsProvider.expectQuery(
                Contacts.CONTENT_URI.buildUpon()
                        .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true")
                        .build())
                .withProjection(
                        Contacts._ID,
                        Contacts.DISPLAY_NAME,
                        Contacts.DISPLAY_NAME_ALTERNATIVE,
                        Contacts.SORT_KEY_PRIMARY,
                        Contacts.STARRED,
                        Contacts.TIMES_CONTACTED,
                        Contacts.CONTACT_PRESENCE,
                        Contacts.PHOTO_ID,
                        Contacts.LOOKUP_KEY,
                        Contacts.PHONETIC_NAME,
                        Contacts.HAS_PHONE_NUMBER)
                .withSelection(Contacts.IN_VISIBLE_GROUP + "=1")
                .withSortOrder(Contacts.SORT_KEY_PRIMARY)
                .returnRow(1, "John", "John", "john", 1, 10,
                        StatusUpdates.AVAILABLE, 23, "lk1", "john", 1)
                .returnRow(2, "Jim", "Jim", "jim", 1, 8,
                        StatusUpdates.AWAY, 24, "lk2", "jim", 0);

        Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent, null, null);
        ContactsListActivity activity = getActivity();
        activity.runQueriesSynchronously();
        activity.onResume();        // Trigger the queries

        assertEquals(3, activity.getListAdapter().getCount());
    }
}
