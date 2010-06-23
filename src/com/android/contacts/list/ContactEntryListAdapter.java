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

import com.android.contacts.ContactPhotoLoader;
import com.android.contacts.ContactsSectionIndexer;
import com.android.contacts.R;
import com.android.contacts.widget.IndexerListAdapter;
import com.android.contacts.widget.TextWithHighlightingFactory;

import android.content.Context;
import android.content.CursorLoader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashSet;

/**
 * Common base class for various contact-related lists, e.g. contact list, phone number list
 * etc.
 */
public abstract class ContactEntryListAdapter extends IndexerListAdapter {

    private static final String TAG = "ContactEntryListAdapter";

    private static final class DirectoryQuery {
        public static final Uri URI = Directory.CONTENT_URI;
        public static final String ORDER_BY = Directory._ID;

        public static final String[] PROJECTION = {
            Directory._ID,
            Directory.PACKAGE_NAME,
            Directory.TYPE_RESOURCE_ID,
            Directory.DISPLAY_NAME,
        };

        public static final int ID = 0;
        public static final int PACKAGE_NAME = 1;
        public static final int TYPE_RESOURCE_ID = 2;
        public static final int DISPLAY_NAME = 3;
    }

    /**
     * The animation is used here to allocate animated name text views.
     */
    private TextWithHighlightingFactory mTextWithHighlightingFactory;
    private int mDisplayOrder;
    private int mSortOrder;
    private boolean mNameHighlightingEnabled;

    private boolean mDisplayPhotos;
    private ContactPhotoLoader mPhotoLoader;

    private String mQueryString;
    private boolean mSearchMode;
    private boolean mSearchResultsMode;

    private boolean mLoading = true;
    private boolean mEmptyListEnabled = true;

    public ContactEntryListAdapter(Context context) {
        super(context, R.layout.list_section, R.id.header_text);
        addPartitions();
    }

    protected void addPartitions() {
        addPartition(createDefaultDirectoryPartition());
    }

    protected DirectoryPartition createDefaultDirectoryPartition() {
        DirectoryPartition partition = new DirectoryPartition(true, true);
        partition.setDirectoryId(Directory.DEFAULT);
        partition.setDirectoryType(getContext().getString(R.string.contactsList));
        partition.setPriorityDirectory(true);
        return partition;
    }

