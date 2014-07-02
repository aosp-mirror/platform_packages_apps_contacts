/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.contacts.common.tests.testauth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

/**
 * Simple (minimal) sync adapter.
 *
 */
public class TestSyncAdapter extends AbstractThreadedSyncAdapter {
    private final AccountManager mAccountManager;

    private final Context mContext;

    public TestSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context.getApplicationContext();
        mAccountManager = AccountManager.get(mContext);
    }

    /**
     * Doesn't actually sync, but sweep up all existing local-only contacts.
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        Log.v(TestauthConstants.LOG_TAG, "TestSyncAdapter.onPerformSync() account=" + account);

        // First, claim all local-only contacts, if any.
        ContentResolver cr = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(RawContacts.ACCOUNT_NAME, account.name);
        values.put(RawContacts.ACCOUNT_TYPE, account.type);
        final int count = cr.update(RawContacts.CONTENT_URI, values,
                RawContacts.ACCOUNT_NAME + " IS NULL AND " + RawContacts.ACCOUNT_TYPE + " IS NULL",
                null);
        if (count > 0) {
            Log.v(TestauthConstants.LOG_TAG, "Claimed " + count + " local raw contacts");
        }

        // TODO: Clear isDirty flag
        // TODO: Remove isDeleted raw contacts
    }
}
