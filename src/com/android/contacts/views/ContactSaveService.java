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

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.ArrayList;

public class ContactSaveService extends IntentService {
    private static final String TAG = "ContactSaveService";

    public static final String EXTRA_OPERATIONS = "Operations";

    public ContactSaveService() {
        super(TAG);
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
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
}
