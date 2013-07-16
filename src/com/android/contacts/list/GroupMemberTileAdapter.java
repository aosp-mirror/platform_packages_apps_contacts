/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.list;

import android.content.Context;
import android.database.Cursor;

import com.android.contacts.GroupMemberLoader;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.list.ContactTileAdapter;
import com.android.contacts.common.list.ContactTileView;
import com.google.common.collect.Lists;

import java.util.ArrayList;

/**
 * Tile adapter for groups.
 */
public class GroupMemberTileAdapter extends ContactTileAdapter {

    public GroupMemberTileAdapter(Context context, ContactTileView.Listener listener, int numCols) {
        super(context, listener, numCols, DisplayType.GROUP_MEMBERS);
    }

    @Override
    protected void bindColumnIndices() {
        mIdIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_ID;
        mLookupIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_LOOKUP_KEY;
        mPhotoUriIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_PHOTO_URI;
        mNameIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_DISPLAY_NAME_PRIMARY;
        mPresenceIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_PRESENCE_STATUS;
        mStatusIndex = GroupMemberLoader.GroupDetailQuery.CONTACT_STATUS;
    }

    @Override
    protected void saveNumFrequentsFromCursor(Cursor cursor) {
        mNumFrequents = 0;
    }

    @Override
    public int getItemViewType(int position) {
        return ViewTypes.STARRED;
    }

    @Override
    protected int getDividerPosition(Cursor cursor) {
        // No divider
        return -1;
    }

    @Override
    public int getCount() {
        if (mContactCursor == null || mContactCursor.isClosed()) {
            return 0;
        }

        return getRowCount(mContactCursor.getCount());
    }

    @Override
    public ArrayList<ContactEntry> getItem(int position) {
        final ArrayList<ContactEntry> resultList = Lists.newArrayListWithCapacity(mColumnCount);
        int contactIndex = position * mColumnCount;

        for (int columnCounter = 0; columnCounter < mColumnCount; columnCounter++) {
            resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
            contactIndex++;
        }
        return resultList;
    }
}
