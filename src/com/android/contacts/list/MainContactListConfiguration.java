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
import com.android.contacts.ContactsListActivity;

import android.content.Context;
import android.widget.ListAdapter;

/**
 * Configuration for the default contact list.
 */
public class MainContactListConfiguration extends ContactEntryListConfiguration {

    public MainContactListConfiguration(Context context,
            ContactsApplicationController applicationController) {
        super(context, applicationController);
    }

    @Override
    public ListAdapter createListAdapter() {
        ContactItemListAdapter adapter =
                new ContactItemListAdapter((ContactsListActivity)getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    public ContactEntryListController createController() {
        return new MainContactListController(getContext(), getApplicationController());
    }
}
