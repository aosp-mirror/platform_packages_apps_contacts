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

import android.content.Intent;
import android.net.Uri;

/**
 * Action callbacks that can be sent by a contact picker.
 */
public interface OnContactPickerActionListener  {

    /**
     * Returns the selected contact to the requester.
     */
    void onPickContactAction(Uri contactUri);

    /**
     * Returns the selected contact as a shortcut intent.
     */
    void onShortcutIntentCreated(Intent intent);

    /**
     * Creates a new contact and then returns it to the caller.
     */
    void onCreateNewContactAction();

    /**
     * Opens the specified contact for editing.
     */
    void onEditContactAction(Uri contactLookupUri);
}
