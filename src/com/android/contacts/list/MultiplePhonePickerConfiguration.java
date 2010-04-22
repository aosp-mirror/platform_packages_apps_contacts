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
import com.android.contacts.MultiplePhonePickerActivity;

import android.content.Context;
import android.widget.ListAdapter;

/**
 * Configuration for the multiple phone picker.
 */
public class MultiplePhonePickerConfiguration extends ContactEntryListConfiguration {

    public MultiplePhonePickerConfiguration(Context context,
            ContactsApplicationController applicationController) {
        super(context, applicationController);
    }

    @Override
    public ListAdapter createListAdapter() {
        MultiplePhonePickerAdapter adapter =
                new MultiplePhonePickerAdapter((MultiplePhonePickerActivity)getContext());
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    public ContactEntryListController createController() {

        // TODO this needs a separate controller
        return new DefaultContactListController(getContext(), getApplicationController());
    }
}
