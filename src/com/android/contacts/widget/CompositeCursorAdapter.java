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
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * A general purpose adapter that is composed of multiple cursors. It just
 * appends them in the order they are added.
 */
public abstract class CompositeCursorAdapter extends BaseAdapter {

    private static final int INITIAL_CAPACITY = 2;

    private static class Partition {
        final boolean showIfEmpty;
        final boolean hasHeader;

        int count;
        Cursor cursor;
        int idColumnIndex;

        public Partition(boolean showIfEmpty, boolean hasHeader) {
            this.showIfEmpty = showIfEmpty;
            this.hasHeader = hasHeader;
        }
    }

    private final Context mContext;
    private Partition[] mPartitions;
    private int mSize = 0;
    private int mCount = 0;
    private boolean mCacheValid = true;

    public CompositeCursorAdapter(Context context) {
        this(context, INITIAL_CAPACITY);
    }

    public CompositeCursorAdapter(Context context, int initialCapacity) {
        mContext = context;
        mPartitions = new Partition[INITIAL_CAPACITY];
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * Registers a partition. The cursor for that partition can be set later.
     * Partitions should be added in the order they are supposed to appear in the
     * list.
     */
    public void addPartition(boolean showIfEmpty, boolean hasHeader) {
        if (mSize >= mPartitions.length) {
            int newCapacity = mSize + 2;
            Partition[] newAdapters = new Partition[newCapacity];
            System.arraycopy(mPartitions, 0, newAdapters, 0, mSize);
            mPartitions = newAdapters;
        }
        mPartitions[mSize++] = new Partition(showIfEmpty, hasHeader);
        invalidate();
    }

    protected void invalidate() {
        mCacheValid = false;
    }

    public int getPartitionCount() {
        return mSize;
    }

    protected void ensureCacheValid() {
        if (mCacheValid) {
            return;
        }

        if (mSize == 0) {
            throw new IllegalStateException("A CompositeCursorAdapter should have "
                    + "at least one partition");
        }

        mCount = 0;
        for (int i = 0; i < mSize; i++) {
            Cursor cursor = mPartitions[i].cursor;
            int count = cursor != null ? cursor.getCount() : 0;
            if (mPartitions[i].hasHeader) {
                if (count != 0 || mPartitions[i].showIfEmpty) {
                    count++;
                }
            }
            mPartitions[i].count = count;
            mCount += count;
        }

        mCacheValid = true;
    }

    /**
     * Returns true if the specified partition was configured to have a header.
     */
    public boolean hasHeader(int partition) {
        return mPartitions[partition].hasHeader;
    }

    /**
     * Returns the total number of list items in all partitions.
     */
    public int getCount() {
        ensureCacheValid();
        return mCount;
    }

    /**
     * Changes the cursor for an individual partition.
     */
    public void changeCursor(int partition, Cursor cursor) {
        mPartitions[partition].cursor = cursor;
        if (cursor != null) {
            mPartitions[partition].idColumnIndex = cursor.getColumnIndex("_id");
        }
        invalidate();
        notifyDataSetChanged();
    }

    /**
     * Returns true if the specified partition has no cursor or an empty cursor.
     */
    public boolean isPartitionEmpty(int partition) {
        Cursor cursor = mPartitions[partition].cursor;
        return cursor == null || cursor.getCount() == 0;
    }

    /**
     * Given a list position, returns the index of the corresponding partition.
     */
    public int getPartitionForPosition(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            int end = start + mPartitions[i].count;
            if (position >= start && position < end) {
                return i;
            }
            start = end;
        }
        return -1;
    }

