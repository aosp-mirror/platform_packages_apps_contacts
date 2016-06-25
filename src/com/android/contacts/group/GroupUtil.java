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

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.text.TextUtils;
import android.widget.ImageView;

import com.android.contacts.GroupListLoader;
import com.android.contacts.activities.GroupMembersActivity;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.list.ContactsSectionIndexer;
import com.android.contacts.common.model.account.GoogleAccountType;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Group utility methods.
 */
public final class GroupUtil {

    private static final String LEGACY_CONTACTS_AUTHORITY = "contacts";
    private static final String LEGACY_CONTACTS_URI = "content://contacts/groups";

    // System IDs of FFC groups in Google accounts
    private static final Set<String> FFC_GROUPS =
            new HashSet(Arrays.asList("Friends", "Family", "Coworkers"));

    private GroupUtil() {
    }

    /** Returns a {@link GroupListItem} read from the given cursor and position. */
    static GroupListItem getGroupListItem(Cursor cursor, int position) {
        if (cursor == null || cursor.isClosed() || !cursor.moveToPosition(position)) {
            return null;
        }
        String accountName = cursor.getString(GroupListLoader.ACCOUNT_NAME);
        String accountType = cursor.getString(GroupListLoader.ACCOUNT_TYPE);
        String dataSet = cursor.getString(GroupListLoader.DATA_SET);
        long groupId = cursor.getLong(GroupListLoader.GROUP_ID);
        String title = cursor.getString(GroupListLoader.TITLE);
        int memberCount = cursor.getInt(GroupListLoader.MEMBER_COUNT);
        boolean isReadOnly = cursor.getInt(GroupListLoader.IS_READ_ONLY) == 1;
        String systemId = cursor.getString(GroupListLoader.SYSTEM_ID);

        // Figure out if this is the first group for this account name / account type pair by
        // checking the previous entry. This is to determine whether or not we need to display an
        // account header in this item.
        int previousIndex = position - 1;
        boolean isFirstGroupInAccount = true;
        if (previousIndex >= 0 && cursor.moveToPosition(previousIndex)) {
            String previousGroupAccountName = cursor.getString(GroupListLoader.ACCOUNT_NAME);
            String previousGroupAccountType = cursor.getString(GroupListLoader.ACCOUNT_TYPE);
            String previousGroupDataSet = cursor.getString(GroupListLoader.DATA_SET);

            if (accountName.equals(previousGroupAccountName) &&
                    accountType.equals(previousGroupAccountType) &&
                    Objects.equal(dataSet, previousGroupDataSet)) {
                isFirstGroupInAccount = false;
            }
        }

        return new GroupListItem(accountName, accountType, dataSet, groupId, title,
                isFirstGroupInAccount, memberCount, isReadOnly, systemId);
    }

    /**
     * @param identifier the {@link ContactPhotoManager.DefaultImageRequest#identifier}
     *         to use for this the group member.
     */
    public static void bindPhoto(ContactPhotoManager photoManager, ImageView imageView,
            long photoId, Uri photoUri, String displayName, String identifier) {
        if (photoId == 0) {
            final DefaultImageRequest defaultImageRequest = photoUri == null
                    ? new DefaultImageRequest(displayName, identifier,
                            /* circularPhotos */ true)
                    : null;
            photoManager.loadDirectoryPhoto(imageView, photoUri, /* darkTheme */ false,
                        /* isCircular */ true, defaultImageRequest);
        } else {
            photoManager.loadThumbnail(imageView, photoId, /* darkTheme */ false,
                        /* isCircular */ true, /* defaultImageRequest */ null);
        }
    }

    /** Returns an Intent to create a new group. */
    public static Intent createAddGroupIntent(Context context) {
        final Intent intent = new Intent(context, GroupMembersActivity.class);
        intent.setAction(Intent.ACTION_INSERT);
        return intent;
    }

    /** Returns an Intent to view the details of the group identified by the given ID. */
    public static Intent createViewGroupIntent(Context context, long groupId) {
        final Intent intent = new Intent(context, GroupMembersActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(ContentUris.withAppendedId(Groups.CONTENT_URI, groupId));
        return intent;
    }

    /**
     * Converts the given group Uri to the legacy format if the legacy authority was specified
     * in the given Uri.
     */
    // TODO(wjang):
    public static Uri maybeConvertToLegacyUri(Uri groupUri) {
        final String requestAuthority = groupUri.getAuthority();
        if (!LEGACY_CONTACTS_AUTHORITY.equals(requestAuthority)) {
            return groupUri;
        }
        final long groupId = ContentUris.parseId(groupUri);
        final Uri legacyContentUri = Uri.parse(LEGACY_CONTACTS_URI);
        return ContentUris.withAppendedId(legacyContentUri, groupId);
    }

    /**
     * Returns true if it's an empty and read-only group of a Google account and the system ID of
     * the group is one of "Friends", "Family" and "Coworkers".
     */
    public static boolean isEmptyFFCGroup(GroupListItem groupListItem) {
        return GoogleAccountType.ACCOUNT_TYPE.equals(groupListItem.getAccountType())
                && groupListItem.isReadOnly()
                && isSystemIdFFC(groupListItem.getSystemId())
                && (groupListItem.getMemberCount() <= 0);
    }

    private static boolean isSystemIdFFC(String systemId) {
        return !TextUtils.isEmpty(systemId) && FFC_GROUPS.contains(systemId);
    }

    /**
     * Sort groups alphabetically and in a localized way.
     */
    public static String getGroupsSortOrder() {
        return Groups.TITLE + " COLLATE LOCALIZED ASC";
    }

    /**
     * The sum of the last element in counts[] and the last element in positions[] is the total
     * number of remaining elements in cursor. If count is more than what's in the indexer now,
     * then we don't need to trim.
     */
    public static boolean needTrimming(int count, int[] counts, int[] positions) {
        // The sum of the last element in counts[] and the last element in positions[] is
        // the total number of remaining elements in cursor. If mCount is more than
        // what's in the indexer now, then we don't need to trim.
        return positions.length > 0 && counts.length > 0
                && count <= (counts[counts.length - 1] + positions[positions.length - 1]);
    }

    /**
     * Update Bundle extras so as to update indexer.
     */
    public static void updateBundle(Bundle bundle, ContactsSectionIndexer indexer,
            List<Integer> subscripts, String[] sections, int[] counts) {
        for (int i : subscripts) {
            final int filteredContact = indexer.getSectionForPosition(i);
            if (filteredContact < counts.length && filteredContact >= 0) {
                counts[filteredContact]--;
                if (counts[filteredContact] == 0) {
                    sections[filteredContact] = "";
                }
            }
        }
        final String[] newSections = clearEmptyString(sections);
        bundle.putStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES, newSections);
        final int[] newCounts = clearZeros(counts);
        bundle.putIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS, newCounts);
    }

    private static String[] clearEmptyString(String[] strings) {
        final List<String> list = new ArrayList<>();
        for (String s : strings) {
            if (!TextUtils.isEmpty(s)) {
                list.add(s);
            }
        }
        return list.toArray(new String[list.size()]);
    }

    private static int[] clearZeros(int[] numbers) {
        final List<Integer> list = new ArrayList<>();
        for (int n : numbers) {
            if (n > 0) {
                list.add(n);
            }
        }
        final int[] array = new int[list.size()];
        for(int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}