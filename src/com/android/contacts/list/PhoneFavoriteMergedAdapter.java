/*
 * Copyright (C) 2011 Google Inc.
 * Licensed to The Android Open Source Project.
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

import com.android.contacts.R;

import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.SectionIndexer;

/**
 * An adapter that combines items from {@link ContactTileAdapter} and
 * {@link ContactEntryListAdapter} into a single list. In between those two results,
 * an account filter header will be inserted.
 */
public class PhoneFavoriteMergedAdapter extends BaseAdapter implements SectionIndexer {

    private class CustomDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }
    }

    private final ContactTileAdapter mContactTileAdapter;
    private final ContactEntryListAdapter mContactEntryListAdapter;
    private final View mAccountFilterHeaderContainer;

    private final int mItemPaddingLeft;
    private final int mItemPaddingRight;

    // Make frequent header consistent with account filter header.
    private final int mFrequentHeaderPaddingTop;

    private final DataSetObserver mObserver;

    public PhoneFavoriteMergedAdapter(Context context,
            ContactTileAdapter contactTileAdapter,
            View accountFilterHeaderContainer,
            ContactEntryListAdapter contactEntryListAdapter) {
        Resources resources = context.getResources();
        mItemPaddingLeft = resources.getDimensionPixelSize(R.dimen.detail_item_side_margin);
        mItemPaddingRight = resources.getDimensionPixelSize(R.dimen.list_visible_scrollbar_padding);
        mFrequentHeaderPaddingTop = resources.getDimensionPixelSize(
                R.dimen.contact_browser_list_top_margin);
        mContactTileAdapter = contactTileAdapter;
        mContactEntryListAdapter = contactEntryListAdapter;

        mAccountFilterHeaderContainer = accountFilterHeaderContainer;

        mObserver = new CustomDataSetObserver();
        mContactTileAdapter.registerDataSetObserver(mObserver);
        mContactEntryListAdapter.registerDataSetObserver(mObserver);
    }

    @Override
    public int getCount() {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        final int contactEntryListAdapterCount = mContactEntryListAdapter.getCount();
        if (mContactEntryListAdapter.isLoading()) {
            // Hide "all" contacts during its being loaded.
            return contactTileAdapterCount + 1;
        } else {
            return contactTileAdapterCount + contactEntryListAdapterCount + 1;
        }
    }

    @Override
    public Object getItem(int position) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        final int contactEntryListAdapterCount = mContactEntryListAdapter.getCount();
        if (position < contactTileAdapterCount) {
            return mContactTileAdapter.getItem(position);
        } else if (position == contactTileAdapterCount) {
            return mAccountFilterHeaderContainer;
        } else {
            final int localPosition = position - contactTileAdapterCount - 1;
            return mContactTileAdapter.getItem(localPosition);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return (mContactTileAdapter.getViewTypeCount()
                + mContactEntryListAdapter.getViewTypeCount()
                + 1);
    }

    @Override
    public int getItemViewType(int position) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        final int contactEntryListAdapterCount = mContactEntryListAdapter.getCount();
        if (position < contactTileAdapterCount) {
            return mContactTileAdapter.getItemViewType(position);
        } else if (position == contactTileAdapterCount) {
            return mContactTileAdapter.getViewTypeCount()
                    + mContactEntryListAdapter.getViewTypeCount();
        } else {
            final int localPosition = position - contactTileAdapterCount - 1;
            final int type = mContactEntryListAdapter.getItemViewType(localPosition);
            // IGNORE_ITEM_VIEW_TYPE must be handled differently.
            return (type < 0) ? type : type + mContactTileAdapter.getViewTypeCount();
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        final int contactEntryListAdapterCount = mContactEntryListAdapter.getCount();

        // Obtain a View relevant for that position, and adjust its horizontal padding. Each
        // View has different implementation, so we use different way to control those padding.
        if (position < contactTileAdapterCount) {
            final View view = mContactTileAdapter.getView(position, convertView, parent);
            final int frequentHeaderPosition = mContactTileAdapter.getFrequentHeaderPosition();
            if (position < frequentHeaderPosition) {  // "starred" contacts
                // No padding adjustment.
            } else if (position == frequentHeaderPosition) {
                view.setPadding(mItemPaddingLeft, mFrequentHeaderPaddingTop,
                        mItemPaddingRight, view.getPaddingBottom());
            } else {
                // Views for "frequent" contacts use FrameLayout's margins instead of padding.
                final FrameLayout frameLayout = (FrameLayout) view;
                final View child = frameLayout.getChildAt(0);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(mItemPaddingLeft, 0, mItemPaddingRight, 0);
                child.setLayoutParams(params);
            }
            return view;
        } else if (position == contactTileAdapterCount) {
            mAccountFilterHeaderContainer.setPadding(mItemPaddingLeft,
                    mAccountFilterHeaderContainer.getPaddingTop(),
                    mItemPaddingRight,
                    mAccountFilterHeaderContainer.getPaddingBottom());
            return mAccountFilterHeaderContainer;
        } else {
            final int localPosition = position - contactTileAdapterCount - 1;
            final ContactListItemView itemView = (ContactListItemView)
                    mContactEntryListAdapter.getView(localPosition, convertView, null);
            itemView.setPadding(mItemPaddingLeft, itemView.getPaddingTop(),
                    mItemPaddingRight, itemView.getPaddingBottom());
            itemView.setSelectionBoundsHorizontalMargin(mItemPaddingLeft, mItemPaddingRight);
            return itemView;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return (mContactTileAdapter.areAllItemsEnabled()
                && mAccountFilterHeaderContainer.isEnabled()
                && mContactEntryListAdapter.areAllItemsEnabled());
    }

    @Override
    public boolean isEnabled(int position) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        final int contactEntryListAdapterCount = mContactEntryListAdapter.getCount();
        if (position < contactTileAdapterCount) {
            return mContactTileAdapter.isEnabled(position);
        } else if (position == contactTileAdapterCount) {
            // This will be handled by View's onClick event instead of ListView's onItemClick event.
            return false;
        } else {
            final int localPosition = position - contactTileAdapterCount - 1;
            return mContactEntryListAdapter.isEnabled(localPosition);
        }
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        final int localPosition = mContactEntryListAdapter.getPositionForSection(sectionIndex);
        return contactTileAdapterCount + 1 + localPosition;
    }

    @Override
    public int getSectionForPosition(int position) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        if (position <= contactTileAdapterCount) {
            return 0;
        } else {
            final int localPosition = position - contactTileAdapterCount - 1;
            return mContactEntryListAdapter.getSectionForPosition(localPosition);
        }
    }

    @Override
    public Object[] getSections() {
        return mContactEntryListAdapter.getSections();
    }

    public boolean shouldShowFirstScroller(int firstVisibleItem) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        return firstVisibleItem > contactTileAdapterCount;
    }
}