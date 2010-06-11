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
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.ContactCounts;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Common base class for various contact-related lists, e.g. contact list, phone number list
 * etc.
 */
public abstract class ContactEntryListAdapter extends IndexerListAdapter {

    private static final String TAG = "ContactEntryListAdapter";

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

    private HashMap<Integer, DirectoryPartition> mPartitions;

    public ContactEntryListAdapter(Context context) {
        super(context, R.layout.list_section, R.id.header_text);
        addPartitions();
    }

    /**
     * Adds all partitions this adapter will handle. The default implementation
     * creates one partition with no header.
     */
    protected void addPartitions() {
        addPartition(false, false);
    }

    public void addDirectoryPartition(DirectoryPartition partition) {
        if (mPartitions == null) {
            mPartitions = new HashMap<Integer, DirectoryPartition>();
        }
        int partitionIndex = getPartitionCount();
        mPartitions.put(partitionIndex, partition);
        addPartition(partition.getShowIfEmpty(), partition.getDirectoryType() != null);
    }

    public abstract String getContactDisplayName(int position);
    public abstract void configureLoader(CursorLoader loader, long directoryId);

    /**
     * Marks all partitions as "loading"
     */
    public void onDataReload() {
        boolean notify = false;
        if (mPartitions != null) {
            for (DirectoryPartition partition : mPartitions.values()) {
                if (!partition.isLoading()) {
                    partition.setLoading(true);
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
        if (mSearchMode != flag) {
            mSearchMode = flag;

            // Search mode change may mean a new data type in the list.
            // Let's drop current data to avoid confusion
            resetPartitions();
        }
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

    // TODO no highlighting in STREQUENT mode
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

    @Override
    public void changeCursor(int partitionIndex, Cursor cursor) {
        if (mPartitions != null) {
            DirectoryPartition partition = mPartitions.get(partitionIndex);
            partition.setLoading(false);
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

    @Override
    protected View newHeaderView(Context context, int partition, Cursor cursor,
            ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(R.layout.directory_header, parent, false);
    }

    @Override
    protected void bindHeaderView(View view, int partitionIndex, Cursor cursor) {
        DirectoryPartition partition = mPartitions.get(partitionIndex);

        TextView directoryTypeTextView = (TextView)view.findViewById(R.id.directory_type);
        directoryTypeTextView.setText(partition.getDirectoryType());
        TextView displayNameTextView = (TextView)view.findViewById(R.id.display_name);
        if (!TextUtils.isEmpty(partition.getDisplayName())) {
            displayNameTextView.setText(partition.getDisplayName());
            displayNameTextView.setVisibility(View.VISIBLE);
        } else {
            displayNameTextView.setVisibility(View.GONE);
        }

        TextView countText = (TextView)view.findViewById(R.id.count);
        if (partition.isLoading()) {
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
