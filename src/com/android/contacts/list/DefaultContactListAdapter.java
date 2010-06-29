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
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.view.View;

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
        Uri uri;

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
            uri = builder.build();
            loader.setProjection(FILTER_PROJECTION);
        } else {
            uri = Contacts.CONTENT_URI;
            loader.setProjection(PROJECTION);
        }

        if (directoryId == Directory.DEFAULT) {
            if (mVisibleContactsOnly && mContactsWithPhoneNumbersOnly) {
                loader.setSelection(Contacts.IN_VISIBLE_GROUP + "=1"
                        + " AND " + Contacts.HAS_PHONE_NUMBER + "=1");
            } else if (mVisibleContactsOnly) {
                loader.setSelection(Contacts.IN_VISIBLE_GROUP + "=1");
            } else if (mContactsWithPhoneNumbersOnly) {
                loader.setSelection(Contacts.HAS_PHONE_NUMBER + "=1");
            }
            if (isSectionHeaderDisplayEnabled()) {
                uri = buildSectionIndexerUri(uri);
            }
        }

        loader.setUri(uri);

        String sortOrder;
        if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            sortOrder = Contacts.SORT_KEY_PRIMARY;
        } else {
            sortOrder = Contacts.SORT_KEY_ALTERNATIVE;
        }

        loader.setSortOrder(sortOrder);
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        final ContactListItemView view = (ContactListItemView)itemView;

        bindSectionHeaderAndDivider(view, position);

        if (isQuickContactEnabled()) {
            bindQuickContact(view, cursor);
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
