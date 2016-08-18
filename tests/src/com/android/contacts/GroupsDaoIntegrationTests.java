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

package com.android.contacts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Data;
import android.test.InstrumentationTestCase;

import com.android.contacts.common.model.account.AccountWithDataSet;

/**
 * Tests of GroupsDaoImpl that perform DB operations directly against CP2
 */
public class GroupsDaoIntegrationTests extends InstrumentationTestCase {

    private Account mAccount;
    private ContentResolver cr;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAccount = new Account(getClass().getSimpleName() + "_t" +
                System.currentTimeMillis(), "com.android.contacts.tests.authtest.basic");
        AccountManager accountManager = (AccountManager) getContext()
                .getSystemService(Context.ACCOUNT_SERVICE);
        accountManager.addAccountExplicitly(mAccount, null, null);
        cr = getContext().getContentResolver();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // Cleanup anything leftover by the tests.
        // the ACCOUNT_NAME should be unique because it contains a timestamp
        final Uri groupsUri = ContactsContract.Groups.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
        final Uri rawContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
        getContext().getContentResolver().delete(groupsUri,
                ContactsContract.Groups.ACCOUNT_NAME + "=?", new String[] { mAccount.name });
        getContext().getContentResolver().delete(rawContactsUri,
                ContactsContract.RawContacts.ACCOUNT_NAME + "=?", new String[] { mAccount.name });

