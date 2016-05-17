/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.logging.Logger;
import com.android.contacts.common.logging.SearchState;
import com.android.contacts.list.MultiSelectEntryContactListAdapter.SelectedContactsListener;

import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Fragment containing a contact list used for browsing contacts and optionally selecting
 * multiple contacts via checkboxes.
 */
public class MultiSelectContactsListFragment extends DefaultContactBrowseListFragment
        implements SelectedContactsListener {

    private static final String TAG = "MultiContactsList";

    public interface OnCheckBoxListActionListener {
        void onStartDisplayingCheckBoxes();
        void onSelectedContactIdsChanged();
        void onStopDisplayingCheckBoxes();
    }

    private static final String EXTRA_KEY_SELECTED_CONTACTS = "selected_contacts";

    private static final String KEY_SEARCH_RESULT_CLICKED = "search_result_clicked";

    private OnCheckBoxListActionListener mCheckBoxListListener;
    private boolean mSearchResultClicked;

    public void setCheckBoxListListener(OnCheckBoxListActionListener checkBoxListListener) {
        mCheckBoxListListener = checkBoxListListener;
    }

    /**
     * Whether a search result was clicked by the user. Tracked so that we can distinguish
     * between exiting the search mode after a result was clicked from exiting w/o clicking
     * any search result.
     */
    public boolean wasSearchResultClicked() {
        return mSearchResultClicked;
    }

    /**
     * Resets whether a search result was clicked by the user to false.
     */
    public void resetSearchResultClicked() {
        mSearchResultClicked = false;
    }

    @Override
    public void onSelectedContactsChanged() {
        if (mCheckBoxListListener != null) mCheckBoxListListener.onSelectedContactIdsChanged();
    }

    @Override
    public void onSelectedContactsChangedViaCheckBox() {
        if (getAdapter().getSelectedContactIds().size() == 0) {
            // Last checkbox has been unchecked. So we should stop displaying checkboxes.
            mCheckBoxListListener.onStopDisplayingCheckBoxes();
        } else {
            onSelectedContactsChanged();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            final TreeSet<Long> selectedContactIds = (TreeSet<Long>)
                    savedInstanceState.getSerializable(EXTRA_KEY_SELECTED_CONTACTS);
            getAdapter().setSelectedContactIds(selectedContactIds);
            if (mCheckBoxListListener != null) {
                mCheckBoxListListener.onSelectedContactIdsChanged();
            }
            mSearchResultClicked = savedInstanceState.getBoolean(KEY_SEARCH_RESULT_CLICKED);
        }
    }

    public TreeSet<Long> getSelectedContactIds() {
        return getAdapter().getSelectedContactIds();
    }

    public long[] getSelectedContactIdsArray() {
        return getAdapter().getSelectedContactIdsArray();
    }

    @Override
    public MultiSelectEntryContactListAdapter getAdapter() {
        return (MultiSelectEntryContactListAdapter) super.getAdapter();
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        getAdapter().setSelectedContactsListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(EXTRA_KEY_SELECTED_CONTACTS, getSelectedContactIds());
        outState.putBoolean(KEY_SEARCH_RESULT_CLICKED, mSearchResultClicked);
    }

    public void displayCheckBoxes(boolean displayCheckBoxes) {
        if (getAdapter() != null) {
            getAdapter().setDisplayCheckBoxes(displayCheckBoxes);
            if (!displayCheckBoxes) {
                clearCheckBoxes();
            }
        }
    }

    public void clearCheckBoxes() {
        getAdapter().setSelectedContactIds(new TreeSet<Long>());
    }

    @Override
    protected boolean onItemLongClick(int position, long id) {
        final int previouslySelectedCount = getAdapter().getSelectedContactIds().size();
        final long contactId = getContactId(position);
        final int partition = getAdapter().getPartitionForPosition(position);
        if (contactId >= 0 && partition == ContactsContract.Directory.DEFAULT) {
            if (mCheckBoxListListener != null) {
                mCheckBoxListListener.onStartDisplayingCheckBoxes();
            }
            getAdapter().toggleSelectionOfContactId(contactId);
            // Manually send clicked event if there is a checkbox.
            // See b/24098561.  TalkBack will not read it otherwise.
            final int index = position + getListView().getHeaderViewsCount() - getListView()
                    .getFirstVisiblePosition();
            if (index >= 0 && index < getListView().getChildCount()) {
                getListView().getChildAt(index).sendAccessibilityEvent(AccessibilityEvent
                        .TYPE_VIEW_CLICKED);
            }
        }
        final int nowSelectedCount = getAdapter().getSelectedContactIds().size();
        if (mCheckBoxListListener != null
                && previouslySelectedCount != 0 && nowSelectedCount == 0) {
            // Last checkbox has been unchecked. So we should stop displaying checkboxes.
            mCheckBoxListListener.onStopDisplayingCheckBoxes();
        }
        return true;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final long contactId = getContactId(position);
        if (contactId < 0) {
            return;
        }
        if (getAdapter().isDisplayingCheckBoxes()) {
            getAdapter().toggleSelectionOfContactId(contactId);
        } else {
            if (isSearchMode()) {
                mSearchResultClicked = true;
                Logger.logSearchEvent(createSearchStateForSearchResultClick(position));
            }
            super.onItemClick(position, id);
        }
        if (mCheckBoxListListener != null && getAdapter().getSelectedContactIds().size() == 0) {
            mCheckBoxListListener.onStopDisplayingCheckBoxes();
        }
    }

    private long getContactId(int position) {
        final int contactIdColumnIndex = getAdapter().getContactColumnIdIndex();

        final Cursor cursor = (Cursor) getAdapter().getItem(position);
        if (cursor != null) {
            if (cursor.getColumnCount() > contactIdColumnIndex) {
                return cursor.getLong(contactIdColumnIndex);
            }
        }

        Log.w(TAG, "Failed to get contact ID from cursor column " + contactIdColumnIndex);
        return -1;
    }

    /**
     * Returns the state of the search results currently presented to the user.
     */
    public SearchState createSearchState() {
        return createSearchState(/* selectedPosition */ -1);
    }

    /**
     * Returns the state of the search results presented to the user
     * at the time the result in the given position was clicked.
     */
    public SearchState createSearchStateForSearchResultClick(int selectedPosition) {
        return createSearchState(selectedPosition);
    }

    private SearchState createSearchState(int selectedPosition) {
        final MultiSelectEntryContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return null;
        }
        final SearchState searchState = new SearchState();
        searchState.queryLength = adapter.getQueryString() == null
                ? 0 : adapter.getQueryString().length();
        searchState.numPartitions = adapter.getPartitionCount();

        // Set the number of results displayed to the user.  Note that the adapter.getCount(),
        // value does not always match the number of results actually displayed to the user,
        // which is why we calculate it manually.
        final List<Integer> numResultsInEachPartition = new ArrayList<>();
        for (int i = 0; i < adapter.getPartitionCount(); i++) {
            final Cursor cursor = adapter.getCursor(i);
            if (cursor == null || cursor.isClosed()) {
                // Something went wrong, abort.
                numResultsInEachPartition.clear();
                break;
            }
            numResultsInEachPartition.add(cursor.getCount());
        }
        if (!numResultsInEachPartition.isEmpty()) {
            int numResults = 0;
            for (int i = 0; i < numResultsInEachPartition.size(); i++) {
                numResults += numResultsInEachPartition.get(i);
            }
            searchState.numResults = numResults;
        }

        // If a selection was made, set additional search state
        if (selectedPosition >= 0) {
            searchState.selectedPartition = adapter.getPartitionForPosition(selectedPosition);
            searchState.selectedIndexInPartition = adapter.getOffsetInPartition(selectedPosition);
            final Cursor cursor = adapter.getCursor(searchState.selectedPartition);
            searchState.numResultsInSelectedPartition =
                    cursor == null || cursor.isClosed() ? -1 : cursor.getCount();

            // Calculate the index across all partitions
            if (!numResultsInEachPartition.isEmpty()) {
                int selectedIndex = 0;
                for (int i = 0; i < searchState.selectedPartition; i++) {
                    selectedIndex += numResultsInEachPartition.get(i);
                }
                selectedIndex += searchState.selectedIndexInPartition;
                searchState.selectedIndex = selectedIndex;
            }
        }
        return searchState;
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new MultiSelectEntryContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        adapter.setPhotoPosition(
                ContactListItemView.getDefaultPhotoPosition(/* opposite = */ false));
        return adapter;
    }
}
