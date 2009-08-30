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
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditField;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.EntityDelta.ValuesDelta;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Custom view that provides all the editor interaction for a specific
 * {@link Contacts} represented through an {@link EntityDelta}. Callers can
 * reuse this view and quickly rebuild its contents through
 * {@link #setState(EntityDelta, ContactsSource)}.
 * <p>
 * Internal updates are performed against {@link ValuesDelta} so that the
 * source {@link Entity} can be swapped out. Any state-based changes, such as
 * adding {@link Data} rows or changing {@link EditType}, are performed through
 * {@link EntityModifier} to ensure that {@link ContactsSource} are enforced.
 */
public class ContactEditorView extends RelativeLayout implements OnClickListener {
    private LayoutInflater mInflater;

    private PhotoEditorView mPhoto;
    private GenericEditorView mName;

    private ViewGroup mGeneral;
    private ViewGroup mSecondary;

    private TextView mSecondaryHeader;

    private Drawable mSecondaryOpen;
    private Drawable mSecondaryClosed;

    public ContactEditorView(Context context) {
        super(context);
    }

    public ContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        mInflater = (LayoutInflater)getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mPhoto = (PhotoEditorView)findViewById(R.id.edit_photo);

        final int photoSize = getResources().getDimensionPixelSize(R.dimen.edit_photo_size);

        mName = (GenericEditorView)findViewById(R.id.edit_name);
        mName.setMinimumHeight(photoSize);
        mName.setDeletable(false);

        mGeneral = (ViewGroup)findViewById(R.id.sect_general);
        mSecondary = (ViewGroup)findViewById(R.id.sect_secondary);

        mSecondaryHeader = (TextView)findViewById(R.id.head_secondary);
        mSecondaryHeader.setOnClickListener(this);

        final Resources res = getResources();
        mSecondaryOpen = res.getDrawable(com.android.internal.R.drawable.expander_ic_maximized);
        mSecondaryClosed = res.getDrawable(com.android.internal.R.drawable.expander_ic_minimized);

        this.setSecondaryVisible(false);
    }

    /** {@inheritDoc} */
    public void onClick(View v) {
        // Toggle visibility of secondary kinds
        final boolean makeVisible = mSecondary.getVisibility() != View.VISIBLE;
        this.setSecondaryVisible(makeVisible);
    }

    /**
     * Set the visibility of secondary sections, along with header icon.
     */
    private void setSecondaryVisible(boolean makeVisible) {
        mSecondary.setVisibility(makeVisible ? View.VISIBLE : View.GONE);
        mSecondaryHeader.setCompoundDrawablesWithIntrinsicBounds(makeVisible ? mSecondaryOpen
                : mSecondaryClosed, null, null, null);
    }

    /**
     * Set the internal state for this view, given a current
     * {@link EntityDelta} state and the {@link ContactsSource} that
     * apply to that state.
     */
    public void setState(EntityDelta state, ContactsSource source) {
        // Remove any existing sections
        mGeneral.removeAllViews();
        mSecondary.removeAllViews();

        // Bail if invalid state or source
        if (state == null || source == null) return;

        // Make sure we have StructuredName
        EntityModifier.ensureKindExists(state, source, StructuredName.CONTENT_ITEM_TYPE);

        // Create editor sections for each possible data kind
        for (DataKind kind : source.getSortedDataKinds()) {
            // Skip kind of not editable
            if (!kind.editable) continue;

            final String mimeType = kind.mimeType;
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for structured name
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                mName.setValues(kind, primary, state);
            } else if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for photos
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                mPhoto.setValues(kind, primary, state);
            } else {
                // Otherwise use generic section-based editors
                if (kind.fieldList == null) continue;
                final ViewGroup parent = kind.secondary ? mSecondary : mGeneral;
                final KindSectionView section = (KindSectionView)mInflater.inflate(
                        R.layout.item_kind_section, parent, false);
                section.setState(kind, state);
                section.setId(kind.weight);
                parent.addView(section);
            }
        }
    }
}
