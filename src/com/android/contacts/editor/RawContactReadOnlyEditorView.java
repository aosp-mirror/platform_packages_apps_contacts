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

import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.DataKind;

import java.util.ArrayList;

/**
 * Custom view that displays external contacts in the edit screen.
 */
public class RawContactReadOnlyEditorView extends BaseRawContactEditorView
        implements OnClickListener {
    private LayoutInflater mInflater;

    private TextView mName;
    private Button mEditExternallyButton;
    private ViewGroup mGeneral;

    private TextView mAccountHeaderTypeTextView;
    private TextView mAccountHeaderNameTextView;

    private String mAccountName;
    private String mAccountType;
    private String mDataSet;
    private long mRawContactId = -1;

    public RawContactReadOnlyEditorView(Context context) {
        super(context);
    }

    public RawContactReadOnlyEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mInflater = (LayoutInflater)getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mName = (TextView) findViewById(R.id.read_only_name);
        mEditExternallyButton = (Button) findViewById(R.id.button_edit_externally);
        mEditExternallyButton.setOnClickListener(this);
        mGeneral = (ViewGroup)findViewById(R.id.sect_general);

        mAccountHeaderTypeTextView = (TextView) findViewById(R.id.account_type);
        mAccountHeaderNameTextView = (TextView) findViewById(R.id.account_name);
    }

    /**
     * Set the internal state for this view, given a current
     * {@link RawContactDelta} state and the {@link AccountType} that
     * apply to that state.
     */
    @Override
    public void setState(RawContactDelta state, AccountType type, ViewIdGenerator vig,
            boolean isProfile) {
        // Remove any existing sections
        mGeneral.removeAllViews();

        // Bail if invalid state or source
        if (state == null || type == null) return;

        // Make sure we have StructuredName
        RawContactModifier.ensureKindExists(state, type, StructuredName.CONTENT_ITEM_TYPE);

        // Fill in the header info
        mAccountName = state.getAccountName();
        mAccountType = state.getAccountType();
        mDataSet = state.getDataSet();

        final Pair<String,String> accountInfo = EditorUiUtils.getAccountInfo(getContext(),
                isProfile, state.getAccountName(), type);
        if (accountInfo == null) {
            // Hide this view so the other text view will be centered vertically
            mAccountHeaderNameTextView.setVisibility(View.GONE);
        } else {
            if (accountInfo.first == null) {
                mAccountHeaderNameTextView.setVisibility(View.GONE);
            } else {
                mAccountHeaderNameTextView.setVisibility(View.VISIBLE);
                mAccountHeaderNameTextView.setText(accountInfo.first);
            }
            mAccountHeaderTypeTextView.setText(accountInfo.second);
        }
        updateAccountHeaderContentDescription();

        // TODO: Expose data set in the UI somehow?

        mRawContactId = state.getRawContactId();

        ValuesDelta primary;

        // Photo
        DataKind kind = type.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
        if (kind != null) {
            RawContactModifier.ensureKindExists(state, type, Photo.CONTENT_ITEM_TYPE);
            boolean hasPhotoEditor = type.getKindForMimetype(Photo.CONTENT_ITEM_TYPE) != null;
            setHasPhotoEditor(hasPhotoEditor);
            primary = state.getPrimaryEntry(Photo.CONTENT_ITEM_TYPE);
            getPhotoEditor().setValues(kind, primary, state, !type.areContactsWritable(), vig);
        }

        // Name
        primary = state.getPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
        mName.setText(primary != null ? primary.getAsString(StructuredName.DISPLAY_NAME) :
                getContext().getString(R.string.missing_name));

        if (type.getEditContactActivityClassName() != null) {
            mEditExternallyButton.setVisibility(View.VISIBLE);
        } else {
            mEditExternallyButton.setVisibility(View.GONE);
        }

        final Resources res = getContext().getResources();
        // Phones
        final ArrayList<ValuesDelta> phones = state.getMimeEntries(Phone.CONTENT_ITEM_TYPE);
        final Drawable phoneDrawable = getResources().getDrawable(R.drawable.ic_phone_24dp);
        final String phoneContentDescription = res.getString(R.string.header_phone_entry);
        if (phones != null) {
            boolean isFirstPhoneBound = true;
            for (ValuesDelta phone : phones) {
                final String phoneNumber = phone.getPhoneNumber();
                if (TextUtils.isEmpty(phoneNumber)) {
                    continue;
                }
                final String formattedNumber = PhoneNumberUtils.formatNumber(
                        phoneNumber, phone.getPhoneNormalizedNumber(),
                        GeoUtil.getCurrentCountryIso(getContext()));
                CharSequence phoneType = null;
                if (phone.phoneHasType()) {
                    phoneType = Phone.getTypeLabel(
                            res, phone.getPhoneType(), phone.getPhoneLabel());
                }
                bindData(phoneDrawable, phoneContentDescription, formattedNumber, phoneType,
                        isFirstPhoneBound, true);
                isFirstPhoneBound = false;
            }
        }

        // Emails
        final ArrayList<ValuesDelta> emails = state.getMimeEntries(Email.CONTENT_ITEM_TYPE);
        final Drawable emailDrawable = getResources().getDrawable(R.drawable.ic_email_24dp);
        final String emailContentDescription = res.getString(R.string.header_email_entry);
        if (emails != null) {
            boolean isFirstEmailBound = true;
            for (ValuesDelta email : emails) {
                final String emailAddress = email.getEmailData();
                if (TextUtils.isEmpty(emailAddress)) {
                    continue;
                }
                CharSequence emailType = null;
                if (email.emailHasType()) {
                    emailType = Email.getTypeLabel(
                            res, email.getEmailType(), email.getEmailLabel());
                }
                bindData(emailDrawable, emailContentDescription, emailAddress, emailType,
                        isFirstEmailBound);
                isFirstEmailBound = false;
            }
        }

        // Hide mGeneral if it's empty
        if (mGeneral.getChildCount() > 0) {
            mGeneral.setVisibility(View.VISIBLE);
        } else {
            mGeneral.setVisibility(View.GONE);
        }
    }

    private void bindData(Drawable icon, String iconContentDescription, CharSequence data,
            CharSequence type, boolean isFirstEntry) {
        bindData(icon, iconContentDescription, data, type, isFirstEntry, false);
    }

    private void bindData(Drawable icon, String iconContentDescription, CharSequence data,
            CharSequence type, boolean isFirstEntry, boolean forceLTR) {
        final View field = mInflater.inflate(R.layout.item_read_only_field, mGeneral, false);
        if (isFirstEntry) {
            final ImageView imageView = (ImageView) field.findViewById(R.id.kind_icon);
            imageView.setImageDrawable(icon);
            imageView.setContentDescription(iconContentDescription);
        } else {
            final ImageView imageView = (ImageView) field.findViewById(R.id.kind_icon);
            imageView.setVisibility(View.INVISIBLE);
            imageView.setContentDescription(null);
        }
        final TextView dataView = (TextView) field.findViewById(R.id.data);
        dataView.setText(data);
        if (forceLTR) {
            dataView.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
        final TextView typeView = (TextView) field.findViewById(R.id.type);
        if (!TextUtils.isEmpty(type)) {
            typeView.setText(type);
        } else {
            typeView.setVisibility(View.GONE);
        }

        mGeneral.addView(field);
    }

    @Override
    public long getRawContactId() {
        return mRawContactId;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_edit_externally) {
            if (mListener != null) {
                mListener.onExternalEditorRequest(
                        new AccountWithDataSet(mAccountName, mAccountType, mDataSet),
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, mRawContactId));
            }
        }
    }
}
