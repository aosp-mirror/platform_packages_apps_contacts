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

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

/**
 * Tests for {@link ContactsUtils}.
 */
@LargeTest
public class ContactsUtilsTests extends AndroidTestCase {
    private static final String TEST_ADDRESS = "user@example.org";
    private static final String TEST_PROTOCOL = "prot%col";

    public void testImIntent() throws Exception {
        // Normal IM is appended as path
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        values.put(Im.DATA, TEST_ADDRESS);

        final Intent intent = ContactsUtils.buildImIntent(values);
        assertEquals(Intent.ACTION_SENDTO, intent.getAction());

        final Uri data = intent.getData();
        assertEquals("imto", data.getScheme());
        assertEquals("gtalk", data.getAuthority());
        assertEquals(TEST_ADDRESS, data.getPathSegments().get(0));
    }

    public void testImIntentCustom() throws Exception {
        // Custom IM types have encoded authority
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
        values.put(Im.CUSTOM_PROTOCOL, TEST_PROTOCOL);
        values.put(Im.DATA, TEST_ADDRESS);

        final Intent intent = ContactsUtils.buildImIntent(values);
        assertEquals(Intent.ACTION_SENDTO, intent.getAction());

        final Uri data = intent.getData();
        assertEquals("imto", data.getScheme());
        assertEquals(TEST_PROTOCOL, data.getAuthority());
        assertEquals(TEST_ADDRESS, data.getPathSegments().get(0));
    }

    public void testImEmailIntent() throws Exception {
        // Email addresses are treated as Google Talk entries
        final ContentValues values = new ContentValues();
        values.put(Email.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.put(Email.DATA, TEST_ADDRESS);

        final Intent intent = ContactsUtils.buildImIntent(values);
        assertEquals(Intent.ACTION_SENDTO, intent.getAction());

        final Uri data = intent.getData();
        assertEquals("imto", data.getScheme());
        assertEquals("gtalk", data.getAuthority());
        assertEquals(TEST_ADDRESS, data.getPathSegments().get(0));
    }

    public void testIsGraphicNull() throws Exception {
        assertFalse(ContactsUtils.isGraphic(null));
    }

    public void testIsGraphicEmpty() throws Exception {
        assertFalse(ContactsUtils.isGraphic(""));
    }

    public void testIsGraphicSpaces() throws Exception {
        assertFalse(ContactsUtils.isGraphic("  "));
    }

    public void testIsGraphicPunctuation() throws Exception {
        assertTrue(ContactsUtils.isGraphic("."));
    }

    public void testAreObjectsEqual() throws Exception {
        assertTrue("null:null", ContactsUtils.areObjectsEqual(null, null));
        assertTrue("1:1", ContactsUtils.areObjectsEqual(1, 1));

        assertFalse("null:1", ContactsUtils.areObjectsEqual(null, 1));
        assertFalse("1:null", ContactsUtils.areObjectsEqual(1, null));
        assertFalse("1:2", ContactsUtils.areObjectsEqual(1, 2));
    }

    public void testAreDataEqual() throws Exception {
        checkAreDataEqual("1", true, null, null, null, null);
        checkAreDataEqual("2", true, "a", "b", "a", "b");

        checkAreDataEqual("11", false, "a", null, null, null);
        checkAreDataEqual("12", false, null, "a", null, null);
        checkAreDataEqual("13", false, null, null, "a", null);
        checkAreDataEqual("14", false, null, null, null, "a");

        checkAreDataEqual("21", false, "a", "b", null, null);
        checkAreDataEqual("22", false, "a", "b", "a", null);
        checkAreDataEqual("23", false, "a", "b", null, "b");
        checkAreDataEqual("24", false, "a", "b", "a", "x");
        checkAreDataEqual("25", false, "a", "b", "x", "b");

        checkAreDataEqual("31", false, null, null, "a", "b");
        checkAreDataEqual("32", false, "a", null, "a", "b");
        checkAreDataEqual("33", false, null, "b", "a", "b");
        checkAreDataEqual("34", false, "a", "x", "a", "b");
        checkAreDataEqual("35", false, "x", "b", "a", "b");

        checkAreDataEqual("41", true, Phone.CONTENT_ITEM_TYPE, null, Phone.CONTENT_ITEM_TYPE,
                null);
        checkAreDataEqual("42", true, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, "1");

        checkAreDataEqual("51", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, "2");
        checkAreDataEqual("52", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, null);
        checkAreDataEqual("53", false, Phone.CONTENT_ITEM_TYPE, null, Phone.CONTENT_ITEM_TYPE, "2");
    }

    private void checkAreDataEqual(String message, boolean expected, CharSequence mimetype1,
            CharSequence data1, CharSequence mimetype2, CharSequence data2) {
        assertEquals(message, expected,
                ContactsUtils.areDataEqual(mContext, mimetype1, data1, mimetype2, data2));
    }

    public void testAreIntentActionEqual() throws Exception {
        assertTrue("1", ContactsUtils.areIntentActionEqual(null, null));
        assertTrue("1", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent("a")));

        assertFalse("11", ContactsUtils.areIntentActionEqual(new Intent("a"), null));
        assertFalse("12", ContactsUtils.areIntentActionEqual(null, new Intent("a")));

        assertFalse("21", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent()));
        assertFalse("22", ContactsUtils.areIntentActionEqual(new Intent(), new Intent("b")));
        assertFalse("23", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent("b")));
    }
}
