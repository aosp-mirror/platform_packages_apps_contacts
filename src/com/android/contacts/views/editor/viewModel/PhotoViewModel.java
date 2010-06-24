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
import com.android.contacts.views.editor.view.PhotoView;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContentProviderOperation.Builder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Editor for the contact photo.
 */
public class PhotoViewModel extends DataViewModel {
    private PhotoViewModel(Context context, DisplayRawContact rawContact, long dataId,
            ContentValues contentValues) {
        super(context, rawContact, dataId, contentValues, Im.CONTENT_ITEM_TYPE);
    }

    public static PhotoViewModel createForExisting(Context context, DisplayRawContact rawContact,
            long dataId, ContentValues contentValues) {
        return new PhotoViewModel(context, rawContact, dataId, contentValues);
    }

    @Override
    protected void writeToBuilder(Builder builder, boolean isInsert) {
        // TODO
    }

    @Override
    public View createAndAddView(LayoutInflater inflater, ViewGroup parent) {
        final PhotoView result = PhotoView.inflate(inflater, parent, false);

        final byte[] binaryData = getContentValues().getAsByteArray(Photo.PHOTO);

        final Bitmap bitmap = binaryData != null
                ? BitmapFactory.decodeByteArray(binaryData, 0, binaryData.length)
                : null;
        result.setPhoto(bitmap);
        parent.addView(result);
        return result;
    }
}
