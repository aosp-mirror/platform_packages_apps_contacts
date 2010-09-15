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

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
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

    private boolean mContactsWithPhoneNumbersOnly;
    private boolean mVisibleContactsOnly;

    public DefaultContactListAdapter(Context context) {
        super(context);
    }

    public void setContactsWithPhoneNumbersOnly(boolean flag) {
        mContactsWithPhoneNumbersOnly = flag;
    }

    public void setVisibleContactsOnly(boolean flag) {
        mVisibleContactsOnly = flag;
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
        Uri uri;
        if (filter != null && filter.groupId != 0) {
            uri = Data.CONTENT_URI;
        } else {
            uri = Contacts.CONTENT_URI;
        }

        if (directoryId == Directory.DEFAULT && isSectionHeaderDisplayEnabled()) {
            uri = buildSectionIndexerUri(uri);
        }
        loader.setUri(uri);
    }

    protected void configureProjection(
            CursorLoader loader, long directoryId, ContactListFilter filter) {
        if (filter != null && filter.groupId != 0) {
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

        if (directoryId != Directory.DEFAULT || filter == null) {
            loader.setSelection(Contacts.IN_VISIBLE_GROUP + "=1");
            return;
        }

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();

        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
                List<ContactListFilter> filters = getAllFilters();
                for (ContactListFilter aFilter : filters) {
                    if (aFilter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                        if (selection.length() > 0) {
                            selection.append(" OR ");
                        }
                        selection.append("(");
                        if (aFilter.groupId != 0) {
                            // TODO: avoid the use of private API
                            // TODO: optimize the query
                            selection.append(
                                    Contacts._ID + " IN ("
                                            + "SELECT " + RawContacts.CONTACT_ID
                                            + " FROM view_data"
                                            + " WHERE " + Data.MIMETYPE + "=?"
                                            + "   AND " + GroupMembership.GROUP_ROW_ID + "=?)");
                            selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
                            selectionArgs.add(String.valueOf(aFilter.groupId));
                        } else {
                            // TODO: avoid the use of private API
                            selection.append(
                                    Contacts._ID + " IN ("
                                            + "SELECT " + RawContacts.CONTACT_ID
                                            + " FROM raw_contacts"
                                            + " WHERE " + RawContacts.ACCOUNT_TYPE + "=?"
                                            + "   AND " + RawContacts.ACCOUNT_NAME + "=?)");
                            selectionArgs.add(aFilter.accountType);
                            selectionArgs.add(aFilter.accountName);
                        }
                        selection.append(")");
                    }
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                if (mVisibleContactsOnly && mContactsWithPhoneNumbersOnly) {
                    selection.append(Contacts.IN_VISIBLE_GROUP + "=1"
                            + " AND " + Contacts.HAS_PHONE_NUMBER + "=1");
                } else if (mVisibleContactsOnly) {
                    selection.append(Contacts.IN_VISIBLE_GROUP + "=1");
                } else if (mContactsWithPhoneNumbersOnly) {
                    selection.append(Contacts.HAS_PHONE_NUMBER + "=1");
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT:
            case ContactListFilter.FILTER_TYPE_GROUP: {
                if (filter.groupId != 0) {
                    selection.append(Data.MIMETYPE + "=?"
                            + " AND " + GroupMembership.GROUP_ROW_ID + "=?");
                    selectionArgs.add(GroupMembership.CONTENT_ITEM_TYPE);
                    selectionArgs.add(String.valueOf(filter.groupId));
                } else {
                    // TODO: avoid the use of private API
                    selection.append(
                            Contacts._ID + " IN ("
                                    + "SELECT " + RawContacts.CONTACT_ID
                                    + " FROM raw_contacts"
                                    + " WHERE " + RawContacts.ACCOUNT_TYPE + "=?"
                                    + "   AND " + RawContacts.ACCOUNT_NAME + "=?)");
                    selectionArgs.add(filter.accountType);
                    selectionArgs.add(filter.accountName);
                }
                break;
            }
        }
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        final ContactListItemView view = (ContactListItemView)itemView;

        if (isSelectionVisible()) {
            view.setItemSelected(isSelectedContact(partition, cursor));
        }

        bindSectionHeaderAndDivider(view, position);

        if (isQuickContactEnabled()) {
            bindQuickContact(view, partition, cursor);
        } else {
            bindPhoto(view, cursor);
        }

        bindName(view, cursor);
        bindPresence(view, cursor);

        if (isSearchMode()) {
            bindSearchSnippet(view, cursor);
        }
    }
}
