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

package com.android.contacts.ui.widget;

import com.android.contacts.R;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.model.Editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Simple editor for {@link Photo}.
 */
public class PhotoEditorView extends ImageView implements Editor {
    private ValuesDelta mEntry;

    public PhotoEditorView(Context context) {
        super(context);
    }

    public PhotoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@inheritDoc} */
    public void onFieldChanged(String column, String value) {
        throw new UnsupportedOperationException("Photos don't support direct field changes");
    }

    public void setValues(DataKind kind, ValuesDelta values, EntityDelta state) {
        mEntry = values;
        if (values != null) {
            // Try decoding photo if actual entry
            final byte[] photoBytes = values.getAsByteArray(Photo.PHOTO);
            if (photoBytes != null) {
                final Bitmap photo = BitmapFactory.decodeByteArray(photoBytes, 0,
                        photoBytes.length);

                setScaleType(ImageView.ScaleType.CENTER_CROP);
                setImageBitmap(photo);
            } else {
                resetDefault();
            }
        } else {
            resetDefault();
        }
    }

    protected void resetDefault() {
        // Invalid photo, show default "add photo" placeholder
        setScaleType(ImageView.ScaleType.CENTER);
        setImageResource(R.drawable.ic_menu_add_picture);
    }

    public void setEditorListener(EditorListener listener) {
    }
}
