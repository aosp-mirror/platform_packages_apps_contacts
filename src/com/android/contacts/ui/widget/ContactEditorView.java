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
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.ViewIdGenerator;

import android.content.Context;
import android.content.Entity;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Custom view that provides all the editor interaction for a specific
 * {@link Contacts} represented through an {@link EntityDelta}. Callers can
 * reuse this view and quickly rebuild its contents through
 * {@link #setState(EntityDelta, ContactsSource, ViewIdGenerator)}.
 * <p>
 * Internal updates are performed against {@link ValuesDelta} so that the
 * source {@link Entity} can be swapped out. Any state-based changes, such as
 * adding {@link Data} rows or changing {@link EditType}, are performed through
 * {@link EntityModifier} to ensure that {@link ContactsSource} are enforced.
 */
public class ContactEditorView extends BaseContactEditorView {
    private View mPhotoStub;
    private GenericEditorView mName;
    private GroupMembershipView mGroupMembershipView;

    private ViewGroup mFields;

    private View mHeader;
    private View mBody;
    private ImageView mHeaderIcon;
    private TextView mHeaderAccountType;
    private TextView mHeaderAccountName;

    private Button mAddFieldButton;
    private boolean mExpanded = true;

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

        mInflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mPhoto = (PhotoEditorView)findViewById(R.id.edit_photo);
        mPhotoStub = findViewById(R.id.stub_photo);

        final int photoSize = getResources().getDimensionPixelSize(R.dimen.edit_photo_size);

        mName = (GenericEditorView)findViewById(R.id.edit_name);
        mName.setMinimumHeight(photoSize);
        mName.setDeletable(false);

        mFields = (ViewGroup)findViewById(R.id.sect_fields);

        mHeader = findViewById(R.id.header);
        mBody = findViewById(R.id.body);
        mHeader.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setExpanded(!mExpanded);
            }
        });
        mHeaderIcon = (ImageView) findViewById(R.id.header_icon);
        mHeaderAccountType = (TextView) findViewById(R.id.header_account_type);
        mHeaderAccountName = (TextView) findViewById(R.id.header_account_name);

        mAddFieldButton = (Button) findViewById(R.id.button_add_field);
        mAddFieldButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddInformationPopupWindow();
            }
        });
    }

    /**
     * Set the internal state for this view, given a current
     * {@link EntityDelta} state and the {@link ContactsSource} that
     * apply to that state.
     */
    @Override
    public void setState(EntityDelta state, ContactsSource source, ViewIdGenerator vig) {
        // Remove any existing sections
        mFields.removeAllViews();

        // Bail if invalid state or source
        if (state == null || source == null) return;

        setId(vig.getId(state, null, null, ViewIdGenerator.NO_VIEW_INDEX));

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
        mPhoto.setEnabled(true);
        mName.setEnabled(true);

        // Show and hide the appropriate views
        mFields.setVisibility(View.VISIBLE);
        mName.setVisibility(View.VISIBLE);

        DataKind groupMembershipKind = source.getKindForMimetype(GroupMembership.CONTENT_ITEM_TYPE);
        if (groupMembershipKind != null) {
            mGroupMembershipView = (GroupMembershipView)mInflater.inflate(
                    R.layout.item_group_membership, mFields, false);
            mGroupMembershipView.setKind(groupMembershipKind);
        }

        // Create editor sections for each possible data kind
        for (DataKind kind : source.getSortedDataKinds()) {
            // Skip kind of not editable
            if (!kind.editable) continue;

            final String mimeType = kind.mimeType;
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for structured name
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                mName.setValues(kind, primary, state, false, vig);
            } else if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for photos
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                mPhoto.setValues(kind, primary, state, false, vig);
                mPhotoStub.setVisibility(View.VISIBLE);
            } else if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                if (mGroupMembershipView != null) {
                    mGroupMembershipView.setState(state);
                }
            } else {
                // Otherwise use generic section-based editors
                if (kind.fieldList == null) continue;
                final KindSectionView section = (KindSectionView)mInflater.inflate(
                        R.layout.item_kind_section, mFields, false);
                section.setState(kind, state, false, vig);
                mFields.addView(section);
            }
        }

        if (mGroupMembershipView != null) {
            mFields.addView(mGroupMembershipView);
        }
    }

    @Override
    public void setGroupMetaData(Cursor groupMetaData) {
        if (mGroupMembershipView != null) {
            mGroupMembershipView.setGroupMetaData(groupMetaData);
        }
    }

    public GenericEditorView getNameEditor() {
        return mName;
    }

    @Override
    public long getRawContactId() {
        return mRawContactId;
    }

    /* package */ void setExpanded(boolean value) {
        // only allow collapsing if we are one of several children
        final boolean newValue;
        if (getParent() instanceof ViewGroup && ((ViewGroup) getParent()).getChildCount() == 1) {
            newValue = true;
        } else {
            newValue = value;
        }

        if (newValue == mExpanded) return;
        mExpanded = newValue;
        mBody.setVisibility(newValue ? View.VISIBLE : View.GONE);
    }

    private void showAddInformationPopupWindow() {
        final ArrayList<KindSectionView> fields =
                new ArrayList<KindSectionView>(mFields.getChildCount());

        final PopupMenu popupMenu = new PopupMenu(getContext(), mAddFieldButton);
        final Menu menu = popupMenu.getMenu();
        for (int i = 0; i < mFields.getChildCount(); i++) {
            View child = mFields.getChildAt(i);
            if (child instanceof KindSectionView) {
                final KindSectionView sectionView = (KindSectionView) child;
                // not a list and already exists? ignore
                if (!sectionView.getKind().isList && sectionView.getEditorCount() != 0) {
                    continue;
                }
                menu.add(Menu.NONE, i, Menu.NONE, sectionView.getTitle());
                fields.add(sectionView);
            }
        }
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final KindSectionView view = fields.get(item.getItemId());
                view.addItem();
                return true;
            }
        });

        popupMenu.show();
    }
}
