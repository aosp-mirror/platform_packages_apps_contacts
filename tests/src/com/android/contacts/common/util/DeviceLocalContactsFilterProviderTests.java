/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.common.util;

import android.content.ContentProvider;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.Nullable;
import android.test.LoaderTestCase;
import android.test.mock.MockContentResolver;
import android.test.mock.MockContext;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.test.mocks.MockContentProvider;

import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.when;

@SmallTest
public class DeviceLocalContactsFilterProviderTests extends LoaderTestCase {

    // Basic smoke test that just checks that it doesn't throw when loading from CP2. We don't
    // care what CP2 actually contains for this.
    public void testShouldNotCrash() {
        final DeviceLocalContactsFilterProvider sut = new DeviceLocalContactsFilterProvider(
                getContext(), DeviceAccountFilter.ONLY_NULL);
        final CursorLoader loader = sut.onCreateLoader(0, null);
        getLoaderResultSynchronously(loader);
        // We didn't throw so it passed
    }

    public void testCreatesNoFiltersIfNoRawContactsHaveDeviceAccountType() {
        final DeviceLocalContactsFilterProvider sut = createWithFilterAndLoaderResult(
                DeviceAccountFilter.ONLY_NULL, queryResult(
                        "user", "com.example",
                        "user", "com.example",
                        "user", "com.example"));
        sut.setKnownAccountTypes("com.example");

        doLoad(sut);

        assertEquals(0, sut.getListFilters().size());
    }

    public void testCreatesOneFilterForDeviceAccount() {
        final DeviceLocalContactsFilterProvider sut = createWithFilterAndLoaderResult(
                DeviceAccountFilter.ONLY_NULL, queryResult(
                        "user", "com.example",
                        "user", "com.example",
                        null, null,
                        "user", "com.example",
                        null, null));
        sut.setKnownAccountTypes("com.example");

        doLoad(sut);

        assertEquals(1, sut.getListFilters().size());
        assertEquals(ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS,
                sut.getListFilters().get(0).filterType);
    }

    public void testCreatesOneFilterForEachDeviceAccount() {
         final DeviceLocalContactsFilterProvider sut = createWithFilterAndLoaderResult(
                 filterAllowing(null, "vnd.sec.contact.phone", "vnd.sec.contact.sim"), queryResult(
                         "sim_account", "vnd.sec.contact.sim",
                         "user", "com.example",
                         "user", "com.example",
                         "phone_account", "vnd.sec.contact.phone",
                         null, null,
                         "phone_account", "vnd.sec.contact.phone",
                         "user", "com.example",
                         null, null,
                         "sim_account", "vnd.sec.contact.sim",
                         "sim_account_2", "vnd.sec.contact.sim"
                 ));
        sut.setKnownAccountTypes("com.example");

        doLoad(sut);

        assertEquals(4, sut.getListFilters().size());
    }

    public void testFilterIsUpdatedWhenLoaderReloads() {
        final FakeContactsProvider provider = new FakeContactsProvider();
        final DeviceLocalContactsFilterProvider sut = new DeviceLocalContactsFilterProvider(
                createStubContextWithContactsProvider(provider), DeviceAccountFilter.ONLY_NULL);
        sut.setKnownAccountTypes("com.example");

        provider.setNextQueryResult(queryResult(
                null, null,
                "user", "com.example",
                "user", "com.example"
        ));
        doLoad(sut);

        assertFalse(sut.getListFilters().isEmpty());

        provider.setNextQueryResult(queryResult(
                "user", "com.example",
                "user", "com.example"
        ));
        doLoad(sut);

        assertTrue(sut.getListFilters().isEmpty());
    }

    public void testDoesNotCreateFiltersForKnownAccounts() {
        final DeviceLocalContactsFilterProvider sut = new DeviceLocalContactsFilterProvider(
                getContext(), DeviceAccountFilter.ONLY_NULL);
        sut.setKnownAccountTypes("com.example", "maybe_syncable_device_account_type");

        final CursorLoader loader = sut.onCreateLoader(0, null);

        // The filtering is done at the DB level rather than in the code so just verify that
        // selection is about right.
        assertTrue("Loader selection is wrong", loader.getSelection().contains("NOT IN (?,?)"));
        assertEquals("com.example", loader.getSelectionArgs()[0]);
        assertEquals("maybe_syncable_device_account_type", loader.getSelectionArgs()[1]);
    }

    private void doLoad(DeviceLocalContactsFilterProvider loaderCallbacks) {
        final CursorLoader loader = loaderCallbacks.onCreateLoader(0, null);
        final Cursor cursor = getLoaderResultSynchronously(loader);
        loaderCallbacks.onLoadFinished(loader, cursor);
    }

    private DeviceLocalContactsFilterProvider createWithFilterAndLoaderResult(
            DeviceAccountFilter filter, Cursor cursor) {
        final DeviceLocalContactsFilterProvider result = new DeviceLocalContactsFilterProvider(
                createStubContextWithContentQueryResult(cursor), filter);
        return result;
    }

    private Context createStubContextWithContentQueryResult(final Cursor cursor) {
        return createStubContextWithContactsProvider(new FakeContactsProvider(cursor));
    }

    private Context createStubContextWithContactsProvider(ContentProvider contactsProvider) {
        final MockContentResolver resolver = new MockContentResolver();
        resolver.addProvider(ContactsContract.AUTHORITY, contactsProvider);

        final Context context = Mockito.mock(MockContext.class);
        when(context.getContentResolver()).thenReturn(resolver);

        // The loader pulls out the application context instead of usign the context directly
        when(context.getApplicationContext()).thenReturn(context);

        return context;
    }

    private Cursor queryResult(String... typeNamePairs) {
        final MatrixCursor cursor = new MatrixCursor(new String[]
                { RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE });
        for (int i = 0; i < typeNamePairs.length; i += 2) {
            cursor.newRow().add(typeNamePairs[i]).add(typeNamePairs[i+1]);
        }
        return cursor;
    }

    private DeviceAccountFilter filterAllowing(String... accountTypes) {
        final Set<String> allowed = new HashSet<>(Arrays.asList(accountTypes));
        return new DeviceAccountFilter() {
            @Override
            public boolean isDeviceAccountType(String accountType) {
                return allowed.contains(accountType);
            }
        };
    }

    private static class FakeContactsProvider extends MockContentProvider {
        public Cursor mNextQueryResult;

        public FakeContactsProvider() {}

        public FakeContactsProvider(Cursor result) {
            mNextQueryResult = result;
        }

        public void setNextQueryResult(Cursor result) {
            mNextQueryResult = result;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return query(uri, projection, selection, selectionArgs, sortOrder, null);
        }

        @Nullable
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder, CancellationSignal cancellationSignal) {
            return mNextQueryResult;
        }
    }
}
