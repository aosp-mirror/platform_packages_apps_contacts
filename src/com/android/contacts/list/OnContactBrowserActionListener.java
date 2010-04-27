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

import android.net.Uri;

/**
 * Action callbacks that can be sent by a contact browser.
 */
public interface OnContactBrowserActionListener  {

    /**
     * Search all contacts for the specified string an show results for browsing.
     */
    void onSearchAllContactsAction(String string);

    /**
     * Open the specified contact for viewing.
     */
    void onViewContactAction(Uri contactLookupUri);

    /**
     * Create a new contact.
     */
    void onCreateNewContactAction();

    /**
     * Edit the specified contact.
     */
    void onEditContactAction(Uri contactLookupUri);
}
