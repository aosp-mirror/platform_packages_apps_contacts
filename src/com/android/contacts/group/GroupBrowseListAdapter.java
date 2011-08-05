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

package com.android.contacts.group;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.GroupListLoader;
import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.android.internal.util.Objects;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter to populate the list of groups.
 */
public class GroupBrowseListAdapter extends BaseAdapter {

    private static final int MAX_ICONS_PER_GROUP_ROW = 4;

    private static final String[] PROJECTION_GROUP_MEMBERSHIP_INFO = new String[] {
        GroupMembership._ID,
        GroupMembership.PHOTO_ID
    };
    private static final int GROUP_MEMBERSHIP_COLUMN_PHOTO_ID = 1;

    /**
     * Arguments for asynchronous photo ID loading. See {@link AsyncPhotoIdLoadTask}
     */
    private static class AsyncPhotoIdLoadArg {
        public final View icons;
        public final long groupId;
        public final Map<Long, ArrayList<Long>> groupPhotoIdMap;
        public final ContentResolver contentResolver;
        public final ContactPhotoManager contactPhotoManager;

        public AsyncPhotoIdLoadArg(
                View icons, long groupId, Map<Long, ArrayList<Long>> groupPhotoIdMap,
                ContentResolver contentResolver, ContactPhotoManager contactPhotoManager) {
            this.icons = icons;
            this.groupId = groupId;
            this.groupPhotoIdMap = groupPhotoIdMap;
            this.contentResolver = contentResolver;
            this.contactPhotoManager = contactPhotoManager;
        }
    }

