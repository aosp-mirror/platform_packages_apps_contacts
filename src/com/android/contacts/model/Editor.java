/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.model;

import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.ViewIdGenerator;

import android.provider.ContactsContract.Data;

/**
 * Generic definition of something that edits a {@link Data} row through an
 * {@link ValuesDelta} object.
 */
public interface Editor {
    /**
     * Listener for an {@link Editor}, usually to handle deleted items.
     */
    public interface EditorListener {
        /**
         * Called when the given {@link Editor} has been deleted.
         */
        public void onDeleted(Editor editor);

        /**
         * Called when the given {@link Editor} has a request, for example it
         * wants to select a photo.
         */
        public void onRequest(int request);

        public static final int REQUEST_PICK_PHOTO = 1;
        public static final int FIELD_CHANGED = 2;
    }

    /**
     * Prepare this editor for the given {@link ValuesDelta}, which
     * builds any needed views. Any changes performed by the user will be
     * written back to that same object.
     */
    public void setValues(DataKind kind, ValuesDelta values, EntityDelta state, boolean readOnly,
            ViewIdGenerator vig);

    /**
     * Add a specific {@link EditorListener} to this {@link Editor}.
     */
    public void setEditorListener(EditorListener listener);

    /**
     * Called internally when the contents of a specific field have changed,
     * allowing advanced editors to persist data in a specific way.
     */
    public void onFieldChanged(String column, String value);
}
