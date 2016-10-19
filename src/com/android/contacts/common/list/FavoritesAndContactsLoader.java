/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.contacts.common.list;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

import com.android.contacts.common.Experiments;
import com.android.contactsbind.ObjectFactory;
import com.android.contactsbind.experiments.Flags;
import com.android.contactsbind.search.AutocompleteHelper;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A loader for use in the default contact list, which will also query for favorite contacts
 * if configured to do so.
 */
public class FavoritesAndContactsLoader extends CursorLoader implements AutocompleteHelper.Listener {

    private boolean mLoadFavorites;

    private String[] mProjection;

    private String mAutocompleteQuery;
    private CountDownLatch mAutocompleteLatch = new CountDownLatch(1);
    private Cursor mAutocompleteCursor;
    private int mAutocompleteTimeout;

    public FavoritesAndContactsLoader(Context context) {
        super(context);
        mAutocompleteTimeout = Flags.getInstance().getInteger(
                Experiments.SEARCH_YENTA_TIMEOUT_MILLIS);
    }

    /** Whether to load favorites and merge results in before any other results. */
    public void setLoadFavorites(boolean flag) {
        mLoadFavorites = flag;
    }

    public void setAutocompleteQuery(String autocompleteQuery) {
        mAutocompleteQuery = autocompleteQuery;
    }

    public void setProjection(String[] projection) {
        super.setProjection(projection);
        mProjection = projection;
    }

    @Override
    public Cursor loadInBackground() {
        List<Cursor> cursors = Lists.newArrayList();
        if (mLoadFavorites) {
            cursors.add(loadFavoritesContacts());
        }

        if (mAutocompleteQuery != null) {
            final AutocompleteHelper autocompleteHelper =
                    ObjectFactory.getAutocompleteHelper(getContext());
            if (autocompleteHelper != null) {
                autocompleteHelper.setListener(this);
                autocompleteHelper.setProjection(mProjection);
                autocompleteHelper.setQuery(mAutocompleteQuery);
                try {
                    if (!mAutocompleteLatch.await(mAutocompleteTimeout, TimeUnit.MILLISECONDS)) {
                        logw("Timeout expired before receiving autocompletions");
                    }
                } catch (InterruptedException e) {
                    logw("Interrupted while waiting for autocompletions");
                }
                if (mAutocompleteCursor != null) {
                    cursors.add(mAutocompleteCursor);
                    // TODO: exclude these results from the main loader results, see b/30742359
                }
            }
        }

        // TODO: if the autocomplete experiment in on, only show those results even if they're empty
        final Cursor contactsCursor = mAutocompleteQuery == null ? loadContacts() : null;
        if (mAutocompleteQuery == null) {
            cursors.add(contactsCursor);
        }
        // Guard against passing an empty array to the MergeCursor constructor
        if (cursors.isEmpty()) cursors.add(null);

        return new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) {
            @Override
            public Bundle getExtras() {
                // Need to get the extras from the contacts cursor.
                return contactsCursor == null ? new Bundle() : contactsCursor.getExtras();
            }
        };
    }

    private Cursor loadContacts() {
        // ContactsCursor.loadInBackground() can return null; MergeCursor
        // correctly handles null cursors.
        try {
            return super.loadInBackground();
        } catch (NullPointerException | SecurityException e) {
            // Ignore NPEs and SecurityExceptions thrown by providers
        }
        return null;
    }

    private Cursor loadFavoritesContacts() {
        final StringBuilder selection = new StringBuilder();
        selection.append(Contacts.STARRED + "=?");
        final ContactListFilter filter =
                ContactListFilterController.getInstance(getContext()).getFilter();
        if (filter != null && filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            selection.append(" AND ").append(Contacts.IN_VISIBLE_GROUP + "=1");
        }
        return getContext().getContentResolver().query(
                Contacts.CONTENT_URI, mProjection, selection.toString(), new String[]{"1"},
                getSortOrder());
    }

    @Override
    public void onAutocompletesAvailable(Cursor cursor) {
        if (cursor != null && cursor.getCount() > 0) {
            mAutocompleteCursor = cursor;
            mAutocompleteLatch.countDown();
        }
    }

    private static void logw(String message) {
        if (Log.isLoggable(AutocompleteHelper.TAG, Log.WARN)) {
            Log.w(AutocompleteHelper.TAG, message);
        }
    }
}
