/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.interactions;

import com.android.contacts.R;
import com.android.contacts.model.Sources;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockContentProvider;
import com.android.contacts.tests.mocks.MockContentProvider.Query;
import com.android.contacts.tests.mocks.MockSources;

import android.content.AsyncTaskLoader;
import android.content.ContentUris;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.Smoke;

/**
 * Tests for {@link ContactDeletionInteraction}.
 *
 * Running all tests:
 *
 *   runtest contacts
 * or
 *   adb shell am instrument \
 *     -w com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@Smoke
public class ContactDeletionInteractionTest extends InstrumentationTestCase {

    private final static class TestContactDeletionInteraction extends ContactDeletionInteraction {
        public Uri contactUri;
        public int messageId;

        @Override
        void startLoading(Loader<Cursor> loader) {
            // Execute the loader synchronously
            AsyncTaskLoader<Cursor> atLoader = (AsyncTaskLoader<Cursor>)loader;
            Cursor data = atLoader.loadInBackground();
            atLoader.deliverResult(data);
        }

        @Override
        void showDialog(Bundle bundle) {
            contactUri = bundle.getParcelable(EXTRA_KEY_CONTACT_URI);
            messageId = bundle.getInt(EXTRA_KEY_MESSAGE_ID);
        }

        @Override
        Sources getSources() {
            return new MockSources();
        }
    }

    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
    }

    public void testSingleWritableRawContact() {
        expectQuery().returnRow(1, MockSources.WRITABLE_ACCOUNT_TYPE);
        assertWithMessageId(R.string.deleteConfirmation);
    }

    public void testReadOnlyRawContacts() {
        expectQuery().returnRow(1, MockSources.READONLY_ACCOUNT_TYPE);
        assertWithMessageId(R.string.readOnlyContactWarning);
    }

    public void testMixOfWritableAndReadOnlyRawContacts() {
        expectQuery()
                .returnRow(1, MockSources.WRITABLE_ACCOUNT_TYPE)
                .returnRow(2, MockSources.READONLY_ACCOUNT_TYPE);
        assertWithMessageId(R.string.readOnlyContactDeleteConfirmation);
    }

    public void testMultipleWritableRawContacts() {
        expectQuery()
                .returnRow(1, MockSources.WRITABLE_ACCOUNT_TYPE)
                .returnRow(2, MockSources.WRITABLE_ACCOUNT_TYPE);
        assertWithMessageId(R.string.multipleContactDeleteConfirmation);
    }

    private Query expectQuery() {
        return mContactsProvider.expectQuery(RawContacts.CONTENT_URI)
                .withProjection(RawContacts._ID, RawContacts.ACCOUNT_TYPE)
                .withSelection("contact_id=?", "13");
    }

    private void assertWithMessageId(int messageId) {
        TestContactDeletionInteraction interaction = new TestContactDeletionInteraction();
        interaction.setContext(mContext);
        interaction.deleteContact(ContentUris.withAppendedId(Contacts.CONTENT_URI, 13));
        assertEquals("content://com.android.contacts/contacts/13",
                interaction.contactUri.toString());
        assertEquals(messageId, interaction.messageId);
        mContactsProvider.verify();
    }
}
