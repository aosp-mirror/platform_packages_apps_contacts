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

import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.Editor.EditorListener;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.ViewIdGenerator;

import android.content.Context;
import android.content.Entity;
import android.graphics.Bitmap;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

/**
 * Base view that provides common code for the editor interaction for a specific
 * RawContact represented through an {@link EntityDelta}. Callers can
 * reuse this view and quickly rebuild its contents through
 * {@link #setState(EntityDelta, ContactsSource)}.
 * <p>
 * Internal updates are performed against {@link ValuesDelta} so that the
 * source {@link Entity} can be swapped out. Any state-based changes, such as
 * adding {@link Data} rows or changing {@link EditType}, are performed through
 * {@link EntityModifier} to ensure that {@link ContactsSource} are enforced.
 */
public abstract class BaseContactEditorView extends LinearLayout {
    protected LayoutInflater mInflater;

    protected PhotoEditorView mPhoto;
    protected boolean mHasPhotoEditor = false;

    public BaseContactEditorView(Context context) {
        super(context);
    }

    public BaseContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Assign the given {@link Bitmap} to the internal {@link PhotoEditorView}
     * for the {@link EntityDelta} currently being edited.
     */
    public void setPhotoBitmap(Bitmap bitmap) {
        mPhoto.setPhotoBitmap(bitmap);
    }

    /**
     * Return true if the current {@link RawContacts} supports {@link Photo},
     * which means that {@link PhotoEditorView} is enabled.
     */
    public boolean hasPhotoEditor() {
        return mHasPhotoEditor;
    }

    /**
     * Return true if internal {@link PhotoEditorView} has a {@link Photo} set.
     */
    public boolean hasSetPhoto() {
        return mPhoto.hasSetPhoto();
    }

    public PhotoEditorView getPhotoEditor() {
        return mPhoto;
    }

    /**
     * @return the RawContact ID that this editor is editing.
     */
    public abstract long getRawContactId();

    /**
     * Set the internal state for this view, given a current
     * {@link EntityDelta} state and the {@link ContactsSource} that
     * apply to that state.
     */
    public abstract void setState(EntityDelta state, ContactsSource source, ViewIdGenerator vig);

    /**
     * Sets the {@link EditorListener} on the name field
     */
    public abstract void setNameEditorListener(EditorListener listener);
}
