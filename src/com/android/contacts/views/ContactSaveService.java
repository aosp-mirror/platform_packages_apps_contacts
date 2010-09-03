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

package com.android.contacts.views;

import com.android.contacts.activities.ContactBrowserActivity;
import com.google.android.collect.Sets;

import android.accounts.Account;
import android.app.Activity;
import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ContactSaveService extends IntentService {
    private static final String TAG = "ContactSaveService";

    public static final String ACTION_NEW_RAW_CONTACT = "newRawContact";

    public static final String EXTRA_ACCOUNT_NAME = "accountName";
    public static final String EXTRA_ACCOUNT_TYPE = "accountType";
    public static final String EXTRA_CONTENT_VALUES = "contentValues";
    public static final String EXTRA_CALLBACK_INTENT = "callbackIntent";

    public static final String EXTRA_OPERATIONS = "Operations";

    private static final HashSet<String> ALLOWED_DATA_COLUMNS = Sets.newHashSet(
        Data.MIMETYPE,
        Data.IS_PRIMARY,
        Data.DATA1,
        Data.DATA2,
        Data.DATA3,
        Data.DATA4,
        Data.DATA5,
        Data.DATA6,
        Data.DATA7,
        Data.DATA8,
        Data.DATA9,
        Data.DATA10,
        Data.DATA11,
        Data.DATA12,
        Data.DATA13,
        Data.DATA14,
        Data.DATA15
    );

    public ContactSaveService() {
        super(TAG);
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (ACTION_NEW_RAW_CONTACT.equals(action)) {
            createRawContact(intent);
        } else {
            performContentProviderOperations(intent);
        }
    }

    private void createRawContact(Intent intent) {
        String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
        String accountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE);
        List<ContentValues> valueList = intent.getParcelableArrayListExtra(EXTRA_CONTENT_VALUES);
        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);

        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        operations.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .build());

        int size = valueList.size();
        for (int i = 0; i < size; i++) {
            ContentValues values = valueList.get(i);
            values.keySet().retainAll(ALLOWED_DATA_COLUMNS);
            operations.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValues(values)
                    .build());
        }

        ContentResolver resolver = getContentResolver();
        ContentProviderResult[] results;
        try {
            results = resolver.applyBatch(ContactsContract.AUTHORITY, operations);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store new contact", e);
        }

        Uri result = ContactsContract.Directory.CONTENT_URI;
        Uri rawContactUri = results[0].uri;
        callbackIntent.setData(RawContacts.getContactLookupUri(resolver, rawContactUri));

        startActivity(callbackIntent);
    }

    private void performContentProviderOperations(Intent intent) {
        final Parcelable[] operationsArray = intent.getParcelableArrayExtra(EXTRA_OPERATIONS);

        // We have to cast each item individually here
        final ArrayList<ContentProviderOperation> operations =
                new ArrayList<ContentProviderOperation>(operationsArray.length);
        for (Parcelable p : operationsArray) {
            operations.add((ContentProviderOperation) p);
        }

        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
        } catch (RemoteException e) {
            Log.e(TAG, "Error saving", e);
        } catch (OperationApplicationException e) {
            Log.e(TAG, "Error saving", e);
        }
    }

    /**
     * Creates an intent that can be sent to this service to create a new raw contact
     * using data presented as a set of ContentValues.
     */
    public static Intent createNewRawContactIntent(
            Activity activity, ArrayList<ContentValues> values, Account account) {
        Intent serviceIntent = new Intent(
                activity, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_NEW_RAW_CONTACT);
        if (account != null) {
            serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_NAME, account.name);
            serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE, account.type);
        }
        serviceIntent.putParcelableArrayListExtra(
                ContactSaveService.EXTRA_CONTENT_VALUES, values);

        // Callback intent will be invoked by the service once the new contact is
        // created.  The service will put the URI of the new contact as "data" on
        // the callback intent.
        Intent callbackIntent = new Intent(activity, activity.getClass());
        callbackIntent.setAction(Intent.ACTION_VIEW);
        callbackIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);
        return serviceIntent;
    }
}
