/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.common.database;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.annotation.VisibleForTesting;

import com.android.contacts.common.model.SimContact;
import com.android.contacts.common.model.account.AccountWithDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides data access methods for loading contacts from a SIM card and and migrating these
 * SIM contacts to a CP2 account.
 */
public class SimContactDao {
    @VisibleForTesting
    public static final Uri ICC_CONTENT_URI = Uri.parse("content://icc/adn");

    public static String _ID = BaseColumns._ID;
    public static String NAME = "name";
    public static String NUMBER = "number";
    public static String EMAILS = "emails";

    private ContentResolver mResolver;

    public SimContactDao(Context context) {
        this(context.getContentResolver());
    }

    private SimContactDao(ContentResolver resolver) {
        mResolver = resolver;
    }

    public ArrayList<SimContact> loadSimContacts(int subscriptionId) {
        return loadFrom(ICC_CONTENT_URI.buildUpon()
                .appendPath("subId")
                .appendPath(String.valueOf(subscriptionId))
                .build());
    }

    public ArrayList<SimContact> loadSimContacts() {
        return loadFrom(ICC_CONTENT_URI);
    }

    private ArrayList<SimContact> loadFrom(Uri uri) {
        final Cursor cursor = mResolver.query(uri, null, null, null, null);

        try {
            return loadFromCursor(cursor);
        } finally {
            cursor.close();
        }
    }

    private ArrayList<SimContact> loadFromCursor(Cursor cursor) {
        final int colId = cursor.getColumnIndex(_ID);
        final int colName = cursor.getColumnIndex(NAME);
        final int colNumber = cursor.getColumnIndex(NUMBER);
        final int colEmails = cursor.getColumnIndex(EMAILS);

        final ArrayList<SimContact> result = new ArrayList<>();

        while (cursor.moveToNext()) {
            final long id = cursor.getLong(colId);
            final String name = cursor.getString(colName);
            final String number = cursor.getString(colNumber);
            final String emails = cursor.getString(colEmails);

            final SimContact contact = new SimContact(id, name, number, parseEmails(emails));
            result.add(contact);
        }
        return result;
    }

    public ContentProviderResult[] importContacts(List<SimContact> contacts,
            AccountWithDataSet targetAccount)
            throws RemoteException, OperationApplicationException {
        final ArrayList<ContentProviderOperation> ops = createImportOperations(contacts, targetAccount);
        return mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
    }

    private ArrayList<ContentProviderOperation> createImportOperations(List<SimContact> contacts,
            AccountWithDataSet targetAccount) {
        final ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        for (SimContact contact : contacts) {
            contact.appendCreateContactOperations(ops, targetAccount);
        }
        return ops;
    }

    private String[] parseEmails(String emails) {
        return emails != null ? emails.split(",") : null;
    }
}
