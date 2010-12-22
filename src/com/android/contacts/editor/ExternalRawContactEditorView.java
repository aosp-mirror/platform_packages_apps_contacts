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

package com.android.contacts.editor;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.editor.ExternalRawContactEditorView.Listener;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountType.DataKind;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.model.EntityModifier;

import android.accounts.Account;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Custom view that displays external contacts in the edit screen.
 */
public class ExternalRawContactEditorView extends BaseRawContactEditorView
        implements OnClickListener {
    private LayoutInflater mInflater;

    private View mPhotoStub;
    private TextView mName;
    private TextView mReadOnlyWarning;
    private Button mEditExternallyButton;
    private ViewGroup mGeneral;

    private ImageView mHeaderIcon;
    private TextView mHeaderAccountType;
    private TextView mHeaderAccountName;

    private String mAccountName;
    private String mAccountType;
    private long mRawContactId = -1;

    private Listener mListener;

    public interface Listener {
        void onExternalEditorRequest(Account account, Uri uri);
    }

    public ExternalRawContactEditorView(Context context) {
        super(context);
    }

    public ExternalRawContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mInflater = (LayoutInflater)getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mPhotoStub = findViewById(R.id.stub_photo);

        mName = (TextView) findViewById(R.id.read_only_name);
        mReadOnlyWarning = (TextView) findViewById(R.id.read_only_warning);
        mEditExternallyButton = (Button) findViewById(R.id.button_edit_externally);
        mEditExternallyButton.setOnClickListener(this);
        mGeneral = (ViewGroup)findViewById(R.id.sect_general);

        mHeaderIcon = (ImageView) findViewById(R.id.header_icon);
        mHeaderAccountType = (TextView) findViewById(R.id.header_account_type);
        mHeaderAccountName = (TextView) findViewById(R.id.header_account_name);
    }

    /**
     * Set the internal state for this view, given a current
     * {@link EntityDelta} state and the {@link AccountType} that
     * apply to that state.
     *
     * TODO: make this more generic using data from the source
     */
    @Override
    public void setState(EntityDelta state, AccountType source, ViewIdGenerator vig) {
        // Remove any existing sections
        mGeneral.removeAllViews();

        // Bail if invalid state or source
        if (state == null || source == null) return;

        // Make sure we have StructuredName
        EntityModifier.ensureKindExists(state, source, StructuredName.CONTENT_ITEM_TYPE);

        // Fill in the header info
        ValuesDelta values = state.getValues();
        mAccountName = values.getAsString(RawContacts.ACCOUNT_NAME);
        mAccountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
        CharSequence accountType = source.getDisplayLabel(mContext);
        if (TextUtils.isEmpty(accountType)) {
            accountType = mContext.getString(R.string.account_phone);
        }
        if (!TextUtils.isEmpty(mAccountName)) {
            mHeaderAccountName.setText(
                    mContext.getString(R.string.from_account_format, mAccountName));
        }
        mHeaderAccountType.setText(mContext.getString(R.string.account_type_format, accountType));
        mHeaderIcon.setImageDrawable(source.getDisplayIcon(mContext));

        mRawContactId = values.getAsLong(RawContacts._ID);

        ValuesDelta primary;

        // Photo
        DataKind kind = source.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
        if (kind != null) {
            EntityModifier.ensureKindExists(state, source, Photo.CONTENT_ITEM_TYPE);
            boolean hasPhotoEditor = source.getKindForMimetype(Photo.CONTENT_ITEM_TYPE) != null;
            setHasPhotoEditor(hasPhotoEditor);
            primary = state.getPrimaryEntry(Photo.CONTENT_ITEM_TYPE);
            getPhotoEditor().setValues(kind, primary, state, source.readOnly, vig);
            if (!hasPhotoEditor || !getPhotoEditor().hasSetPhoto()) {
                mPhotoStub.setVisibility(View.GONE);
            } else {
                mPhotoStub.setVisibility(View.VISIBLE);
            }
        } else {
            mPhotoStub.setVisibility(View.VISIBLE);
        }

        // Name
        primary = state.getPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
        mName.setText(primary.getAsString(StructuredName.DISPLAY_NAME));

        if (source.readOnly) {
            mReadOnlyWarning.setText(mContext.getString(R.string.contact_read_only, accountType));
            mReadOnlyWarning.setVisibility(View.VISIBLE);
            mEditExternallyButton.setVisibility(View.GONE);
        } else {
            mReadOnlyWarning.setVisibility(View.GONE);
            mEditExternallyButton.setVisibility(View.VISIBLE);
        }

        // Phones
        ArrayList<ValuesDelta> phones = state.getMimeEntries(Phone.CONTENT_ITEM_TYPE);
        if (phones != null) {
            for (ValuesDelta phone : phones) {
                View field = mInflater.inflate(
                        R.layout.item_read_only_field, mGeneral, false);
                TextView v;
                v = (TextView) field.findViewById(R.id.label);
                v.setText(mContext.getText(R.string.phoneLabelsGroup));
                v = (TextView) field.findViewById(R.id.data);
                v.setText(PhoneNumberUtils.formatNumber(phone.getAsString(Phone.NUMBER),
                        phone.getAsString(Phone.NORMALIZED_NUMBER),
                        ContactsUtils.getCurrentCountryIso(getContext())));
                mGeneral.addView(field);
            }
        }

        // Emails
        ArrayList<ValuesDelta> emails = state.getMimeEntries(Email.CONTENT_ITEM_TYPE);
        if (emails != null) {
            for (ValuesDelta email : emails) {
                View field = mInflater.inflate(
                        R.layout.item_read_only_field, mGeneral, false);
                TextView v;
                v = (TextView) field.findViewById(R.id.label);
                v.setText(mContext.getText(R.string.emailLabelsGroup));
                v = (TextView) field.findViewById(R.id.data);
                v.setText(email.getAsString(Email.DATA));
                mGeneral.addView(field);
            }
        }

        // Hide mGeneral if it's empty
        if (mGeneral.getChildCount() > 0) {
            mGeneral.setVisibility(View.VISIBLE);
        } else {
            mGeneral.setVisibility(View.GONE);
        }
    }

    @Override
    public long getRawContactId() {
        return mRawContactId;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_edit_externally) {
            if (mListener != null) {
                mListener.onExternalEditorRequest(new Account(mAccountName, mAccountType),
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, mRawContactId));
            }
        }
    }
}
