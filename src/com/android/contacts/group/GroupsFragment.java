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

package com.android.contacts.group;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;

import com.android.contacts.GroupListLoader;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountsLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads groups and group metadata for all accounts.
 */
public final class GroupsFragment extends Fragment implements AccountsLoader.AccountsListener {

    private static final int LOADER_GROUPS = 1;
    private static final int LOADER_ACCOUNTS = 2;

    /**
     * Callbacks for hosts of the {@link GroupsFragment}.
     */
    public interface GroupsListener  {

        /**
         * Invoked after groups and group metadata have been loaded.
         */
        void onGroupsLoaded(List<GroupListItem> groupListItems,
                boolean areGroupWritableAccountsAvailable);
    }

    private final LoaderManager.LoaderCallbacks<Cursor> mGroupListLoaderListener =
            new LoaderManager.LoaderCallbacks<Cursor>() {

                @Override
                public CursorLoader onCreateLoader(int id, Bundle args) {
                    return new GroupListLoader(getActivity());
                }

                @Override
                public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                    if (data == null) {
                        return;
                    }
                    mGroupListItems.clear();
                    for (int i = 0; i < data.getCount(); i++) {
                        if (data.moveToNext()) {
                            mGroupListItems.add(GroupUtil.getGroupListItem(data, i));
                        }
                    }
                    mGroupsLoaded = true;
                    notifyIfReady();
                }

                public void onLoaderReset(Loader<Cursor> loader) {
                }
            };

    private List<GroupListItem> mGroupListItems = new ArrayList<>();
    private boolean mHasGroupWritableAccounts = false;
    private boolean mGroupsLoaded = false;
    private boolean mAccountsLoaded = false;
    private GroupsListener mListener;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        AccountsLoader.loadAccounts(this, LOADER_ACCOUNTS,
                AccountTypeManager.AccountFilter.GROUPS_WRITABLE);
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupListLoaderListener);
    }

    public void setListener(GroupsListener listener) {
        mListener = listener;
    }

    @Override
    public void onAccountsLoaded(List<AccountInfo> accounts) {
        mHasGroupWritableAccounts = !accounts.isEmpty();
        mAccountsLoaded = true;
        notifyIfReady();
    }

    private void notifyIfReady() {
        if (mAccountsLoaded && mGroupsLoaded && mListener != null) {
            mListener.onGroupsLoaded(mGroupListItems, mHasGroupWritableAccounts);
        }
    }
}
