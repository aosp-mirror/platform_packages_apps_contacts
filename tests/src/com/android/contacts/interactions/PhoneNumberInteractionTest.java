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
import com.android.contacts.interactions.PhoneNumberInteraction.PhoneItem;
import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockContentProvider;
import com.android.contacts.tests.mocks.MockContentProvider.Query;

import android.content.AsyncTaskLoader;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.Smoke;

import java.util.ArrayList;

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
@Smoke
public class PhoneNumberInteractionTest extends InstrumentationTestCase {

    private final static class TestPhoneNumberInteraction extends PhoneNumberInteraction {
        Intent startedIntent;
        int dialogId;
        Bundle dialogArgs;

        public TestPhoneNumberInteraction(
                Context context, boolean sendTextMessage, OnDismissListener dismissListener) {
            super(context, sendTextMessage, dismissListener);
        }

        @Override
        void startLoading(Loader<Cursor> loader) {
            // Execute the loader synchronously
            AsyncTaskLoader<Cursor> atLoader = (AsyncTaskLoader<Cursor>)loader;
            Cursor data = atLoader.loadInBackground();
            atLoader.deliverResult(data);
        }

        @Override
        void startActivity(Intent intent) {
            this.startedIntent = intent;
        }

        @Override
        void showDialog(int dialogId, Bundle bundle) {
            this.dialogId = dialogId;
            this.dialogArgs = bundle;
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
                .returnRow(1, "123", 0, null, Phone.TYPE_HOME, null);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, true, null);

        interaction.startInteraction(contactUri);

        assertEquals(Intent.ACTION_SENDTO, interaction.startedIntent.getAction());
        assertEquals("sms:123", interaction.startedIntent.getDataString());
    }

    public void testSendSmsWhenThereIsPrimaryNumber() {
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 13);
        expectQuery(contactUri)
                .returnRow(1, "123", 0, null, Phone.TYPE_HOME, null)
                .returnRow(2, "456", 1, null, Phone.TYPE_HOME, null);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, true, null);

        interaction.startInteraction(contactUri);

        assertEquals(Intent.ACTION_SENDTO, interaction.startedIntent.getAction());
        assertEquals("sms:456", interaction.startedIntent.getDataString());
    }

    public void testCallNumberWhenThereAreDuplicates() {
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 13);
        expectQuery(contactUri)
                .returnRow(1, "123", 0, null, Phone.TYPE_HOME, null)
                .returnRow(2, "123", 0, null, Phone.TYPE_WORK, null);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, false, null);

        interaction.startInteraction(contactUri);

        assertEquals(Intent.ACTION_CALL_PRIVILEGED, interaction.startedIntent.getAction());
        assertEquals("tel:123", interaction.startedIntent.getDataString());
    }

    public void testShowDisambigDialogForCalling() {
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 13);
        expectQuery(contactUri)
                .returnRow(1, "123", 0, "account", Phone.TYPE_HOME, "label")
                .returnRow(2, "456", 0, null, Phone.TYPE_WORK, null);

        TestPhoneNumberInteraction interaction = new TestPhoneNumberInteraction(
                mContext, false, null);

        interaction.startInteraction(contactUri);

        assertEquals(R.id.dialog_phone_number_call_disambiguation, interaction.dialogId);

        ArrayList<PhoneItem> items = interaction.dialogArgs.getParcelableArrayList(
                PhoneNumberInteraction.EXTRA_KEY_ITEMS);
        assertEquals(2, items.size());

        PhoneItem item = items.get(0);
        assertEquals(1, item.id);
        assertEquals("123", item.phoneNumber);
        assertEquals("account", item.accountType);
        assertEquals(Phone.TYPE_HOME, item.type);
        assertEquals("label", item.label);
    }

    private Query expectQuery(Uri contactUri) {
        Uri dataUri = Uri.withAppendedPath(contactUri, Contacts.Data.CONTENT_DIRECTORY);
        return mContactsProvider
                .expectQuery(dataUri)
                .withProjection(
                        Phone._ID,
                        Phone.NUMBER,
                        Phone.IS_SUPER_PRIMARY,
                        RawContacts.ACCOUNT_TYPE,
                        Phone.TYPE,
                        Phone.LABEL)
                .withSelection("mimetype='vnd.android.cursor.item/phone_v2' AND data1 NOT NULL");
    }
}
