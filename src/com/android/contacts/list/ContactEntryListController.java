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

import com.android.contacts.ContactsApplicationController;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Common base class for various contact-related list controllers.
 */
public abstract class ContactEntryListController implements AdapterView.OnItemClickListener {

    private final Context mContext;
    private final ContactsApplicationController mAppController;
    private ListAdapter mAdapter;
    private ListView mListView;

    public ContactEntryListController(Context context,
            ContactsApplicationController appController) {
        this.mContext = context;
        this.mAppController = appController;
    }

    public Context getContext() {
        return mContext;
    }

    public ContactsApplicationController getContactsApplicationController() {
        return mAppController;
    }

    public void setAdapter(ListAdapter adapter) {
        mAdapter = adapter;
    }

    public ListAdapter getAdapter() {
        return mAdapter;
    }

    public void setListView(ListView listView) {
        mListView = listView;
    }

    public ListView getListView() {
        return mListView;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        hideSoftKeyboard();

        onItemClick(position, id);
    }

    protected abstract void onItemClick(int position, long id);

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(), 0);
    }
}