    /**
     * Loads photo IDs associated with a group ID supplied from {@link AsyncPhotoIdLoadArg#groupId},
     * storing them in {@link GroupBrowseListAdapter#mGroupPhotoIdMap}.
     *
     * This AsyncTask also remembers a View which is associated with the group ID at the moment it
     * is initiated (we use {@link View#setTag(Object) and View#getTag() to associate them}. If the
     * View is still associated with the group ID after the asynchronous photo ID load, this class
     * also asks {@link ContactPhotoManager} to load actual photo contents. Its parent (typically
     * ListView) may reuse Views for different group IDs, so the photo content load often don't
     * occur.
     */
    private static class AsyncPhotoIdLoadTask extends
            AsyncTask<AsyncPhotoIdLoadArg, Void, ArrayList<Long>> {

        private View mIcons;
        private long mGroupId;
        private Map<Long, ArrayList<Long>> mGroupPhotoIdMap;
        private ContentResolver mContentResolver;
        private ContactPhotoManager mContactPhotoManager;

        @Override
        protected ArrayList<Long> doInBackground(AsyncPhotoIdLoadArg... params) {
            final AsyncPhotoIdLoadArg arg = params[0];
            mIcons = arg.icons;
            mGroupId = arg.groupId;
            mGroupPhotoIdMap = arg.groupPhotoIdMap;
            mContentResolver = arg.contentResolver;
            mContactPhotoManager = arg.contactPhotoManager;

            // Multiple requests for one group ID is possible. We just ignore duplicates,
            // assuming query results won't change.
            if (mGroupPhotoIdMap.containsKey(mGroupId)) {
                return null;
            }

            final ArrayList<Long> photoIds = new ArrayList<Long>(MAX_ICONS_PER_GROUP_ROW);
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(Data.CONTENT_URI,
                        PROJECTION_GROUP_MEMBERSHIP_INFO,
                        GroupMembership.MIMETYPE + "=? AND "
                                + GroupMembership.PHOTO_ID + " IS NOT NULL AND "
                                + GroupMembership.GROUP_ROW_ID + "=?",
                        new String[] { GroupMembership.CONTENT_ITEM_TYPE,
                                String.valueOf(mGroupId) }, null);
                if (cursor != null) {
                    int count = 0;
                    while (cursor.moveToNext() && count < MAX_ICONS_PER_GROUP_ROW) {
                        photoIds.add(cursor.getLong(GROUP_MEMBERSHIP_COLUMN_PHOTO_ID));
                        count++;
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return photoIds;
        }

        @Override
        protected void onPostExecute(ArrayList<Long> photoIds) {
            if (photoIds == null) {
                return;
            }

            mGroupPhotoIdMap.put(mGroupId, photoIds);

            final View icons = mIcons;
            // If the original group ID, which was supplied when this AsyncTask was executed, is
            // consistent with the ID inside mArgs, it means the View isn't reused by the
            // other groups, and thus we can assume these Views are available for the group ID.
            final Long currentGroupId = (Long) icons.getTag();
            if (currentGroupId == mGroupId) {
                final ImageView[] children = getIconViewsSordedByFillOrder(icons);
                for (int i = 0; i < children.length; i++) {
                    if (i < photoIds.size()) {
                        mContactPhotoManager.loadPhoto(children[i], photoIds.get(i));
                    } else {
                        mContactPhotoManager.removePhoto(children[i]);
                    }
                }
            }
        }
    }

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final AccountTypeManager mAccountTypeManager;

    private final Map<Long, ArrayList<Long>> mGroupPhotoIdMap =
            new ConcurrentHashMap<Long, ArrayList<Long>>();

    private final ContactPhotoManager mContactPhotoManager;

    private Cursor mCursor;

    private boolean mSelectionVisible;
    private Uri mSelectedGroupUri;

    public GroupBrowseListAdapter(Context context) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mAccountTypeManager = AccountTypeManager.getInstance(mContext);
        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
    }

    public void setCursor(Cursor cursor) {
        mCursor = cursor;

        // If there's no selected group already and the cursor is valid, then by default, select the
        // first group
        if (mSelectedGroupUri == null && cursor != null && cursor.getCount() > 0) {
            GroupListItem firstItem = getItem(0);
            long groupId = (firstItem == null) ? null : firstItem.getGroupId();
            mSelectedGroupUri = getGroupUriFromId(groupId);
        }

        notifyDataSetChanged();
    }

    public int getSelectedGroupPosition() {
        if (mSelectedGroupUri == null || mCursor == null || mCursor.getCount() == 0) {
            return -1;
        }

        int index = 0;
        mCursor.moveToPosition(-1);
        while (mCursor.moveToNext()) {
            long groupId = mCursor.getLong(GroupListLoader.GROUP_ID);
            Uri uri = getGroupUriFromId(groupId);
            if (mSelectedGroupUri.equals(uri)) {
                  return index;
            }
            index++;
        }
        return -1;
    }

    public void setSelectionVisible(boolean flag) {
        mSelectionVisible = flag;
    }

    public void setSelectedGroup(Uri groupUri) {
        mSelectedGroupUri = groupUri;
    }

    private boolean isSelectedGroup(Uri groupUri) {
        return mSelectedGroupUri != null && mSelectedGroupUri.equals(groupUri);
    }

    public Uri getSelectedGroup() {
        return mSelectedGroupUri;
    }

    @Override
    public int getCount() {
        return mCursor == null ? 0 : mCursor.getCount();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public GroupListItem getItem(int position) {
        if (mCursor == null || mCursor.isClosed() || !mCursor.moveToPosition(position)) {
            return null;
        }
        String accountName = mCursor.getString(GroupListLoader.ACCOUNT_NAME);
        String accountType = mCursor.getString(GroupListLoader.ACCOUNT_TYPE);
        String dataSet = mCursor.getString(GroupListLoader.DATA_SET);
        long groupId = mCursor.getLong(GroupListLoader.GROUP_ID);
        String title = mCursor.getString(GroupListLoader.TITLE);
        int memberCount = mCursor.getInt(GroupListLoader.MEMBER_COUNT);
        int groupCountForThisAccount = mCursor.getInt(GroupListLoader.GROUP_COUNT_PER_ACCOUNT);

        // Figure out if this is the first group for this account name / account type pair by
        // checking the previous entry. This is to determine whether or not we need to display an
        // account header in this item.
        int previousIndex = position - 1;
        boolean isFirstGroupInAccount = true;
        if (previousIndex >= 0 && mCursor.moveToPosition(previousIndex)) {
            String previousGroupAccountName = mCursor.getString(GroupListLoader.ACCOUNT_NAME);
            String previousGroupAccountType = mCursor.getString(GroupListLoader.ACCOUNT_TYPE);
            String previousGroupDataSet = mCursor.getString(GroupListLoader.DATA_SET);

            if (accountName.equals(previousGroupAccountName) &&
                    accountType.equals(previousGroupAccountType) &&
                    Objects.equal(dataSet, previousGroupDataSet)) {
                isFirstGroupInAccount = false;
            }
        }

        return new GroupListItem(accountName, accountType, dataSet, groupId, title,
                isFirstGroupInAccount, memberCount, groupCountForThisAccount);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        GroupListItem entry = getItem(position);
        View result;
        GroupListItemViewCache viewCache;
        if (convertView != null) {
            result = convertView;
            viewCache = (GroupListItemViewCache) result.getTag();
        } else {
            result = mLayoutInflater.inflate(R.layout.group_browse_list_item, parent, false);
            viewCache = new GroupListItemViewCache(result);
            result.setTag(viewCache);
        }

        // Add a header if this is the first group in an account and hide the divider
        if (entry.isFirstGroupInAccount()) {
            bindHeaderView(entry, viewCache);
            viewCache.accountHeader.setVisibility(View.VISIBLE);
            viewCache.divider.setVisibility(View.GONE);
        } else {
            viewCache.accountHeader.setVisibility(View.GONE);
            viewCache.divider.setVisibility(View.VISIBLE);
        }

        // Bind the group data
        Uri groupUri = getGroupUriFromId(entry.getGroupId());
        String memberCountString = mContext.getResources().getQuantityString(
                R.plurals.group_list_num_contacts_in_group, entry.getMemberCount(),
                entry.getMemberCount());
        viewCache.setUri(groupUri);
        viewCache.groupTitle.setText(entry.getTitle());
        viewCache.groupMemberCount.setText(memberCountString);

        final View icons = result.findViewById(R.id.icons);
        final ImageView[] children = getIconViewsSordedByFillOrder(icons);
        final ArrayList<Long> photoIds = mGroupPhotoIdMap.get(entry.getGroupId());

        // Let the icon holder remember its associated group ID.
        // Each AsyncTask loading photo IDs will compare this ID with the AsyncTask's argument, and
        // check if the bound View is reused by the other list items or not. If the View is reused,
        // the group ID set here will be overridden by the new owner, thus ID inconsistency happens.
        icons.setTag(entry.getGroupId());
        if (photoIds != null) {
            // Cache is available. Let the photo manager load those IDs.
            for (int i = 0; i < children.length; i++) {
                if (i < photoIds.size()) {
                    mContactPhotoManager.loadPhoto(children[i], photoIds.get(i));
                } else {
                    mContactPhotoManager.removePhoto(children[i]);
                }
            }
        } else {
            // Cache is not available. Load photo IDs asynchronously.
            for (ImageView child : children) {
                mContactPhotoManager.removePhoto(child);
            }
            new AsyncPhotoIdLoadTask().execute(
                    new AsyncPhotoIdLoadArg(icons, entry.getGroupId(),
                            mGroupPhotoIdMap, mContext.getContentResolver(),
                            mContactPhotoManager));
        }

        if (mSelectionVisible) {
            result.setActivated(isSelectedGroup(groupUri));
        }
        return result;
    }

    private void bindHeaderView(GroupListItem entry, GroupListItemViewCache viewCache) {
        AccountType accountType = mAccountTypeManager.getAccountType(
                entry.getAccountType(), entry.getDataSet());
        viewCache.accountType.setText(accountType.getDisplayLabel(mContext).toString());
        viewCache.accountName.setText(entry.getAccountName());

        int count = entry.getGroupCountForThisAccount();
        viewCache.groupCountForAccount.setText(mContext.getResources().getQuantityString(
                R.plurals.num_groups_in_account, count, count));
    }

    private static Uri getGroupUriFromId(long groupId) {
        return ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);
    }

    /**
     * Get ImageView objects inside the given View, sorted by the order photos should be filled.
     */
    private static ImageView[] getIconViewsSordedByFillOrder(View icons) {
        final ImageView[] children = new ImageView[] {
                (ImageView) icons.findViewById(R.id.icon_4),
                (ImageView) icons.findViewById(R.id.icon_2),
                (ImageView) icons.findViewById(R.id.icon_3),
                (ImageView) icons.findViewById(R.id.icon_1)
        };
        return children;
    }

    /**
     * Cache of the children views of a contact detail entry represented by a
     * {@link GroupListItem}
     */
    public static class GroupListItemViewCache {
        public final TextView accountType;
        public final TextView accountName;
        public final TextView groupCountForAccount;
        public final TextView groupTitle;
        public final TextView groupMemberCount;
        public final View accountHeader;
        public final View divider;
        private Uri mUri;

        public GroupListItemViewCache(View view) {
            accountType = (TextView) view.findViewById(R.id.account_type);
            accountName = (TextView) view.findViewById(R.id.account_name);
            groupCountForAccount = (TextView) view.findViewById(R.id.group_count);
            groupTitle = (TextView) view.findViewById(R.id.label);
            groupMemberCount = (TextView) view.findViewById(R.id.count);
            accountHeader = view.findViewById(R.id.group_list_header);
            divider = view.findViewById(R.id.divider);
        }

        public void setUri(Uri uri) {
            mUri = uri;
        }

        public Uri getUri() {
            return mUri;
        }
    }
}