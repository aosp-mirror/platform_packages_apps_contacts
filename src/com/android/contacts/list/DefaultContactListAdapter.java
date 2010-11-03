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

import com.android.contacts.preference.ContactsPreferences;

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 */
public class DefaultContactListAdapter extends ContactListAdapter {

    public DefaultContactListAdapter(Context context) {
        super(context);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {

        ContactListFilter filter = getFilter();
        if (isSearchMode()) {
            String query = getQueryString();
            Builder builder = Contacts.CONTENT_FILTER_URI.buildUpon();
            if (TextUtils.isEmpty(query)) {
                builder.appendPath("");
            } else {
                builder.appendPath(query);      // Builder will encode the query
            }
            builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                    String.valueOf(directoryId));
            if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE) {
                builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                        String.valueOf(getDirectoryResultLimit()));
            }
            loader.setUri(builder.build());
            loader.setProjection(FILTER_PROJECTION);
        } else {
            configureUri(loader, directoryId, filter);
            configureProjection(loader, directoryId, filter);
            configureSelection(loader, directoryId, filter);
        }

        String sortOrder;
        if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            sortOrder = Contacts.SORT_KEY_PRIMARY;
        } else {
            sortOrder = Contacts.SORT_KEY_ALTERNATIVE;
        }

        loader.setSortOrder(sortOrder);
    }

    protected void configureUri(CursorLoader loader, long directoryId, ContactListFilter filter) {
        Uri uri = Contacts.CONTENT_URI;
        if (filter != null) {
            if (filter.filterType == ContactListFilter.FILTER_TYPE_GROUP) {
                uri = Data.CONTENT_URI;
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                String lookupKey = getSelectedContactLookupKey();
                if (lookupKey != null) {
                    uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
                } else {
                    // Non-existent contact
                    uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, 0);
                }
            }
        }

        if (directoryId == Directory.DEFAULT && isSectionHeaderDisplayEnabled()) {
            uri = buildSectionIndexerUri(uri);
        }

        // The "All accounts" filter is the same as the entire contents of Directory.DEFAULT
        if (filter != null
                && filter.filterType != ContactListFilter.FILTER_TYPE_CUSTOM
                && filter.filterType != ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            uri = uri.buildUpon().appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                    .build();
        }
        loader.setUri(uri);
    }

    protected void configureProjection(
            CursorLoader loader, long directoryId, ContactListFilter filter) {
        if (filter != null && filter.filterType == ContactListFilter.FILTER_TYPE_GROUP) {
            loader.setProjection(PROJECTION_DATA);
        } else {
            loader.setProjection(PROJECTION_CONTACT);
        }
    }

    private void configureSelection(
            CursorLoader loader, long directoryId, ContactListFilter filter) {
        if (filter == null) {
            return;
        }

        if (directoryId != Directory.DEFAULT) {
            return;
        }

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();

        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
                // We have already added directory=0 to the URI, which takes care of this
                // filter
                break;
            }
            case ContactListFilter.FILTER_TYPE_SINGLE_CONTACT: {
                // We have already added the lookup key to the URI, which takes care of this
                // filter
                break;
            }
            case ContactListFilter.FILTER_TYPE_STARRED: {
                selection.append(Contacts.STARRED + "!=0");
                break;
            }
            case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY: {
                selection.append(Contacts.HAS_PHONE_NUMBER + "=1");
                break;
            }
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                selection.append(Contacts.IN_VISIBLE_GROUP + "=1");
                if (isCustomFilterForPhoneNumbersOnly()) {
                    selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                // TODO: avoid the use of private API
                selection.append(
                        Contacts._ID + " IN ("
                                + "SELECT DISTINCT " + RawContacts.CONTACT_ID
                                + " FROM raw_contacts"
                                + " WHERE " + RawContacts.ACCOUNT_TYPE + "=?"
                                + "   AND " + RawContacts.ACCOUNT_NAME + "=?)");
                selectionArgs.add(filter.accountType);
                selectionArgs.add(filter.accountName);
                break;
            }
            case ContactListFilter.FILTER_TYPE_GROUP: {
                selection.append(Data.MIMETYPE + "=?"
                        + " AND " + GroupMembership.GROUP_ROW_ID + "=?");
                selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
                selectionArgs.add(String.valueOf(filter.groupId));
                break;
            }
        }
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        final ContactListItemView view = (ContactListItemView)itemView;

        view.setHighlightedPrefix(getUpperCaseQueryString());

        if (isSelectionVisible()) {
            view.setActivated(isSelectedContact(partition, cursor));
        }

        bindSectionHeaderAndDivider(view, position);

        if (isQuickContactEnabled()) {
            bindQuickContact(view, partition, cursor);
        } else {
            bindPhoto(view, partition, cursor);
        }

        bindName(view, cursor);
        bindPresence(view, cursor);

        if (isSearchMode()) {
            bindSearchSnippet(view, cursor);
        }
    }

    private boolean isCustomFilterForPhoneNumbersOnly() {
        // TODO: this flag should not be stored in shared prefs.  It needs to be in the db.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return prefs.getBoolean(ContactsPreferences.PREF_DISPLAY_ONLY_PHONES,
                ContactsPreferences.PREF_DISPLAY_ONLY_PHONES_DEFAULT);
    }
}
