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

package com.android.contacts.list;

import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;

import android.accounts.Account;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.Groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * A loader for the data needed for the group selector.
 */
public class ContactListFilterLoader extends AsyncTaskLoader<List<ContactListFilter>> {

    private static final class GroupQuery {
        public static final String[] COLUMNS = {
            Groups._ID,
            Groups.ACCOUNT_TYPE,
            Groups.ACCOUNT_NAME,
            Groups.TITLE,
            Groups.AUTO_ADD,
        };

        public static final int ID = 0;
        public static final int ACCOUNT_TYPE = 1;
        public static final int ACCOUNT_NAME = 2;
        public static final int TITLE = 3;
        public static final int IS_DEFAULT_GROUP = 4;       // Using the AUTO_ADD group as default

        private static final String SELECTION =
                Groups.DELETED + "=0 AND " + Groups.FAVORITES + "=0";
    }

    public ContactListFilterLoader(Context context) {
        super(context);
    }

    @Override
    public List<ContactListFilter> loadInBackground() {

        ArrayList<ContactListFilter> results = new ArrayList<ContactListFilter>();
        Context context = getContext();
        final Sources sources = Sources.getInstance(context);
        ArrayList<Account> accounts = sources.getAccounts(false);
        for (Account account : accounts) {
            ContactsSource source = sources.getInflatedSource(
                    account.type, ContactsSource.LEVEL_SUMMARY);
            Drawable icon = source != null ? source.getDisplayIcon(getContext()) : null;
            results.add(new ContactListFilter(account.type, account.name, icon, account.name));
        }

        HashSet<Account> accountsWithGroups = new HashSet<Account>();
        ContentResolver resolver = context.getContentResolver();

        Cursor cursor = resolver.query(
                Groups.CONTENT_URI, GroupQuery.COLUMNS, GroupQuery.SELECTION, null, null);
        try {
            while (cursor.moveToNext()) {
                long groupId = cursor.getLong(GroupQuery.ID);
                String accountType = cursor.getString(GroupQuery.ACCOUNT_TYPE);
                String accountName = cursor.getString(GroupQuery.ACCOUNT_NAME);
                boolean defaultGroup = false;
                if (!cursor.isNull(GroupQuery.IS_DEFAULT_GROUP)) {
                    defaultGroup = cursor.getInt(GroupQuery.IS_DEFAULT_GROUP) != 0;
                }
                if (defaultGroup) {
                    // Find the filter for this account and set the default group ID
                    for (ContactListFilter filter : results) {
                        if (filter.accountName.equals(accountName)
                                && filter.accountType.equals(accountType)) {
                            filter.groupId = groupId;
                            break;
                        }
                    }
                } else {
                    String title = cursor.getString(GroupQuery.TITLE);
                    results.add(new ContactListFilter(accountType, accountName, groupId, title));
                }
            }
        } finally {
            cursor.close();
        }

        Collections.sort(results);

        return results;
    }

    @Override
    public void destroy() {
        stopLoading();
    }

    @Override
    public void startLoading() {
        cancelLoad();
        forceLoad();
    }

    @Override
    public void stopLoading() {
        cancelLoad();
    }
}