    private int getPartitionByDirectoryId(long id) {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                if (((DirectoryPartition)partition).getDirectoryId() == id) {
                    return i;
                }
            }
        }
        return -1;
    }

    public abstract String getContactDisplayName(int position);
    public abstract void configureLoader(CursorLoader loader, long directoryId);

    /**
     * Marks all partitions as "loading"
     */
    public void onDataReload() {
        boolean notify = false;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                if (!directoryPartition.isLoading()) {
                    directoryPartition.setLoading(true);
                    notify = true;
                }
            }
        }
        if (notify) {
            notifyDataSetChanged();
        }
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        mSearchMode = flag;
    }

    public boolean isSearchResultsMode() {
        return mSearchResultsMode;
    }

    public void setSearchResultsMode(boolean searchResultsMode) {
        mSearchResultsMode = searchResultsMode;
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String queryString) {
        mQueryString = queryString;
    }

    public int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    public void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
    }

    public void setNameHighlightingEnabled(boolean flag) {
        mNameHighlightingEnabled = flag;
    }

    public boolean isNameHighlightingEnabled() {
        return mNameHighlightingEnabled;
    }

    public void setTextWithHighlightingFactory(TextWithHighlightingFactory factory) {
        mTextWithHighlightingFactory = factory;
    }

    protected TextWithHighlightingFactory getTextWithHighlightingFactory() {
        return mTextWithHighlightingFactory;
    }

    public void setPhotoLoader(ContactPhotoLoader photoLoader) {
        mPhotoLoader = photoLoader;
    }

    protected ContactPhotoLoader getPhotoLoader() {
        return mPhotoLoader;
    }

    public boolean getDisplayPhotos() {
        return mDisplayPhotos;
    }

    public void setDisplayPhotos(boolean displayPhotos) {
        mDisplayPhotos = displayPhotos;
    }

    public boolean isEmptyListEnabled() {
        return mEmptyListEnabled;
    }

    public void setEmptyListEnabled(boolean flag) {
        mEmptyListEnabled = flag;
    }

    public void configureDirectoryLoader(CursorLoader loader) {
        loader.setUri(DirectoryQuery.URI);
        loader.setProjection(DirectoryQuery.PROJECTION);
        loader.setSortOrder(DirectoryQuery.ORDER_BY);
    }

    /**
     * Updates partitions according to the directory meta-data contained in the supplied
     * cursor.  Takes ownership of the cursor and will close it.
     */
    public void changeDirectories(Cursor cursor) {
        HashSet<Long> directoryIds = new HashSet<Long>();

        // TODO preserve the order of partition to match those of the cursor
        // Phase I: add new directories
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(DirectoryQuery.ID);
                directoryIds.add(id);
                if (getPartitionByDirectoryId(id) == -1) {
                    DirectoryPartition partition = createDirectoryPartition(cursor);
                    addPartition(partition);
                }
            }
        } finally {
            cursor.close();
        }

        // Phase II: remove deleted directories
        int count = getPartitionCount();
        for (int i = count; --i >= 0; ) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                long id = ((DirectoryPartition)partition).getDirectoryId();
                if (!directoryIds.contains(id)) {
                    removePartition(i);
                }
            }
        }

        invalidate();
        notifyDataSetChanged();
    }

    private DirectoryPartition createDirectoryPartition(Cursor cursor) {
        PackageManager pm = getContext().getPackageManager();
        DirectoryPartition partition = new DirectoryPartition(false, true);
        partition.setDirectoryId(cursor.getLong(DirectoryQuery.ID));
        String packageName = cursor.getString(DirectoryQuery.PACKAGE_NAME);
        int typeResourceId = cursor.getInt(DirectoryQuery.TYPE_RESOURCE_ID);
        if (!TextUtils.isEmpty(packageName) && typeResourceId != 0) {
            // TODO: should this be done on a background thread?
            try {
                partition.setDirectoryType(pm.getResourcesForApplication(packageName)
                        .getString(typeResourceId));
            } catch (Exception e) {
                Log.e(TAG, "Cannot obtain directory type from package: " + packageName);
            }
        }
        partition.setDisplayName(cursor.getString(DirectoryQuery.DISPLAY_NAME));
        return partition;
    }

    @Override
    public void changeCursor(int partitionIndex, Cursor cursor) {
        Partition partition = getPartition(partitionIndex);
        if (partition instanceof DirectoryPartition) {
            ((DirectoryPartition)partition).setLoading(false);
        }

        super.changeCursor(partitionIndex, cursor);

        if (isSectionHeaderDisplayEnabled() && partitionIndex == getIndexedPartition()) {
            updateIndexer(cursor);
        }
    }

    public void changeCursor(Cursor cursor) {
        changeCursor(0, cursor);
    }

    /**
     * Updates the indexer, which is used to produce section headers.
     */
    private void updateIndexer(Cursor cursor) {
        if (cursor == null) {
            setIndexer(null);
            return;
        }

        Bundle bundle = cursor.getExtras();
        if (bundle.containsKey(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)) {
            String sections[] =
                    bundle.getStringArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            int counts[] = bundle.getIntArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            setIndexer(new ContactsSectionIndexer(sections, counts));
        } else {
            setIndexer(null);
        }
    }

    @Override
    public boolean isEmpty() {
        // TODO
//        if (contactsListActivity.mProviderStatus != ProviderStatus.STATUS_NORMAL) {
//            return true;
//        }

        if (!mEmptyListEnabled) {
            return false;
        } else if (isSearchMode()) {
            return TextUtils.isEmpty(getQueryString());
        } else if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return super.isEmpty();
        }
    }

    /**
     * Changes visibility parameters for the default directory partition.
     */
    public void configureDefaultPartition(boolean showIfEmpty, boolean hasHeader) {
        int defaultPartitionIndex = -1;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition &&
                    ((DirectoryPartition)partition).getDirectoryId() == Directory.DEFAULT) {
                defaultPartitionIndex = i;
                break;
            }
        }
        if (defaultPartitionIndex != -1) {
            setShowIfEmpty(defaultPartitionIndex, showIfEmpty);
            setHasHeader(defaultPartitionIndex, hasHeader);
        }
    }

    @Override
    protected View newHeaderView(Context context, int partition, Cursor cursor,
            ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(R.layout.directory_header, parent, false);
    }

    @Override
    protected void bindHeaderView(View view, int partitionIndex, Cursor cursor) {
        Partition partition = getPartition(partitionIndex);
        if (!(partition instanceof DirectoryPartition)) {
            return;
        }

        DirectoryPartition directoryPartition = (DirectoryPartition)partition;
        TextView directoryTypeTextView = (TextView)view.findViewById(R.id.directory_type);
        directoryTypeTextView.setText(directoryPartition.getDirectoryType());
        TextView displayNameTextView = (TextView)view.findViewById(R.id.display_name);
        if (!TextUtils.isEmpty(directoryPartition.getDisplayName())) {
            displayNameTextView.setText(directoryPartition.getDisplayName());
            displayNameTextView.setVisibility(View.VISIBLE);
        } else {
            displayNameTextView.setVisibility(View.GONE);
        }

        TextView countText = (TextView)view.findViewById(R.id.count);
        if (directoryPartition.isLoading()) {
            countText.setText(R.string.search_results_searching);
        } else {
            int count = cursor == null ? 0 : cursor.getCount();
            countText.setText(getQuantityText(count, R.string.listFoundAllContactsZero,
                    R.plurals.searchFoundContacts));
        }
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getContext().getString(zeroResourceId);
        } else {
            String format = getContext().getResources()
                    .getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }
}
