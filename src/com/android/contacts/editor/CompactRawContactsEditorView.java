/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountType.EditField;
import com.android.contacts.common.model.dataitem.DataKind;

import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * View to display information from multiple {@link RawContactDelta}s grouped together
 * (e.g. all the phone numbers from a {@link com.android.contacts.common.model.Contact} together.
 */
public class CompactRawContactsEditorView extends LinearLayout {

    private static final String TAG = "CompactEditorView";

    private AccountTypeManager mAccountTypeManager;
    private LayoutInflater mLayoutInflater;
    private ViewIdGenerator mViewIdGenerator;

    private ViewGroup mNames;
    private ViewGroup mPhoneticNames;
    private ViewGroup mNicknames;
    private ViewGroup mPhoneNumbers;
    private ViewGroup mEmails;
    private ViewGroup mOther;

    public CompactRawContactsEditorView(Context context) {
        super(context);
    }

    public CompactRawContactsEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAccountTypeManager = AccountTypeManager.getInstance(getContext());
        mLayoutInflater = (LayoutInflater)
                getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mNames = (LinearLayout) findViewById(R.id.names);
        mPhoneticNames = (LinearLayout) findViewById(R.id.phonetic_names);
        mNicknames = (LinearLayout) findViewById(R.id.nicknames);
        mPhoneNumbers = (LinearLayout) findViewById(R.id.phone_numbers);
        mEmails = (LinearLayout) findViewById(R.id.emails);
        mOther = (LinearLayout) findViewById(R.id.other);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setEnabled(enabled, mNames);
        setEnabled(enabled, mPhoneticNames);
        setEnabled(enabled, mNicknames);
        setEnabled(enabled, mPhoneNumbers);
        setEnabled(enabled, mEmails);
        setEnabled(enabled, mOther);
    }

    private void setEnabled(boolean enabled, ViewGroup viewGroup) {
        if (viewGroup != null) {
            final int childCount = viewGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                viewGroup.getChildAt(i).setEnabled(enabled);
            }
        }
    }

    public void setState(RawContactDeltaList rawContactDeltas, ViewIdGenerator viewIdGenerator) {
        mNames.removeAllViews();
        mPhoneticNames.removeAllViews();
        mNicknames.removeAllViews();
        mPhoneNumbers.removeAllViews();
        mEmails.removeAllViews();
        mOther.removeAllViews();

        if (rawContactDeltas == null || rawContactDeltas.isEmpty()) {
            return;
        }

        mViewIdGenerator = viewIdGenerator;

        setId(mViewIdGenerator.getId(rawContactDeltas.get(0), /* dataKind =*/ null,
                /* valuesDelta =*/ null, ViewIdGenerator.NO_VIEW_INDEX));

        addEditorViews(rawContactDeltas);
        removeExtraEmptyStructuredNames();
    }

    private void addEditorViews(RawContactDeltaList rawContactDeltas) {
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) {
                continue;
            }
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
            for (DataKind dataKind : accountType.getSortedDataKinds()) {
                if (!dataKind.editable) {
                    continue;
                }
                final String mimeType = dataKind.mimeType;
                log(Log.VERBOSE, mimeType + " " + dataKind.fieldList.size() + " field(s)");
                if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)
                        || GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Photos are handled separately and group membership is not supported
                    continue;
                } else if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final ValuesDelta valuesDelta = rawContactDelta.getPrimaryEntry(mimeType);
                    if (valuesDelta != null) {
                        mNames.addView(inflateStructuredNameEditorView(
                                mNames, accountType, valuesDelta, rawContactDelta));
                    }
                } else if (DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(mimeType)) {
                    // Use the StructuredName mime type to get values
                    if (hasNonEmptyPrimaryValuesDelta(
                            rawContactDelta, StructuredName.CONTENT_ITEM_TYPE, dataKind)) {
                        final ValuesDelta valuesDelta = rawContactDelta.getPrimaryEntry(
                                StructuredName.CONTENT_ITEM_TYPE);
                        mPhoneticNames.addView(inflatePhoneticNameEditorView(
                                mPhoneticNames, accountType, valuesDelta, rawContactDelta));
                    }
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    if (hasNonEmptyValuesDelta(rawContactDelta, mimeType, dataKind)) {
                        mNicknames.addView(inflateNicknameEditorView(
                                mNicknames, dataKind, rawContactDelta));
                    }
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    if (hasNonEmptyValuesDelta(rawContactDelta, mimeType, dataKind)) {
                        mPhoneNumbers.addView(inflateKindSectionView(
                                mPhoneNumbers, dataKind, rawContactDelta));
                    }
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    if (hasNonEmptyValuesDelta(rawContactDelta, mimeType, dataKind)) {
                        mEmails.addView(inflateKindSectionView(
                                mEmails, dataKind, rawContactDelta));
                    }
                } else if (hasNonEmptyValuesDelta(rawContactDelta, mimeType, dataKind)) {
                    mOther.addView(inflateKindSectionView(
                            mOther, dataKind, rawContactDelta));
                }
            }
        }
    }

    private void removeExtraEmptyStructuredNames() {
        // If there is one (or less) structured names, leave it whether it is empty or not
        if (mNames.getChildCount() <= 1) {
            return;
        }
        // Determine if there are any non-empty names
        boolean hasAtLeastOneNonEmptyName = false;
        for (int i = 0; i < mNames.getChildCount(); i++) {
            final StructuredNameEditorView childView =
                    (StructuredNameEditorView) mNames.getChildAt(i);
            if (!childView.isEmpty()) {
                hasAtLeastOneNonEmptyName = true;
                break;
            }
        }
        if (hasAtLeastOneNonEmptyName) {
            // There is at least one non-empty name, remove all the empty ones
            for (int i = 0; i < mNames.getChildCount(); i++) {
                final StructuredNameEditorView childView =
                        (StructuredNameEditorView) mNames.getChildAt(i);
                if (childView.isEmpty()) {
                    childView.setVisibility(View.GONE);
                }
            }
        } else {
            // There is no non-empty name, keep the first empty view and remove the rest
            for (int i = 1; i < mNames.getChildCount(); i++) {
                final StructuredNameEditorView childView =
                        (StructuredNameEditorView) mNames.getChildAt(i);
                childView.setVisibility(View.GONE);
            }
        }
    }

    private static boolean hasNonEmptyValuesDelta(RawContactDelta rawContactDelta,
            String mimeType, DataKind dataKind) {
        return !getNonEmptyValuesDeltas(rawContactDelta, mimeType, dataKind).isEmpty();
    }

    private static List<ValuesDelta> getNonEmptyValuesDeltas(RawContactDelta rawContactDelta,
            String mimeType, DataKind dataKind) {
        final List<ValuesDelta> result = new ArrayList<>();
        if (rawContactDelta == null) {
            log(Log.VERBOSE, "Null RawContactDelta");
            return result;
        }
        if (!rawContactDelta.hasMimeEntries(mimeType)) {
            log(Log.VERBOSE, "No ValueDeltas");
            return result;
        }
        for (ValuesDelta valuesDelta : rawContactDelta.getMimeEntries(mimeType)) {
            if (valuesDelta == null) {
                log(Log.VERBOSE, "Null valuesDelta");
            }
            for (EditField editField : dataKind.fieldList) {
                final String column = editField.column;
                final String value = valuesDelta == null ? null : valuesDelta.getAsString(column);
                log(Log.VERBOSE, "Field " + column + " empty=" + TextUtils.isEmpty(value) +
                        " value=" + value);
                if (!TextUtils.isEmpty(value)) {
                    result.add(valuesDelta);
                }
            }
        }
        return result;
    }

    private static boolean hasNonEmptyPrimaryValuesDelta(RawContactDelta rawContactDelta,
            String mimeType, DataKind dataKind) {
        final ValuesDelta valuesDelta = rawContactDelta.getPrimaryEntry(mimeType);
        if (valuesDelta == null) {
            return false;
        }
        for (EditField editField : dataKind.fieldList) {
            final String column = editField.column;
            final String value = valuesDelta == null ? null : valuesDelta.getAsString(column);
            log(Log.VERBOSE, "Field (primary) " + column + " empty=" + TextUtils.isEmpty(value) +
                    " value=" + value);
            if (!TextUtils.isEmpty(value)) {
                return true;
            }
        }
        return false;
    }

    private StructuredNameEditorView inflateStructuredNameEditorView(ViewGroup viewGroup,
            AccountType accountType, ValuesDelta valuesDelta, RawContactDelta rawContactDelta) {
        final StructuredNameEditorView result = (StructuredNameEditorView) mLayoutInflater.inflate(
                R.layout.structured_name_editor_view, viewGroup, /* attachToRoot =*/ false);
        result.setValues(
                accountType.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME),
                valuesDelta,
                rawContactDelta,
                /* readOnly =*/ false,
                mViewIdGenerator);
        return result;
    }

    private PhoneticNameEditorView inflatePhoneticNameEditorView(ViewGroup viewGroup,
            AccountType accountType, ValuesDelta valuesDelta, RawContactDelta rawContactDelta) {
        final PhoneticNameEditorView result = (PhoneticNameEditorView) mLayoutInflater.inflate(
                R.layout.phonetic_name_editor_view, viewGroup, /* attachToRoot =*/ false);
        result.setValues(
                accountType.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME),
                valuesDelta,
                rawContactDelta,
                /* readOnly =*/ false,
                mViewIdGenerator);
        return result;
    }

    private KindSectionView inflateNicknameEditorView(ViewGroup viewGroup, DataKind dataKind,
            RawContactDelta rawContactDelta) {
        final KindSectionView result = (KindSectionView) mLayoutInflater.inflate(
                R.layout.item_kind_section, viewGroup, /* attachToRoot =*/ false);
        result.setState(
                dataKind,
                rawContactDelta,
                /* readOnly =*/ false,
                mViewIdGenerator);
        return result;
    }

    private KindSectionView inflateKindSectionView(ViewGroup viewGroup, DataKind dataKind,
            RawContactDelta rawContactDelta) {
        final KindSectionView result = (KindSectionView) mLayoutInflater.inflate(
                R.layout.item_kind_section, viewGroup, /* attachToRoot =*/ false);
        result.setState(
                dataKind,
                rawContactDelta,
                /* readOnly =*/ false,
                mViewIdGenerator);
        return result;
    }

    private static void log(int level, String message) {
        log(TAG, level, message);
    }

    private static void log(String tag, int level, String message) {
        if (Log.isLoggable(tag, level)) {
            switch (level) {
                case Log.VERBOSE:
                    Log.v(tag, message);
                    break;
                case Log.DEBUG:
                    Log.d(tag, message);
                    break;
                case Log.INFO:
                    Log.i(tag, message);
                    break;
                case Log.WARN:
                    Log.w(tag, message);
                    break;
                case Log.ERROR:
                    Log.e(tag, message);
                    break;
                default:
                    Log.v(tag, message);
                    break;
            }
        }
    }
}
