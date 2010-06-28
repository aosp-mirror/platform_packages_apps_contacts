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

import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockContentProvider;
import com.android.contacts.widget.LoaderManagingFragmentTestDelegate;

import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.StatusUpdates;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.Smoke;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;


/**
 * Tests for {@link DefaultContactBrowseListFragment}.
 *
 * Running all tests:
 *
 *   runtest contacts
 * or
 *   adb shell am instrument \
 *     -w com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@Smoke
public class DefaultContactBrowseListFragmentTest
        extends InstrumentationTestCase {

    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;
    private MockContentProvider mSettingsProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
        mSettingsProvider = mContext.getSettingsProvider();
    }

    public void testDefaultMode() throws Exception {

        mSettingsProvider.expectQuery(Settings.System.CONTENT_URI)
                .withProjection(Settings.System.VALUE)
                .withSelection(Settings.System.NAME + "=?",
                        ContactsContract.Preferences.DISPLAY_ORDER);

        mSettingsProvider.expectQuery(Settings.System.CONTENT_URI)
                .withProjection(Settings.System.VALUE)
                .withSelection(Settings.System.NAME + "=?",
                        ContactsContract.Preferences.SORT_ORDER);

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
                        Contacts.CONTACT_PRESENCE,
                        Contacts.PHOTO_ID,
                        Contacts.LOOKUP_KEY,
                        Contacts.PHONETIC_NAME,
                        Contacts.HAS_PHONE_NUMBER)
                .withSelection(Contacts.IN_VISIBLE_GROUP + "=1")
                .withSortOrder(Contacts.SORT_KEY_PRIMARY)
                .returnRow(1, "John", "John", "john", 1,
                        StatusUpdates.AVAILABLE, 23, "lk1", "john", 1)
                .returnRow(2, "Jim", "Jim", "jim", 1,
                        StatusUpdates.AWAY, 24, "lk2", "jim", 0);

        mContactsProvider.expectQuery(ProviderStatus.CONTENT_URI)
                .withProjection(ProviderStatus.STATUS, ProviderStatus.DATA1);

        DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();

        LoaderManagingFragmentTestDelegate<Cursor> delegate =
                new LoaderManagingFragmentTestDelegate<Cursor>();

        // Divert loader registration to the delegate to ensure that loading is done synchronously
        fragment.setDelegate(delegate);

        // Fragment life cycle
        fragment.onCreate(null);

        // Instead of attaching the fragment to an activity, "attach" it to the target context
        // of the instrumentation
        fragment.setContext(mContext);

        // Fragment life cycle
        View view = fragment.onCreateView(LayoutInflater.from(mContext), null, null);

        // Fragment life cycle
        fragment.onStart();

        // All loaders have been registered. Now perform the loading synchronously.
        delegate.executeLoaders();

        // Now we can assert that the data got loaded into the list.
        ListView listView = (ListView)view.findViewById(android.R.id.list);
        ListAdapter adapter = listView.getAdapter();
        assertEquals(3, adapter.getCount());        // It has two items + header view

        // Assert that all queries have been called
        mSettingsProvider.verify();
        mContactsProvider.verify();
    }
}