    /**
     * Given a list position, return the offset of the corresponding item in its
     * partition.  The header, if any, will have offset -1.
     */
    public int getOffsetInPartition(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            int end = start + mPartitions[i].count;
            if (position >= start && position < end) {
                int offset = position - start;
                if (mPartitions[i].hasHeader) {
                    offset--;
                }
                return offset;
            }
            start = end;
        }
        return -1;
    }

    /**
     * Returns the first list position for the specified partition.
     */
    public int getPositionForPartition(int partition) {
        ensureCacheValid();
        int position = 0;
        for (int i = 0; i < partition; i++) {
            position += mPartitions[i].count;
        }
        return position;
    }

    /**
     * Returns the overall number of view types across all partitions. An implementation
     * of this method needs to ensure that the returned count is consistent with the
     * values returned by {@link #getItemViewType(int,int)}.
     */
    @Override
    public int getViewTypeCount() {
        return 2;
    }

    /**
     * Returns the view type for the list item at the specified position in the
     * specified partition.
     */
    protected int getItemViewType(int partition, int position) {
        return 0;
    }

    @Override
    public int getItemViewType(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            int end = start  + mPartitions[i].count;
            if (position >= start && position < end) {
                int offset = position - start;
                if (mPartitions[i].hasHeader && offset == 0) {
                    return IGNORE_ITEM_VIEW_TYPE;
                }
                return getItemViewType(i, position);
            }
            start = end;
        }

        throw new ArrayIndexOutOfBoundsException(position);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            int end = start + mPartitions[i].count;
            if (position >= start && position < end) {
                int offset = position - start;
                if (mPartitions[i].hasHeader) {
                    offset--;
                }
                View view;
                if (offset == -1) {
                    view = getHeaderView(i, mPartitions[i].cursor, convertView, parent);
                } else {
                    if (!mPartitions[i].cursor.moveToPosition(offset)) {
                        throw new IllegalStateException("Couldn't move cursor to position "
                                + offset);
                    }
                    view = getView(i, mPartitions[i].cursor, offset, convertView, parent);
                }
                if (view == null) {
                    throw new NullPointerException("View should not be null, partition: " + i
                            + " position: " + offset);
                }
                return view;
            }
            start = end;
        }

        throw new ArrayIndexOutOfBoundsException(position);
    }

    /**
     * Returns the header view for the specified partition, creating one if needed.
     */
    protected View getHeaderView(int partition, Cursor cursor, View convertView,
            ViewGroup parent) {
        View view = convertView != null
                ? convertView
                : newHeaderView(mContext, partition, cursor, parent);
        bindHeaderView(view, partition, cursor);
        return view;
    }

    /**
     * Creates the header view for the specified partition.
     */
    protected View newHeaderView(Context context, int partition, Cursor cursor,
            ViewGroup parent) {
        return null;
    }

    /**
     * Binds the header view for the specified partition.
     */
    protected void bindHeaderView(View view, int partition, Cursor cursor) {
    }

    /**
     * Returns an item view for the specified partition, creating one if needed.
     */
    protected View getView(int partition, Cursor cursor, int position, View convertView,
            ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            view = newView(mContext, partition, cursor, position, parent);
        }
        bindView(view, partition, cursor, position);
        return view;
    }

    /**
     * Creates an item view for the specified partition and position. Position
     * corresponds directly to the current cursor position.
     */
    protected abstract View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent);

    /**
     * Binds an item view for the specified partition and position. Position
     * corresponds directly to the current cursor position.
     */
    protected abstract void bindView(View v, int partition, Cursor cursor, int position);

    /**
     * Returns a pre-positioned cursor for the specified list position.
     */
    public Object getItem(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            int end = start + mPartitions[i].count;
            if (position >= start && position < end) {
                int offset = position - start;
                if (mPartitions[i].hasHeader) {
                    offset--;
                }
                if (offset == -1) {
                    return null;
                }
                Cursor cursor = mPartitions[i].cursor;
                cursor.moveToPosition(offset);
                return cursor;
            }
            start = end;
        }

        return null;
    }

    /**
     * Returns the item ID for the specified list position.
     */
    public long getItemId(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            int end = start + mPartitions[i].count;
            if (position >= start && position < end) {
                int offset = position - start;
                if (mPartitions[i].hasHeader) {
                    offset--;
                }
                if (offset == -1) {
                    return -1;
                }
                if (mPartitions[i].idColumnIndex == -1) {
                    return -1;
                }

                Cursor cursor = mPartitions[i].cursor;
                cursor.moveToPosition(offset);
                return cursor.getLong(mPartitions[i].idColumnIndex);
            }
            start = end;
        }

        return 0;
    }

    /**
     * Returns false if any partition has a header.
     */
    @Override
    public boolean areAllItemsEnabled() {
        for (int i = 0; i < mSize; i++) {
            if (mPartitions[i].hasHeader) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true for all items except headers.
     */
    @Override
    public boolean isEnabled(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mSize; i++) {
            int end = start + mPartitions[i].count;
            if (position >= start && position < end) {
                int offset = position - start;
                if (mPartitions[i].hasHeader && offset == 0) {
                    return false;
                } else {
                    return isEnabled(i, offset);
                }
            }
            start = end;
        }

        return false;
    }

    /**
     * Returns true if the item at the specified offset of the specified
     * partition is selectable and clickable.
     */
    protected boolean isEnabled(int partition, int position) {
        return true;
    }
}
