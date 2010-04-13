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

package com.android.contacts;

import com.android.contacts.views.detail.ContactLoader;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.test.AndroidTestCase;

import junit.framework.AssertionFailedError;

/**
 * Runs ContactLoader tests for the the contact-detail view.
 * TODO: Warning: This currently only works on wiped phones as this will wipe
 * your contact data
 * TODO: Test all fields returned by the Loader
 * TODO: Test social entries returned by the Loader
 */
public class ContactDetailLoaderTest extends AndroidTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        //mContext.getContentResolver().delete(Data.CONTENT_URI, null, null);
        //mContext.getContentResolver().delete(RawContacts.CONTENT_URI, null, null);
    }

    /**
     * Utility function to ensure that an Exception is thrown during the code
     * TODO: This should go to MoreAsserts at one point
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E assertThrows(
            Class<E> expectedException, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable exception) {
            Class<? extends Throwable> receivedException = exception.getClass();
            if (expectedException == receivedException) return (E) exception;
            throw new AssertionFailedError("Expected Exception " + expectedException +
                    " but " + receivedException + " was thrown. Details: " + exception);
        }
        throw new AssertionFailedError(
                "Expected Exception " + expectedException + " which was not thrown");
    }

    private ContactLoader.Result assertLoadContact(Uri uri) {
        final ContactLoader loader = new ContactLoader(mContext, uri);
        final ContactLoader.LoadContactTask loadContactTask = loader.new LoadContactTask();
        return loadContactTask.testExecute();
    }

    protected Uri insertStructuredName(long rawContactId, String givenName, String familyName) {
        ContentValues values = new ContentValues();
        StringBuilder sb = new StringBuilder();
        if (givenName != null) {
            sb.append(givenName);
        }
        if (givenName != null && familyName != null) {
            sb.append(" ");
        }
        if (familyName != null) {
            sb.append(familyName);
        }
        values.put(StructuredName.DISPLAY_NAME, sb.toString());
        values.put(StructuredName.GIVEN_NAME, givenName);
        values.put(StructuredName.FAMILY_NAME, familyName);

        return insertStructuredName(rawContactId, values);
    }

    protected Uri insertStructuredName(long rawContactId, ContentValues values) {
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        Uri resultUri = getContext().getContentResolver().insert(Data.CONTENT_URI, values);
        return resultUri;
    }

    protected Cursor queryRawContact(long rawContactId) {
        return getContext().getContentResolver().query(ContentUris.withAppendedId(
                RawContacts.CONTENT_URI, rawContactId), null, null, null, null);
    }

    protected Cursor queryContact(long contactId) {
        return getContext().getContentResolver().query(ContentUris.withAppendedId(
                Contacts.CONTENT_URI, contactId), null, null, null, null);
    }

    private long getContactIdByRawContactId(long rawContactId) {
        Cursor c = queryRawContact(rawContactId);
        assertTrue(c.moveToFirst());
        long contactId = c.getLong(c.getColumnIndex(RawContacts.CONTACT_ID));
        c.close();
        return contactId;
    }

    private String getContactLookupByContactId(long contactId) {
        Cursor c = queryContact(contactId);
        assertTrue(c.moveToFirst());
        String lookup = c.getString(c.getColumnIndex(Contacts.LOOKUP_KEY));
        c.close();
        return lookup;
    }

    public long createRawContact(String sourceId, String givenName, String familyName) {
        ContentValues values = new ContentValues();

        values.put(RawContacts.ACCOUNT_NAME, "aa");
        values.put(RawContacts.ACCOUNT_TYPE, "mock");
        values.put(RawContacts.SOURCE_ID, sourceId);
        values.put(RawContacts.VERSION, 1);
        values.put(RawContacts.DELETED, 0);
        values.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT);
        values.put(RawContacts.CUSTOM_RINGTONE, "d");
        values.put(RawContacts.SEND_TO_VOICEMAIL, 1);
        values.put(RawContacts.LAST_TIME_CONTACTED, 12345);
        values.put(RawContacts.STARRED, 1);
        values.put(RawContacts.SYNC1, "e");
        values.put(RawContacts.SYNC2, "f");
        values.put(RawContacts.SYNC3, "g");
        values.put(RawContacts.SYNC4, "h");

        Uri rawContactUri =
            getContext().getContentResolver().insert(RawContacts.CONTENT_URI, values);

        long rawContactId = ContentUris.parseId(rawContactUri);
        insertStructuredName(rawContactId, givenName, familyName);
        return rawContactId;
    }

    public void testNullUri() {
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, new Runnable() {
                public void run() {
                    assertLoadContact(null);
                }
            });
        assertEquals(e.getMessage(), "uri must not be null");
    }

    public void testEmptyUri() {
        IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, new Runnable() {
                public void run() {
                    assertLoadContact(Uri.EMPTY);
                }
            });
        assertEquals(e.getMessage(), "uri format is unknown");
    }

    public void testInvalidUri() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, new Runnable() {
                    public void run() {
                        assertLoadContact(Uri.parse("content://wtf"));
                    }
                });
        assertEquals(e.getMessage(), "uri format is unknown");
    }

    public void testLoadContactWithContactIdUri() {
        // Use content Uris that only contain the ID
        // Use some special characters in the source id to ensure that Encode/Decode properly
        // works in Uris
        long rawContactId1 = createRawContact("JohnDoe:;\"'[]{}=+-_\\|/.,<>?!@#$", "John", "Doe");
        long rawContactId2 = createRawContact("JaneDuh%12%%^&*()", "Jane", "Duh");

        long contactId1 = getContactIdByRawContactId(rawContactId1);
        long contactId2 = getContactIdByRawContactId(rawContactId2);

        Uri contactUri1 = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId1);
        Uri contactUri2 = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId2);

        ContactLoader.Result contact1 = assertLoadContact(contactUri1);
        ContactLoader.Result contact2 = assertLoadContact(contactUri2);

        assertEquals(contactId1, contact1.getId());
        assertEquals(contactId2, contact2.getId());
    }

    public void testLoadContactWithOldStyleUri() {
        // Use content Uris that only contain the ID but use the format used in Donut
        long rawContactId1 = createRawContact("JohnDoe", "John", "Doe");
        long rawContactId2 = createRawContact("JaneDuh", "Jane", "Duh");

        Uri oldUri1 = ContentUris.withAppendedId(Uri.parse("content://contacts"), rawContactId1);
        Uri oldUri2 = ContentUris.withAppendedId(Uri.parse("content://contacts"), rawContactId2);

        ContactLoader.Result contact1 = assertLoadContact(oldUri1);
        ContactLoader.Result contact2 = assertLoadContact(oldUri2);

        long contactId1 = getContactIdByRawContactId(rawContactId1);
        long contactId2 = getContactIdByRawContactId(rawContactId2);

        assertEquals(contactId1, contact1.getId());
        assertEquals(contactId2, contact2.getId());
    }

    public void testLoadContactWithContactLookupUri() {
        // Use lookup-style Uris that do not contain the Contact-ID
        long rawContactId1 = createRawContact("JohnDoe", "John", "Doe");
        long rawContactId2 = createRawContact("JaneDuh", "Jane", "Duh");

        assertTrue(rawContactId1 != rawContactId2);

        long contactId1 = getContactIdByRawContactId(rawContactId1);
        long contactId2 = getContactIdByRawContactId(rawContactId2);

        assertTrue(contactId1 != contactId2);

        String lookupKey1 = getContactLookupByContactId(contactId1);
        String lookupKey2 = getContactLookupByContactId(contactId2);
        assertFalse(lookupKey1.equals(lookupKey2));

        Uri contactLookupUri1 = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey1);
        Uri contactLookupUri2 = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey2);

        ContactLoader.Result contact1 = assertLoadContact(contactLookupUri1);
        ContactLoader.Result contact2 = assertLoadContact(contactLookupUri2);

        assertEquals(contactId1, contact1.getId());
        assertEquals(contactId2, contact2.getId());
    }

    public void testLoadContactWithContactLookupAndIdUri() {
        // Use lookup-style Uris that also contain the Contact-ID
        long rawContactId1 = createRawContact("JohnDoe", "John", "Doe");
        long rawContactId2 = createRawContact("JaneDuh", "Jane", "Duh");

        long contactId1 = getContactIdByRawContactId(rawContactId1);
        long contactId2 = getContactIdByRawContactId(rawContactId2);

        String lookupKey1 = getContactLookupByContactId(contactId1);
        String lookupKey2 = getContactLookupByContactId(contactId2);

        Uri contactLookupUri1 = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey1), contactId1);
        Uri contactLookupUri2 = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey2), contactId2);

        ContactLoader.Result contact1 = assertLoadContact(contactLookupUri1);
        ContactLoader.Result contact2 = assertLoadContact(contactLookupUri2);

        assertEquals(contactId1, contact1.getId());
        assertEquals(contactId2, contact2.getId());
    }

    public void testLoadContactWithContactLookupWithIncorrectIdUri() {
        // Use lookup-style Uris that contain incorrect Contact-ID
        // (we want to ensure that still the correct contact is chosen)

        long rawContactId1 = createRawContact("JohnDoe", "John", "Doe");
        long rawContactId2 = createRawContact("JaneDuh", "Jane", "Duh");

        long contactId1 = getContactIdByRawContactId(rawContactId1);
        long contactId2 = getContactIdByRawContactId(rawContactId2);

        String lookupKey1 = getContactLookupByContactId(contactId1);
        String lookupKey2 = getContactLookupByContactId(contactId2);

        long[] fakeIds = new long[] { 0, rawContactId1, rawContactId2, contactId1, contactId2 };

        for (long fakeContactId : fakeIds) {
            Uri contactLookupUri1 = ContentUris.withAppendedId(
                    Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey1), fakeContactId);
            Uri contactLookupUri2 = ContentUris.withAppendedId(
                    Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey2), fakeContactId);

            ContactLoader.Result contact1 = assertLoadContact(contactLookupUri1);
            ContactLoader.Result contact2 = assertLoadContact(contactLookupUri2);

            assertEquals(contactId1, contact1.getId());
            assertEquals(contactId2, contact2.getId());
        }
    }
}
