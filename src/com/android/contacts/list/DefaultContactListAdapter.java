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

import android.app.patterns.CursorLoader;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
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
    public void configureLoader(CursorLoader loader) {
        Uri uri;

        if (isSearchMode() || isSearchResultsMode()) {
            String query = getQueryString();
            uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI,
                    TextUtils.isEmpty(query) ? "" : Uri.encode(query));
            loader.setProjection(FILTER_PROJECTION);
        } else {
            uri = Contacts.CONTENT_URI;
            loader.setProjection(PROJECTION);
        }

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

        loader.setUri(uri);

        if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(Contacts.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(Contacts.SORT_KEY_ALTERNATIVE);
        }
    }

    @Override
    public void bindView(View itemView, Context context, Cursor cursor) {
        final ContactListItemView view = (ContactListItemView)itemView;

        bindSectionHeaderAndDivider(view, cursor);

        if (isQuickContactEnabled()) {
            bindQuickContact(view, cursor);
        } else {
            bindPhoto(view, cursor);
        }

        bindName(view, cursor);
        bindPresence(view, cursor);

        if (isSearchMode() || isSearchResultsMode()) {
            bindSearchSnippet(view, cursor);
        }
    }
}
