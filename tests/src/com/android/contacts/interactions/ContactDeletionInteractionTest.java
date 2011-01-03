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
import com.android.contacts.model.AccountTypes;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockAccountTypes;
import com.android.contacts.tests.mocks.MockContentProvider;
import com.android.contacts.tests.mocks.MockContentProvider.Query;
import com.android.contacts.widget.TestLoaderManager;

import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Entity;
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

    private final class TestContactDeletionDialogFragment
            extends ContactDeletionInteraction {
        public Uri contactUri;
        public int messageId;

        @Override
        public LoaderManager getLoaderManager() {
            return mLoaderManager;
        }

        @Override
        boolean isStarted() {
            return true;
        }

        @Override
        void showDialog(int messageId, Uri contactUri) {
            this.messageId = messageId;
            this.contactUri = contactUri;
        }

        @Override
        AccountTypes getAccountTypes() {
            return new MockAccountTypes();
        }
    }

    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;
    private TestLoaderManager mLoaderManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = ContactsContract.AUTHORITY;
        mContactsProvider.attachInfo(mContext, info);
        mLoaderManager = new TestLoaderManager();
    }

    public void testSingleWritableRawContact() {
        expectQuery().returnRow(1, MockAccountTypes.WRITABLE_ACCOUNT_TYPE, 13, "foo");
        assertWithMessageId(R.string.deleteConfirmation);
    }

    public void testReadOnlyRawContacts() {
        expectQuery().returnRow(1, MockAccountTypes.READONLY_ACCOUNT_TYPE, 13, "foo");
        assertWithMessageId(R.string.readOnlyContactWarning);
    }

    public void testMixOfWritableAndReadOnlyRawContacts() {
        expectQuery()
                .returnRow(1, MockAccountTypes.WRITABLE_ACCOUNT_TYPE, 13, "foo")
                .returnRow(2, MockAccountTypes.READONLY_ACCOUNT_TYPE, 13, "foo");
        assertWithMessageId(R.string.readOnlyContactDeleteConfirmation);
    }

    public void testMultipleWritableRawContacts() {
        expectQuery()
                .returnRow(1, MockAccountTypes.WRITABLE_ACCOUNT_TYPE, 13, "foo")
                .returnRow(2, MockAccountTypes.WRITABLE_ACCOUNT_TYPE, 13, "foo");
        assertWithMessageId(R.string.multipleContactDeleteConfirmation);
    }

    private Query expectQuery() {
        Uri uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, 13), Entity.CONTENT_DIRECTORY);
        return mContactsProvider.expectQuery(uri).withProjection(
                Entity.RAW_CONTACT_ID, Entity.ACCOUNT_TYPE, Entity.CONTACT_ID, Entity.LOOKUP_KEY);
    }

    private void assertWithMessageId(int messageId) {
        TestContactDeletionDialogFragment interaction = new TestContactDeletionDialogFragment();
        interaction.setContext(mContext);
        interaction.setContactUri(ContentUris.withAppendedId(Contacts.CONTENT_URI, 13));
        mLoaderManager.executeLoaders();
        assertEquals("content://com.android.contacts/contacts/lookup/foo/13",
                interaction.contactUri.toString());
        assertEquals(messageId, interaction.messageId);
        mContactsProvider.verify();
    }
}
