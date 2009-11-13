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
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.Editor.EditorListener;
import com.android.contacts.model.EntityDelta.ValuesDelta;

import android.content.Context;
import android.content.Entity;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

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
public class ContactEditorView extends BaseContactEditorView implements OnClickListener {
    private TextView mReadOnly;
    private TextView mReadOnlyName;

    private View mPhotoStub;
    private GenericEditorView mName;

    private ViewGroup mGeneral;
    private ViewGroup mSecondary;
    private boolean mSecondaryVisible;

    private TextView mSecondaryHeader;

    private Drawable mSecondaryOpen;
    private Drawable mSecondaryClosed;

    private View mHeaderColorBar;
    private View mSideBar;
    private ImageView mHeaderIcon;
    private TextView mHeaderAccountType;
    private TextView mHeaderAccountName;

    private long mRawContactId = -1;

    public ContactEditorView(Context context) {
        super(context);
    }

    public ContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mInflater = (LayoutInflater)getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mPhoto = (PhotoEditorView)findViewById(R.id.edit_photo);
        mPhotoStub = findViewById(R.id.stub_photo);

        final int photoSize = getResources().getDimensionPixelSize(R.dimen.edit_photo_size);

        mReadOnly = (TextView)findViewById(R.id.edit_read_only);

        mName = (GenericEditorView)findViewById(R.id.edit_name);
        mName.setMinimumHeight(photoSize);
        mName.setDeletable(false);

        mReadOnlyName = (TextView) findViewById(R.id.read_only_name);

        mGeneral = (ViewGroup)findViewById(R.id.sect_general);
        mSecondary = (ViewGroup)findViewById(R.id.sect_secondary);

        mHeaderColorBar = findViewById(R.id.header_color_bar);
        mSideBar = findViewById(R.id.color_bar);
        mHeaderIcon = (ImageView) findViewById(R.id.header_icon);
        mHeaderAccountType = (TextView) findViewById(R.id.header_account_type);
        mHeaderAccountName = (TextView) findViewById(R.id.header_account_name);

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
        mSecondaryVisible = makeVisible;
    }

    /**
     * Set the internal state for this view, given a current
     * {@link EntityDelta} state and the {@link ContactsSource} that
     * apply to that state.
     */
    @Override
    public void setState(EntityDelta state, ContactsSource source) {
        // Remove any existing sections
        mGeneral.removeAllViews();
        mSecondary.removeAllViews();

        // Bail if invalid state or source
        if (state == null || source == null) return;

        // Make sure we have StructuredName
        EntityModifier.ensureKindExists(state, source, StructuredName.CONTENT_ITEM_TYPE);

        // Fill in the header info
        ValuesDelta values = state.getValues();
        String accountName = values.getAsString(RawContacts.ACCOUNT_NAME);
        CharSequence accountType = source.getDisplayLabel(mContext);
        if (TextUtils.isEmpty(accountType)) {
            accountType = mContext.getString(R.string.account_phone);
        }
        if (!TextUtils.isEmpty(accountName)) {
            mHeaderAccountName.setText(
                    mContext.getString(R.string.from_account_format, accountName));
        }
        mHeaderAccountType.setText(mContext.getString(R.string.account_type_format, accountType));
        mHeaderIcon.setImageDrawable(source.getDisplayIcon(mContext));

        mRawContactId = values.getAsLong(RawContacts._ID);

        // Show photo editor when supported
        EntityModifier.ensureKindExists(state, source, Photo.CONTENT_ITEM_TYPE);
        mHasPhotoEditor = (source.getKindForMimetype(Photo.CONTENT_ITEM_TYPE) != null);
        mPhoto.setVisibility(mHasPhotoEditor ? View.VISIBLE : View.GONE);
        mPhoto.setEnabled(!source.readOnly);
        mName.setEnabled(!source.readOnly);

        boolean readOnly = source.readOnly;
        // Show and hide the appropriate views
        if (readOnly) {
            mGeneral.setVisibility(View.GONE);
            mName.setVisibility(View.GONE);
            mReadOnly.setVisibility(View.VISIBLE);
            mReadOnly.setText(mContext.getString(R.string.contact_read_only, accountType));
            mReadOnlyName.setVisibility(View.VISIBLE);
        } else {
            mGeneral.setVisibility(View.VISIBLE);
            mName.setVisibility(View.VISIBLE);
            mReadOnly.setVisibility(View.GONE);
            mReadOnlyName.setVisibility(View.GONE);
        }

        // Create editor sections for each possible data kind
        for (DataKind kind : source.getSortedDataKinds()) {
            // Skip kind of not editable
            if (!kind.editable) continue;

            final String mimeType = kind.mimeType;
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for structured name
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                if (!readOnly) {
                    mName.setValues(kind, primary, state, source.readOnly);
                } else {
                    String displayName = primary.getAsString(StructuredName.DISPLAY_NAME);
                    mReadOnlyName.setText(displayName);
                }
            } else if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for photos
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                mPhoto.setValues(kind, primary, state, source.readOnly);
                if (readOnly && !mPhoto.hasSetPhoto()) {
                    mPhotoStub.setVisibility(View.GONE);
                } else {
                    mPhotoStub.setVisibility(View.VISIBLE);
                }
            } else if (!readOnly) {
                // Otherwise use generic section-based editors
                if (kind.fieldList == null) continue;
                final ViewGroup parent = kind.secondary ? mSecondary : mGeneral;
                final KindSectionView section = (KindSectionView)mInflater.inflate(
                        R.layout.item_kind_section, parent, false);
                section.setState(kind, state, source.readOnly);
                section.setId(kind.weight);
                parent.addView(section);
            }
        }

        if (!readOnly && mSecondary.getChildCount() > 0) {
            // There exist secondary elements, show the header and honor mSecondaryVisible
            mSecondaryHeader.setVisibility(View.VISIBLE);
            if (mSecondaryVisible) {
                mSecondary.setVisibility(View.VISIBLE);
            } else {
                mSecondary.setVisibility(View.GONE);
            }
        } else {
            // There are no secondary elements, hide the whole thing
            mSecondaryHeader.setVisibility(View.GONE);
            mSecondary.setVisibility(View.GONE);
        }
    }

    /**
     * Sets the {@link EditorListener} on the name field
     */
    @Override
    public void setNameEditorListener(EditorListener listener) {
        mName.setEditorListener(listener);
    }

    @Override
    public long getRawContactId() {
        return mRawContactId;
    }
}
