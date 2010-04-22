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

/**
 * Controller for the default contact list.
 */
public class DefaultContactListController extends ContactEntryListController {

    public DefaultContactListController(Context context,
            ContactsApplicationController appController) {
        super(context, appController);
    }

    @Override
    protected void onItemClick(int position, long id) {
        // TODO instead of delegating the entire procedure to the ContactsListActivity,
        // figure out what the specific action is and delegate the specific action.
        getContactsApplicationController().onListItemClick(position, id);
    }
}