        if (mAccount != null) {
            AccountManager accountManager = (AccountManager) getContext()
                    .getSystemService(Context.ACCOUNT_SERVICE);
            accountManager.removeAccountExplicitly(mAccount);
            mAccount = null;
        }
    }

    public void test_createGroup_createsGroupWithCorrectTitle() throws Exception {
        ContactSaveService.GroupsDaoImpl sut = createDao();
        Uri uri = sut.create("Test Create Group", getTestAccount());

        assertNotNull(uri);
        assertGroupHasTitle(uri, "Test Create Group");
    }

    public void test_deleteEmptyGroup_marksRowDeleted() throws Exception {
        ContactSaveService.GroupsDaoImpl sut = createDao();
        Uri uri = sut.create("Test Delete Group", getTestAccount());

        assertEquals(1, sut.delete(uri));

        Cursor cursor = cr.query(uri, null, null, null, null, null);
        try {
            cursor.moveToFirst();
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Groups.DELETED)));
        } finally {
            cursor.close();
        }
    }

    public void test_undoDeleteEmptyGroup_createsGroupWithMatchingTitle() throws Exception {
        ContactSaveService.GroupsDaoImpl sut = createDao();
        Uri uri = sut.create("Test Undo Delete Empty Group", getTestAccount());

        Bundle undoData = sut.captureDeletionUndoData(uri);

        assertEquals(1, sut.delete(uri));

        Uri groupUri = sut.undoDeletion(undoData);

        assertGroupHasTitle(groupUri, "Test Undo Delete Empty Group");
    }

    public void test_deleteNonEmptyGroup_removesGroupAndMembers() throws Exception {
        final ContactSaveService.GroupsDaoImpl sut = createDao();
        final Uri groupUri = sut.create("Test delete non-empty group", getTestAccount());

        final long groupId = ContentUris.parseId(groupUri);
        addMemberToGroup(ContentUris.parseId(createRawContact()), groupId);
        addMemberToGroup(ContentUris.parseId(createRawContact()), groupId);

        assertEquals(1, sut.delete(groupUri));

        final Cursor cursor = cr.query(Data.CONTENT_URI, null,
                Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId) },
                null, null);

        try {
            cursor.moveToFirst();
            // This is more of a characterization test since our code isn't manually deleting
            // the membership rows just the group but this still helps document the expected
            // behavior.
            assertEquals(0, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    public void test_undoDeleteNonEmptyGroup_restoresGroupAndMembers() throws Exception {
        final ContactSaveService.GroupsDaoImpl sut = createDao();
        final Uri groupUri = sut.create("Test undo delete non-empty group", getTestAccount());

        final long groupId = ContentUris.parseId(groupUri);
        addMemberToGroup(ContentUris.parseId(createRawContact()), groupId);
        addMemberToGroup(ContentUris.parseId(createRawContact()), groupId);

        Bundle undoData = sut.captureDeletionUndoData(groupUri);

        sut.delete(groupUri);

        final Uri recreatedGroup = sut.undoDeletion(undoData);

        final long newGroupId = ContentUris.parseId(recreatedGroup);

        final Cursor cursor = cr.query(Data.CONTENT_URI, null,
                Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(newGroupId) },
                null, null);

        try {
            assertEquals(2, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    public void test_captureUndoDataForDeletedGroup_returnsEmptyBundle() {
        final ContactSaveService.GroupsDaoImpl sut = createDao();

        Uri uri = sut.create("a deleted group", getTestAccount());
        sut.delete(uri);

        Bundle undoData = sut.captureDeletionUndoData(uri);

        assertTrue(undoData.isEmpty());
    }

    public void test_captureUndoDataForNonExistentGroup_returnsEmptyBundle() {
        final ContactSaveService.GroupsDaoImpl sut = createDao();

        // This test could potentially be flaky if this ID exists for some reason. 10 is subtracted
        // to reduce the likelihood of this happening; some other test may use Integer.MAX_VALUE
        // or nearby values  to cover some special case or boundary condition.
        final long nonExistentId = Integer.MAX_VALUE - 10;

        Bundle undoData = sut.captureDeletionUndoData(ContentUris
                .withAppendedId(ContactsContract.Groups.CONTENT_URI, nonExistentId));

        assertTrue(undoData.isEmpty());
    }

    public void test_undoWithEmptyBundle_doesNothing() {
        final ContactSaveService.GroupsDaoImpl sut = createDao();

        Cursor cursor = queryGroupsForTestAccount();
        try {
            assertEquals(0, cursor.getCount());
        } finally {
            cursor.close();
        }

        sut.undoDeletion(new Bundle());

        cursor = queryGroupsForTestAccount();
        try {
            assertEquals(0, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    public void test_undoDeleteEmptyGroupWithMissingMembersKey_shouldRecreateGroup() {
        final ContactSaveService.GroupsDaoImpl sut = createDao();
        final Uri groupUri = sut.create("Test undo delete null memberIds", getTestAccount());

        Bundle undoData = sut.captureDeletionUndoData(groupUri);
        undoData.remove(ContactSaveService.GroupsDaoImpl.KEY_GROUP_MEMBERS);

        sut.undoDeletion(undoData);

        assertGroupWithTitleExists("Test undo delete null memberIds");
    }

    private void assertGroupHasTitle(Uri groupUri, String title) {
        final Cursor cursor = cr.query(groupUri, new String[] { ContactsContract.Groups.TITLE },
                ContactsContract.Groups.DELETED + "=?",
                new String[] { "0" }, null, null);
        try {
            assertTrue("Group does not have title \"" + title + "\"",
                    cursor.getCount() == 1 && cursor.moveToFirst() &&
                            title.equals(cursor.getString(0)));
        } finally {
            cursor.close();
        }
    }

    private void assertGroupWithTitleExists(String title) {
        final Cursor cursor = cr.query(ContactsContract.Groups.CONTENT_URI, null,
                ContactsContract.Groups.TITLE + "=? AND " +
                        ContactsContract.Groups.DELETED + "=? AND " +
                        ContactsContract.Groups.ACCOUNT_NAME + "=?",
                new String[] { title, "0", mAccount.name }, null, null);
        try {
            assertTrue("No group exists with title \"" + title + "\"", cursor.getCount() > 0);
        } finally {
            cursor.close();
        }
    }

    private Cursor queryGroupsForTestAccount() {
        return cr.query(ContactsContract.Groups.CONTENT_URI, null,
                ContactsContract.Groups.ACCOUNT_NAME + "=?", new String[] { mAccount.name }, null);
    }

    public ContactSaveService.GroupsDaoImpl createDao() {
        return new ContactSaveService.GroupsDaoImpl(getContext());
    }

    private Uri createRawContact() {
        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, mAccount.name);
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, mAccount.type);
        return cr.insert(ContactsContract.RawContacts.CONTENT_URI, values);
    }

    private Uri addMemberToGroup(long rawContactId, long groupId) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE,
                GroupMembership.CONTENT_ITEM_TYPE);
        values.put(GroupMembership.GROUP_ROW_ID, groupId);
        return cr.insert(Data.CONTENT_URI, values);
    }

    private AccountWithDataSet getTestAccount() {
        return new AccountWithDataSet(mAccount.name, mAccount.type, null);
    }

    private Context getContext() {
        return getInstrumentation().getTargetContext();
    }
}
