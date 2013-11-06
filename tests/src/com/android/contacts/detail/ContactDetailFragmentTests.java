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

package com.android.contacts.detail;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.detail.ContactDetailFragment.DetailViewEntry;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.EmailDataItem;
import com.android.contacts.common.model.dataitem.ImDataItem;

/**
 * Tests for {@link ContactDetailFragment}.
 */
@SmallTest
public class ContactDetailFragmentTests extends AndroidTestCase {
    private static final String TEST_ADDRESS = "user@example.org";
    private static final String TEST_PROTOCOL = "prot%col";

    public void testImIntent() throws Exception {
        // Test GTalk XMPP URI. No chat capabilities provided
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        values.put(Im.DATA, TEST_ADDRESS);
        ImDataItem im = (ImDataItem) DataItem.createFrom(values);

        DetailViewEntry entry = new ContactDetailFragment.DetailViewEntry();
        ContactDetailFragment.buildImActions(mContext, entry, im);
        assertEquals(Intent.ACTION_SENDTO, entry.intent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?message", entry.intent.getData().toString());

        assertNull(entry.secondaryIntent);
    }

    public void testImIntentWithAudio() throws Exception {
        // Test GTalk XMPP URI. Audio chat capabilities provided
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        values.put(Im.DATA, TEST_ADDRESS);
        values.put(Im.CHAT_CAPABILITY, Im.CAPABILITY_HAS_VOICE | Im.CAPABILITY_HAS_VIDEO);
        ImDataItem im = (ImDataItem) DataItem.createFrom(values);

        DetailViewEntry entry = new ContactDetailFragment.DetailViewEntry();
        ContactDetailFragment.buildImActions(mContext, entry, im);
        assertEquals(Intent.ACTION_SENDTO, entry.intent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?message", entry.intent.getData().toString());

        assertEquals(Intent.ACTION_SENDTO, entry.secondaryIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?call", entry.secondaryIntent.getData().toString());
    }

    public void testImIntentWithVideo() throws Exception {
        // Test GTalk XMPP URI. Video chat capabilities provided
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        values.put(Im.DATA, TEST_ADDRESS);
        values.put(Im.CHAT_CAPABILITY, Im.CAPABILITY_HAS_VOICE | Im.CAPABILITY_HAS_VIDEO |
                Im.CAPABILITY_HAS_VOICE);
        ImDataItem im = (ImDataItem) DataItem.createFrom(values);

        DetailViewEntry entry = new ContactDetailFragment.DetailViewEntry();
        ContactDetailFragment.buildImActions(mContext, entry, im);
        assertEquals(Intent.ACTION_SENDTO, entry.intent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?message", entry.intent.getData().toString());

        assertEquals(Intent.ACTION_SENDTO, entry.secondaryIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?call", entry.secondaryIntent.getData().toString());
    }

    public void testImIntentCustom() throws Exception {
        // Custom IM types have encoded authority. We send the imto Intent here, because
        // legacy third party apps might not accept xmpp yet
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
        values.put(Im.CUSTOM_PROTOCOL, TEST_PROTOCOL);
        values.put(Im.DATA, TEST_ADDRESS);
        ImDataItem im = (ImDataItem) DataItem.createFrom(values);

        DetailViewEntry entry = new ContactDetailFragment.DetailViewEntry();
        final Intent imIntent =
                ContactDetailFragment.getCustomIMIntent(im, Im.PROTOCOL_CUSTOM);
        assertEquals(Intent.ACTION_SENDTO, imIntent.getAction());

        final Uri data = imIntent.getData();
        assertEquals("imto", data.getScheme());
        assertEquals(TEST_PROTOCOL, data.getAuthority());
        assertEquals(TEST_ADDRESS, data.getPathSegments().get(0));

        assertNull(entry.secondaryIntent);
    }

    public void testImEmailIntent() throws Exception {
        // Email addresses are treated as Google Talk entries
        // This test only tests the VIDEO+CAMERA case. The other cases have been addressed by the
        // Im tests
        final ContentValues values = new ContentValues();
        values.put(Email.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.put(Email.DATA, TEST_ADDRESS);
        values.put(Email.CHAT_CAPABILITY, Im.CAPABILITY_HAS_VOICE | Im.CAPABILITY_HAS_VIDEO |
                Im.CAPABILITY_HAS_VOICE);
        ImDataItem im = ImDataItem.createFromEmail(
                (EmailDataItem) DataItem.createFrom(values));

        DetailViewEntry entry = new ContactDetailFragment.DetailViewEntry();
        ContactDetailFragment.buildImActions(mContext, entry, im);
        assertEquals(Intent.ACTION_SENDTO, entry.intent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?message", entry.intent.getData().toString());

        assertEquals(Intent.ACTION_SENDTO, entry.secondaryIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?call", entry.secondaryIntent.getData().toString());
    }
}
