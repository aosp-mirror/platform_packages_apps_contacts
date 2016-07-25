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
import android.util.Log;

import com.android.contacts.GroupListLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads groups and group metadata for all accounts.
 */
public final class GroupsFragment extends Fragment {

    private static final String TAG = GroupsFragment.class.getSimpleName();

    private static final int LOADER_GROUPS = 1;

    /**
     * Callbacks for hosts of the {@link GroupsFragment}.
     */
    public interface GroupsListener  {

        /**
         * Invoked after groups and group metadata have been loaded.
         */
        void onGroupsLoaded(List<GroupListItem> groupListItems);
    }

    private final LoaderManager.LoaderCallbacks<Cursor> mGroupListLoaderListener =
            new LoaderManager.LoaderCallbacks<Cursor>() {

                @Override
                public CursorLoader onCreateLoader(int id, Bundle args) {
                    return new GroupListLoader(getActivity());
                }

                @Override
                public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                    final List<GroupListItem> newGroupListItems = new ArrayList<>();
                    for (int i = 0; i < data.getCount(); i++) {
                        if (data.moveToNext()) {
                            newGroupListItems.add(GroupUtil.getGroupListItem(data, i));
                        }
                    }

                    if (mGroupListItems.equals(newGroupListItems)) {
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "The same groups loaded, returning.");
                        }
                        return;
                    }

                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "New group(s) loaded.");
                    }

                    mGroupListItems.clear();
                    mGroupListItems.addAll(newGroupListItems);
                    if (mListener != null) {
                        mListener.onGroupsLoaded(mGroupListItems);
                    }
                }

                public void onLoaderReset(Loader<Cursor> loader) {
                }
            };

    private List<GroupListItem> mGroupListItems = new ArrayList<>();
    private GroupsListener mListener;

    @Override
    public void onStart() {
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupListLoaderListener);
        super.onStart();
    }

    public void setListener(GroupsListener listener) {
        mListener = listener;
    }
}
