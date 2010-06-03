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
package com.android.contacts.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;
import android.widget.TextView;

/**
 * A list adapter that supports section indexer and a pinned header.
 */
public abstract class IndexerListAdapter extends PinnedHeaderListAdapter implements SectionIndexer {

    private final int mSectionHeaderTextViewId;
    private final int mSectionHeaderLayoutResId;

    protected Context mContext;
    private SectionIndexer mIndexer;
    private int mIndexedPartition = 0;
    private boolean mSectionHeaderDisplayEnabled;
    private View mHeader;

    /**
     * Constructor.
     *
     * @param context
     * @param sectionHeaderLayoutResourceId section header layout resource ID
     * @param sectionHeaderTextViewId section header text view ID
     */
    public IndexerListAdapter(Context context, int sectionHeaderLayoutResourceId,
            int sectionHeaderTextViewId) {
        super(context);
        mContext = context;
        mSectionHeaderLayoutResId = sectionHeaderLayoutResourceId;
        mSectionHeaderTextViewId = sectionHeaderTextViewId;
    }

    public boolean isSectionHeaderDisplayEnabled() {
        return mSectionHeaderDisplayEnabled;
    }

    public void setSectionHeaderDisplayEnabled(boolean flag) {
        this.mSectionHeaderDisplayEnabled = flag;
    }

    public int getIndexedPartition() {
        return mIndexedPartition;
    }

    public void setIndexedPartition(int partition) {
        this.mIndexedPartition = partition;
    }

    public void setIndexer(SectionIndexer indexer) {
        mIndexer = indexer;
    }

    public Object[] getSections() {
        if (mIndexer == null) {
            return new String[] { " " };
        } else {
            return mIndexer.getSections();
        }
    }

    /**
     * @return relative position of the section in the indexed partition
     */
    public int getPositionForSection(int sectionIndex) {
        if (mIndexer == null) {
            return -1;
        }

        return mIndexer.getPositionForSection(sectionIndex);
    }

    /**
     * @param position relative position in the indexed partition
     */
    public int getSectionForPosition(int position) {
        if (mIndexer == null) {
            return -1;
        }

        return mIndexer.getSectionForPosition(position);
    }

    @Override
    public int getPinnedHeaderCount() {
        if (isSectionHeaderDisplayEnabled()) {
            return super.getPinnedHeaderCount() + 1;
        } else {
            return super.getPinnedHeaderCount();
        }
    }

    @Override
    public View createPinnedHeaderView(int viewIndex, ViewGroup parent) {
        if (isSectionHeaderDisplayEnabled() && viewIndex == getPinnedHeaderCount() - 1) {
            mHeader = LayoutInflater.from(mContext).
                    inflate(mSectionHeaderLayoutResId, parent, false);
            return mHeader;
        } else {
            return super.createPinnedHeaderView(viewIndex, parent);
        }
    }

    @Override
    public void configurePinnedHeaders(PinnedHeaderListView listView) {
        super.configurePinnedHeaders(listView);

        if (!isSectionHeaderDisplayEnabled()) {
            return;
        }

        int index = getPinnedHeaderCount() - 1;
        if (mIndexer == null || getCount() == 0) {
            listView.setHeaderInvisible(index);
        } else {
            int listPosition = listView.getPositionAt(listView.getTotalTopPinnedHeaderHeight());
            int position = listPosition - listView.getHeaderViewsCount();

            int section = -1;
            int partition = getPartitionForPosition(position);
            if (partition == mIndexedPartition) {
                int offset = getOffsetInPartition(position);
                if (offset != -1) {
                    section = getSectionForPosition(offset);
                }
            }

            if (section == -1) {
                listView.setHeaderInvisible(index);
            } else {
                String title = (String)mIndexer.getSections()[section];
                TextView titleView = (TextView)mHeader.getTag();
                if (titleView == null) {
                    titleView = (TextView)mHeader.findViewById(mSectionHeaderTextViewId);
                    mHeader.setTag(titleView);
                }
                titleView.setText(title);

                // Compute the item position where the current partition begins
                int partitionStart = getPositionForPartition(mIndexedPartition);
                if (hasHeader(mIndexedPartition)) {
                    partitionStart++;
                }

                // Compute the item position where the next section begins
                int nextSectionPosition = partitionStart + getPositionForSection(section + 1);
                boolean isLastInSection = position == nextSectionPosition - 1;
                listView.setFadingHeader(index, listPosition, isLastInSection);
            }
        }
    }
}
