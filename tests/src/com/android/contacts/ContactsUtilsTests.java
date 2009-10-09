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
}
