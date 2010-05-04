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
import android.view.ViewGroup;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 */
public class DefaultContactListAdapter extends ContactListAdapter {

    public DefaultContactListAdapter(Context context) {
        super(context);
    }

    @Override
    public void configureLoader(CursorLoader loader) {
        Uri uri;
        if (isSearchMode() || isSearchResultsMode()) {
            String query = getQueryString();
            uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI,
                    TextUtils.isEmpty(query) ? "" : Uri.encode(query));
            loader.setProjection(CONTACTS_SUMMARY_FILTER_PROJECTION);
            if (!isSearchResultsMode()) {
                loader.setSelection(Contacts.IN_VISIBLE_GROUP + "=1");
            }
        } else {
            uri = Contacts.CONTENT_URI;
            loader.setProjection(CONTACTS_SUMMARY_PROJECTION);
            loader.setSelection(Contacts.IN_VISIBLE_GROUP + "=1");
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
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(getUnknownNameText());
        view.setTextWithHighlightingFactory(getTextWithHighlightingFactory());
        return view;
    }

    @Override
    public void bindView(View itemView, Context context, Cursor cursor) {
        final ContactListItemView view = (ContactListItemView)itemView;

        bindSectionHeaderAndDivider(view, cursor);
        bindQuickContact(view, cursor);
        bindName(view, cursor);
        bindPresence(view, cursor);

        if (isSearchMode() || isSearchResultsMode()) {
            bindSearchSnippet(view, cursor);
        }
    }
}
