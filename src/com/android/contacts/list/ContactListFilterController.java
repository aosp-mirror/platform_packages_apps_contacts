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

import com.android.contacts.R;

import android.app.Activity;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListPopupWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls a list of {@link ContactListFilter}'s.
 */
public class ContactListFilterController
        implements LoaderCallbacks<List<ContactListFilter>>, OnClickListener, OnItemClickListener {

    public interface ContactListFilterListener {
        void onContactListFiltersLoaded();
        void onContactListFilterChanged();
        void onContactListFilterCustomizationRequest();
    }

    private static final int MESSAGE_REFRESH_FILTERS = 0;

    /**
     * The delay before the contact filter list is refreshed. This is needed because
     * during contact sync we will get lots of notifications in rapid succession. This
     * delay will prevent the slowly changing list of filters from reloading too often.
     */
    private static final int FILTER_SPINNER_REFRESH_DELAY_MILLIS = 5000;

    private Context mContext;
    private LoaderManager mLoaderManager;
    private boolean mEnabled = true;
    private List<ContactListFilterListener> mListeners = new ArrayList<ContactListFilterListener>();
    private ListPopupWindow mPopup;
    private int mPopupWidth = -1;
    private SparseArray<ContactListFilter> mFilters;
    private ArrayList<ContactListFilter> mFilterList;
    private int mNextFilterId = 1;
    private View mAnchor;
    private FilterListAdapter mFilterListAdapter;
    private ContactListFilter mFilter;
    private boolean mFiltersLoaded;
    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_REFRESH_FILTERS) {
                loadFilters();
            }
        }
    };

    private int mAccountCount;

    public ContactListFilterController(Activity activity) {
        mContext = activity;
        mLoaderManager = activity.getLoaderManager();
    }

    public void setEnabled(boolean flag) {
        mEnabled = flag;
    }

    public void addListener(ContactListFilterListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(ContactListFilterListener listener) {
        mListeners.remove(listener);
    }

    public void setAnchor(View anchor) {
        mAnchor = anchor;
        mAnchor.setOnClickListener(this);
    }

    public ContactListFilter getFilter() {
        return mFilter;
    }

    public List<ContactListFilter> getFilterList() {
        return mFilterList;
    }

    public boolean isLoaded() {
        return mFiltersLoaded;
    }

    public void startLoading() {
        // Set the "ready" flag right away - we only want to start the loader once
        mFiltersLoaded = false;
        mFilter = ContactListFilter.restoreFromPreferences(getSharedPreferences());
        loadFilters();
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    private void loadFilters() {
        mLoaderManager.restartLoader(R.id.contact_list_filter_loader, null, this);
    }

    @Override
    public ContactListFilterLoader onCreateLoader(int id, Bundle args) {
        return new ContactListFilterLoader(mContext);
    }

    @Override
    public void onLoadFinished(
            Loader<List<ContactListFilter>> loader, List<ContactListFilter> filters) {
        if (mFilters == null) {
            mFilters = new SparseArray<ContactListFilter>(filters.size());
            mFilterList = new ArrayList<ContactListFilter>();
        } else {
            mFilters.clear();
            mFilterList.clear();
        }

        boolean filterValid = mFilter != null && !mFilter.isValidationRequired();

        mAccountCount = 0;
        int count = filters.size();
        for (int index = 0; index < count; index++) {
            if (filters.get(index).filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                mAccountCount++;
            }
        }

        if (mAccountCount > 1) {
            mFilters.append(mNextFilterId++,
                    new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
            mFilters.append(mNextFilterId++,
                    new ContactListFilter(ContactListFilter.FILTER_TYPE_STARRED));
        }

        for (int index = 0; index < count; index++) {
            ContactListFilter filter = filters.get(index);

            boolean firstAndOnly = mAccountCount == 1
                    && filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT;

            // If we only have one account, don't show it as "account", instead show it as "all"
            if (firstAndOnly) {
                filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
            }

            mFilters.append(mNextFilterId++, filter);
            mFilterList.add(filter);

            if (filter.equals(mFilter)) {
                // Refresh the filter in case the title got changed
                mFilter = filter;
                filterValid = true;
            }

            if (firstAndOnly) {
                mFilters.append(mNextFilterId++,
                        new ContactListFilter(ContactListFilter.FILTER_TYPE_STARRED));
            }
        }

        if (mAccountCount > 0) {
            mFilters.append(
                    mNextFilterId++, new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM));
        }

        boolean filterChanged = false;
        if (mFilter == null  || !filterValid) {
            filterChanged = mFilter != null;
            mFilter = getDefaultFilter();
        }

        if (mFilterListAdapter == null) {
            mFilterListAdapter = new FilterListAdapter();
        } else {
            mFilterListAdapter.notifyDataSetChanged();
        }

        if (filterChanged) {
            mFiltersLoaded = true;
            notifyContactListFilterChanged();
        } else if (!mFiltersLoaded) {
            mFiltersLoaded = true;
            notifyContacListFiltersLoaded();
        }
    }

    public void postDelayedRefresh() {
        if (!mHandler.hasMessages(MESSAGE_REFRESH_FILTERS)) {
            mHandler.sendEmptyMessageDelayed(
                    MESSAGE_REFRESH_FILTERS, FILTER_SPINNER_REFRESH_DELAY_MILLIS);
        }
    }

    private void setContactListFilter(int filterId) {
        ContactListFilter filter;
        if (filterId == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
            filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
        } else if (filterId == ContactListFilter.FILTER_TYPE_CUSTOM) {
            filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM);
        } else if (filterId == ContactListFilter.FILTER_TYPE_STARRED) {
            filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_STARRED);
        } else {
            filter = mFilters.get(filterId);
            if (filter == null) {
                filter = getDefaultFilter();
            }
        }

        setContactListFilter(filter, true);
    }

    public void setContactListFilter(ContactListFilter filter, boolean persistent) {
        if (!filter.equals(mFilter)) {
            mFilter = filter;
            if (persistent) {
                ContactListFilter.storeToPreferences(getSharedPreferences(), mFilter);
            }
            if (mListeners != null) {
               notifyContactListFilterChanged();
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (!mFiltersLoaded || !mEnabled) {
            return;
        }

        if (mPopupWidth == -1) {
            TypedArray a = mContext.obtainStyledAttributes(null, R.styleable.ContactBrowser);
            mPopupWidth = a.getDimensionPixelSize(
                    R.styleable.ContactBrowser_contact_filter_popup_width, -1);
            a.recycle();

            if (mPopupWidth == -1) {
                mPopupWidth = mAnchor.getWidth();
            }
        }

        mPopup = new ListPopupWindow(mContext, null);
        mPopup.setWidth(mPopupWidth);
        mPopup.setAdapter(mFilterListAdapter);
        mPopup.setAnchorView(mAnchor);
        mPopup.setOnItemClickListener(this);
        mPopup.setModal(true);
        mPopup.show();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mPopup.dismiss();
        if (mFilters.get((int) id).filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            notifyContactListFilterCustomizationRequest();
        } else {
            setContactListFilter((int) id);
        }
    }

    public void selectCustomFilter() {
        mFilter = new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM);
        notifyContactListFilterChanged();
    }

    private ContactListFilter getDefaultFilter() {
        return mFilters.size() > 0 ? mFilters.valueAt(0) : null;
    }

    private void notifyContacListFiltersLoaded() {
        for (ContactListFilterListener listener : mListeners) {
            listener.onContactListFiltersLoaded();
        }
    }

    private void notifyContactListFilterChanged() {
        for (ContactListFilterListener listener : mListeners) {
            listener.onContactListFilterChanged();
        }
    }

    private void notifyContactListFilterCustomizationRequest() {
        for (ContactListFilterListener listener : mListeners) {
            listener.onContactListFilterCustomizationRequest();
        }
    }

    private class FilterListAdapter extends BaseAdapter {
        private LayoutInflater mLayoutInflater;

        public FilterListAdapter() {
            mLayoutInflater = LayoutInflater.from(mContext);
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return mFilters.keyAt(position);
        }

        @Override
        public Object getItem(int position) {
            return mFilters.valueAt(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ContactListFilterView view;
            if (convertView != null) {
                view = (ContactListFilterView) convertView;
            } else {
                view = (ContactListFilterView) mLayoutInflater.inflate(
                        R.layout.filter_spinner_item, parent, false);
            }
            view.setMultipleAccounts(mAccountCount > 1);
            view.setContactListFilter(mFilters.valueAt(position));
            view.bindView(true);
            return view;
        }
    }
}
