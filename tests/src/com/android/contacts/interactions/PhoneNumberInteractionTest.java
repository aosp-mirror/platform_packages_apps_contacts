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

import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.interactions.PhoneNumberInteraction.InteractionType;
import com.android.contacts.interactions.PhoneNumberInteraction.PhoneItem;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockContentProvider;
import com.android.contacts.tests.mocks.MockContentProvider.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link PhoneNumberInteraction}.
 *
 * Running all tests:
 *
 *   runtest contacts
 * or
 *   adb shell am instrument \
 *     -w com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class PhoneNumberInteractionTest extends InstrumentationTestCase {

    static {
        // AsyncTask class needs to be initialized on the main thread.
        AsyncTask.init();
    }

    private final static class TestPhoneNumberInteraction extends PhoneNumberInteraction {
        private ArrayList<PhoneItem> mPhoneList;

        public TestPhoneNumberInteraction(Context context, InteractionType interactionType,
                OnDismissListener dismissListener) {
            super(context, interactionType, dismissListener);
        }

        @Override
        void showDisambiguationDialog(ArrayList<PhoneItem> phoneList) {
            this.mPhoneList = phoneList;
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

    @Override
    protected void tearDown() throws Exception {
        mContactsProvider.verify();
        super.tearDown();
    }

    public void testSendSmsWhenOnlyOneNumberAvailable() {
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 13);
        expectQuery(contactUri)
                .returnRow(1, "123", 0, null, null, Phone.TYPE_HOME, null,
                        Phone.CONTENT_ITEM_TYPE);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, InteractionType.SMS, null);

        interaction.startInteraction(contactUri);
        interaction.getLoader().waitForLoader();

        Intent intent = mContext.getIntentForStartActivity();
        assertNotNull(intent);

        assertEquals(Intent.ACTION_SENDTO, intent.getAction());
        assertEquals("sms:123", intent.getDataString());
    }

    public void testSendSmsWhenDataIdIsProvided() {
        Uri dataUri = ContentUris.withAppendedId(Data.CONTENT_URI, 1);
        expectQuery(dataUri, true /* isDataUri */ )
                .returnRow(1, "987", 0, null, null, Phone.TYPE_HOME, null,
                        Phone.CONTENT_ITEM_TYPE);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, InteractionType.SMS, null);

        interaction.startInteraction(dataUri);
        interaction.getLoader().waitForLoader();

        Intent intent = mContext.getIntentForStartActivity();
        assertNotNull(intent);

        assertEquals(Intent.ACTION_SENDTO, intent.getAction());
        assertEquals("sms:987", intent.getDataString());
    }

    public void testSendSmsWhenThereIsPrimaryNumber() {
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 13);
        expectQuery(contactUri)
                .returnRow(
                        1, "123", 0, null, null, Phone.TYPE_HOME, null, Phone.CONTENT_ITEM_TYPE)
                .returnRow(
                        2, "456", 1, null, null, Phone.TYPE_HOME, null, Phone.CONTENT_ITEM_TYPE);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, InteractionType.SMS, null);

        interaction.startInteraction(contactUri);
        interaction.getLoader().waitForLoader();

        Intent intent = mContext.getIntentForStartActivity();
        assertNotNull(intent);

        assertEquals(Intent.ACTION_SENDTO, intent.getAction());
        assertEquals("sms:456", intent.getDataString());
    }

    public void testShouldCollapseWith() {
        PhoneNumberInteraction.PhoneItem phoneItem1 = new PhoneNumberInteraction.PhoneItem();
        PhoneNumberInteraction.PhoneItem phoneItem2 = new PhoneNumberInteraction.PhoneItem();

        phoneItem1.phoneNumber = "123";
        phoneItem2.phoneNumber = "123";

        assertTrue(phoneItem1.shouldCollapseWith(phoneItem2));

        phoneItem1.phoneNumber = "123";
        phoneItem2.phoneNumber = "456";

        assertFalse(phoneItem1.shouldCollapseWith(phoneItem2));

        phoneItem1.phoneNumber = "123#,123";
        phoneItem2.phoneNumber = "123#,456";

        assertFalse(phoneItem1.shouldCollapseWith(phoneItem2));
    }

    public void testCallNumberWhenThereAreDuplicates() {
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 13);
        expectQuery(contactUri)
                .returnRow(1, "123", 0, null, null, Phone.TYPE_HOME, null,
                        Phone.CONTENT_ITEM_TYPE)
                .returnRow(2, "123", 0, null, null, Phone.TYPE_WORK, null,
                        Phone.CONTENT_ITEM_TYPE);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, InteractionType.PHONE_CALL, null);

        interaction.startInteraction(contactUri);
        interaction.getLoader().waitForLoader();

        Intent intent = mContext.getIntentForStartActivity();
        assertNotNull(intent);

        assertEquals(Intent.ACTION_CALL_PRIVILEGED, intent.getAction());
        assertEquals("tel:123", intent.getDataString());
    }

    public void testCallWithSip() {
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 13);
        expectQuery(contactUri)
                .returnRow(1, "example@example.com", 0, null, null, Phone.TYPE_HOME, null,
                        SipAddress.CONTENT_ITEM_TYPE);
        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, InteractionType.PHONE_CALL, null);

        interaction.startInteraction(contactUri);
        interaction.getLoader().waitForLoader();

        Intent intent = mContext.getIntentForStartActivity();
        assertNotNull(intent);

        assertEquals(Intent.ACTION_CALL_PRIVILEGED, intent.getAction());
        assertEquals("sip:example%40example.com", intent.getDataString());
    }

    public void testShowDisambigDialogForCalling() {
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 13);
        expectQuery(contactUri)
                .returnRow(1, "123", 0, "account", null, Phone.TYPE_HOME, "label",
                        Phone.CONTENT_ITEM_TYPE)
                .returnRow(2, "456", 0, null, null, Phone.TYPE_WORK, null,
                        Phone.CONTENT_ITEM_TYPE);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, InteractionType.PHONE_CALL, null);

        interaction.startInteraction(contactUri);
        interaction.getLoader().waitForLoader();

        List<PhoneItem> items = interaction.mPhoneList;
        assertNotNull(items);
        assertEquals(2, items.size());

        PhoneItem item = items.get(0);
        assertEquals(1, item.id);
        assertEquals("123", item.phoneNumber);
        assertEquals("account", item.accountType);
        assertEquals(Phone.TYPE_HOME, item.type);
        assertEquals("label", item.label);
    }

    private Query expectQuery(Uri contactUri) {
        return expectQuery(contactUri, false);
    }

    private Query expectQuery(Uri uri, boolean isDataUri) {
        final Uri dataUri;
        if (isDataUri) {
            dataUri = uri;
        } else {
            dataUri = Uri.withAppendedPath(uri, Contacts.Data.CONTENT_DIRECTORY);
        }
        return mContactsProvider
                .expectQuery(dataUri)
                .withProjection(
                        Phone._ID,
                        Phone.NUMBER,
                        Phone.IS_SUPER_PRIMARY,
                        RawContacts.ACCOUNT_TYPE,
                        RawContacts.DATA_SET,
                        Phone.TYPE,
                        Phone.LABEL,
                        Phone.MIMETYPE)
                .withSelection("mimetype IN ('vnd.android.cursor.item/phone_v2',"
                        + " 'vnd.android.cursor.item/sip_address') AND data1 NOT NULL");
    }
}
