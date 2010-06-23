/*
 * Copyright (C) 2010 Google Inc.
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
 * limitations under the License
 */

package com.android.contacts.views.editor.viewModel;

import com.android.contacts.views.editor.DisplayRawContact;

import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Note;

public class NoteViewModel extends SingleFieldViewModel {
    private NoteViewModel(Context context, DisplayRawContact rawContact, long dataId,
            ContentValues contentValues, int titleResId) {
        super(context, rawContact, dataId, contentValues, Note.CONTENT_ITEM_TYPE, titleResId,
                Note.NOTE);
    }

    public static NoteViewModel createForExisting(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, int titleResId) {
        return new NoteViewModel(context, rawContact, dataId, contentValues, titleResId);
    }
}
