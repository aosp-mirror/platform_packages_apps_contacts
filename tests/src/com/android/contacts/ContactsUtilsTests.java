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

    public void testShouldCollapse() throws Exception {
        checkShouldCollapse("1", true, null, null, null, null);
        checkShouldCollapse("2", true, "a", "b", "a", "b");

        checkShouldCollapse("11", false, "a", null, null, null);
        checkShouldCollapse("12", false, null, "a", null, null);
        checkShouldCollapse("13", false, null, null, "a", null);
        checkShouldCollapse("14", false, null, null, null, "a");

        checkShouldCollapse("21", false, "a", "b", null, null);
        checkShouldCollapse("22", false, "a", "b", "a", null);
        checkShouldCollapse("23", false, "a", "b", null, "b");
        checkShouldCollapse("24", false, "a", "b", "a", "x");
        checkShouldCollapse("25", false, "a", "b", "x", "b");

        checkShouldCollapse("31", false, null, null, "a", "b");
        checkShouldCollapse("32", false, "a", null, "a", "b");
        checkShouldCollapse("33", false, null, "b", "a", "b");
        checkShouldCollapse("34", false, "a", "x", "a", "b");
        checkShouldCollapse("35", false, "x", "b", "a", "b");

        checkShouldCollapse("41", true, Phone.CONTENT_ITEM_TYPE, null, Phone.CONTENT_ITEM_TYPE,
                null);
        checkShouldCollapse("42", true, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, "1");

        checkShouldCollapse("51", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE,
                "2");
        checkShouldCollapse("52", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE,
                null);
        checkShouldCollapse("53", false, Phone.CONTENT_ITEM_TYPE, null, Phone.CONTENT_ITEM_TYPE,
                "2");

        // Test phone numbers
        checkShouldCollapse("60", true,
                Phone.CONTENT_ITEM_TYPE, "1234567",
                Phone.CONTENT_ITEM_TYPE, "1234567");
        checkShouldCollapse("61", false,
                Phone.CONTENT_ITEM_TYPE, "1234567",
                Phone.CONTENT_ITEM_TYPE, "1234568");
        checkShouldCollapse("62", true,
                Phone.CONTENT_ITEM_TYPE, "1234567;0",
                Phone.CONTENT_ITEM_TYPE, "1234567;0");
        checkShouldCollapse("63", false,
                Phone.CONTENT_ITEM_TYPE, "1234567;89321",
                Phone.CONTENT_ITEM_TYPE, "1234567;321");
        checkShouldCollapse("64", true,
                Phone.CONTENT_ITEM_TYPE, "1234567;89321",
                Phone.CONTENT_ITEM_TYPE, "1234567;89321");
        checkShouldCollapse("65", false,
                Phone.CONTENT_ITEM_TYPE, "1234567;0111111111",
                Phone.CONTENT_ITEM_TYPE, "1234567;");
        checkShouldCollapse("66", false,
                Phone.CONTENT_ITEM_TYPE, "12345675426;91970xxxxx",
                Phone.CONTENT_ITEM_TYPE, "12345675426");
        checkShouldCollapse("67", false,
                Phone.CONTENT_ITEM_TYPE, "12345675426;23456xxxxx",
                Phone.CONTENT_ITEM_TYPE, "12345675426;234567xxxx");
        checkShouldCollapse("68", true,
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567",
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567");
        checkShouldCollapse("69", false,
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567",
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567");
    }

    private void checkShouldCollapse(String message, boolean expected, CharSequence mimetype1,
            CharSequence data1, CharSequence mimetype2, CharSequence data2) {
        assertEquals(message, expected,
                ContactsUtils.shouldCollapse(mContext, mimetype1, data1, mimetype2, data2));
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
