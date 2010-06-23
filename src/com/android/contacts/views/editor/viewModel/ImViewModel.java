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
import android.content.ContentProviderOperation.Builder;
import android.provider.ContactsContract.CommonDataKinds.Im;

/**
 * Editor for Instant Messaging fields. The Type (HOME, WORK, OTHER, CUSTOM) is not shown but
 * instead the same field is used for showing the Protocol (AIM, YAHOO etc). When
 * creating new Im rows, the Type is set to OTHER
 */
public class ImViewModel extends FieldAndTypeViewModel {
    private ImViewModel(Context context, DisplayRawContact rawContact, long dataId,
            ContentValues contentValues, int titleResId) {
        super(context, rawContact, dataId, contentValues, Im.CONTENT_ITEM_TYPE, titleResId, Im.DATA,
                Im.PROTOCOL, Im.CUSTOM_PROTOCOL);
    }

    public static ImViewModel createForExisting(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues, int titleResId) {
        return new ImViewModel(context, rawContact, dataId, contentValues, titleResId);
    }

    @Override
    protected CharSequence getTypeDisplayLabel() {
        return Im.getProtocolLabel(getContext().getResources(), getType(), getLabel());
    }

    @Override
    protected void writeToBuilder(Builder builder, boolean isInsert) {
        // The Type field is not exposed in the UI. Write OTHER for Insert but don't change it
        // for updates
        if (isInsert) {
            builder.withValue(Im.TYPE, Im.TYPE_OTHER);
            builder.withValue(Im.LABEL, "");
        }
        super.writeToBuilder(builder, isInsert);
    }
}
