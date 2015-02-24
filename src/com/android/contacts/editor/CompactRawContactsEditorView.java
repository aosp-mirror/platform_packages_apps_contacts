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
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountType.EditField;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.editor.CompactContactEditorFragment.PhotoHandler;

import android.content.Context;
import android.graphics.Bitmap;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
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
public class CompactRawContactsEditorView extends LinearLayout implements View.OnClickListener {

    private static final String TAG = "CompactEditorView";

    /**
     * Callbacks for hosts of {@link CompactRawContactsEditorView}s.
     */
    public interface Listener {

        /**
         * Invoked when the compact editor should be expanded to show all fields.
         */
        public void onExpandEditor();
    }

    private Listener mListener;

    private AccountTypeManager mAccountTypeManager;
    private LayoutInflater mLayoutInflater;

    private ViewIdGenerator mViewIdGenerator;
    private MaterialColorMapUtils.MaterialPalette mMaterialPalette;

    private CompactPhotoEditorView mPhoto;
    private ViewGroup mNames;
    private ViewGroup mPhoneticNames;
    private ViewGroup mNicknames;
    private ViewGroup mPhoneNumbers;
    private ViewGroup mEmails;
    private ViewGroup mOther;
    private View mMoreFields;

    private long mPhotoRawContactId;

    public CompactRawContactsEditorView(Context context) {
        super(context);
    }

    public CompactRawContactsEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Sets the receiver for {@link CompactRawContactsEditorView} callbacks.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAccountTypeManager = AccountTypeManager.getInstance(getContext());
        mLayoutInflater = (LayoutInflater)
                getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mPhoto = (CompactPhotoEditorView) findViewById(R.id.photo_editor);
        mNames = (LinearLayout) findViewById(R.id.names);
        mPhoneticNames = (LinearLayout) findViewById(R.id.phonetic_names);
        mNicknames = (LinearLayout) findViewById(R.id.nicknames);
        mPhoneNumbers = (LinearLayout) findViewById(R.id.phone_numbers);
        mEmails = (LinearLayout) findViewById(R.id.emails);
        mOther = (LinearLayout) findViewById(R.id.other);
        mMoreFields = findViewById(R.id.more_fields);
        mMoreFields.setOnClickListener(this);
    }


    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.more_fields && mListener != null ) {
            mListener.onExpandEditor();
        }
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

    /**
     * Pass through to {@link CompactPhotoEditorView#setPhotoHandler}.
     */
    public void setPhotoHandler(PhotoHandler photoHandler) {
        mPhoto.setPhotoHandler(photoHandler);
    }

    /**
     * Pass through to {@link CompactPhotoEditorView#setPhoto}.
     */
    public void setPhoto(Bitmap bitmap) {
        mPhoto.setPhoto(bitmap);
    }

    /**
     * Pass through to {@link CompactPhotoEditorView#isWritablePhotoSet}.
     */
    public boolean isWritablePhotoSet() {
        return mPhoto.isWritablePhotoSet();
    }

    /**
     * Get the raw contact ID for the CompactHeaderView photo.
     */
    public long getPhotoRawContactId() {
        return mPhotoRawContactId;
    }

    public StructuredNameEditorView getStructuredNameEditorView() {
        // We only ever show one StructuredName
        return mNames.getChildCount() == 0
                ? null : (StructuredNameEditorView) mNames.getChildAt(0);
    }

    public View getAggregationAnchorView() {
        // Since there is only one structured name we can just return it as the anchor for
        // the aggregation suggestions popup
        if (mNames.getChildCount() == 0) {
            return null;
        }
        return mNames.getChildAt(0).findViewById(R.id.anchor_view);
    }

    public void setState(RawContactDeltaList rawContactDeltas,
            MaterialColorMapUtils.MaterialPalette materialPalette,
            ViewIdGenerator viewIdGenerator) {
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
        mMaterialPalette = materialPalette;

        addHeaderView(rawContactDeltas, viewIdGenerator);
        addStructuredNameView(rawContactDeltas);
        addEditorViews(rawContactDeltas);
        removeExtraEmptyTextFields(mPhoneNumbers);
        removeExtraEmptyTextFields(mEmails);
    }

    private void addHeaderView(RawContactDeltaList rawContactDeltas,
            ViewIdGenerator viewIdGenerator) {
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) {
                continue;
            }
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

            // Make sure we have a photo
            RawContactModifier.ensureKindExists(
                    rawContactDelta, accountType, Photo.CONTENT_ITEM_TYPE);

            final DataKind dataKind = accountType.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
            if (dataKind != null) {
                if (Photo.CONTENT_ITEM_TYPE.equals(dataKind.mimeType)) {
                    mPhotoRawContactId = rawContactDelta.getRawContactId();
                    final ValuesDelta valuesDelta = rawContactDelta.getSuperPrimaryEntry(
                            dataKind.mimeType, /* forceSelection =*/ true);
                    mPhoto.setValues(dataKind, valuesDelta, rawContactDelta,
                            /* readOnly =*/ !dataKind.editable, mMaterialPalette, viewIdGenerator);
                    return;
                }
            }
        }
    }

    private void addStructuredNameView(RawContactDeltaList rawContactDeltas) {
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) {
                continue;
            }
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

            // Make sure we have a structured name
            RawContactModifier.ensureKindExists(
                    rawContactDelta, accountType, StructuredName.CONTENT_ITEM_TYPE);

            final DataKind dataKind = accountType.getKindForMimetype(
                    StructuredName.CONTENT_ITEM_TYPE);
            if (dataKind != null) {
                final ValuesDelta valuesDelta = rawContactDelta.getPrimaryEntry(dataKind.mimeType);
                if (valuesDelta != null) {
                    mNames.addView(inflateStructuredNameEditorView(
                            mNames, accountType, valuesDelta, rawContactDelta));
                    return;
                }
            }
        }
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
                        || StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)
                        || GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Photos and name are handled separately; group membership is not supported
                    continue;
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
                    mPhoneNumbers.addView(inflateKindSectionView(
                            mPhoneNumbers, dataKind, rawContactDelta));
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    mEmails.addView(inflateKindSectionView(
                            mEmails, dataKind, rawContactDelta));
                } else if (hasNonEmptyValuesDelta(rawContactDelta, mimeType, dataKind)) {
                    mOther.addView(inflateKindSectionView(
                            mOther, dataKind, rawContactDelta));
                }
            }
        }
    }

    // TODO: avoid inflating extra views and deleting them
    private void removeExtraEmptyTextFields(ViewGroup viewGroup) {
        // If there is one (or less) editors, leave it whether it is empty or not
        if (viewGroup.getChildCount() <= 1) {
            return;
        }
        // Determine if there are any non-empty editors
        boolean hasAtLeastOneNonEmptyEditorView = false;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            if (!isEmptyEditorView(viewGroup.getChildAt(i))) {
                hasAtLeastOneNonEmptyEditorView = true;
                break;
            }
        }
        if (hasAtLeastOneNonEmptyEditorView) {
            // There is at least one non-empty editor, remove all the empty ones
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                if (isEmptyEditorView(viewGroup.getChildAt(i))) {
                    viewGroup.getChildAt(i).setVisibility(View.GONE);
                }
            }
        } else {
            // There is no non-empty editor, keep the first empty view and remove the rest
            for (int i = 1; i < viewGroup.getChildCount(); i++) {
                viewGroup.getChildAt(i).setVisibility(View.GONE);
            }
        }
    }

    private static boolean isEmptyEditorView(View view) {
        if (view instanceof TextFieldsEditorView) {
            final TextFieldsEditorView textFieldsEditorView = (TextFieldsEditorView) view;
            return textFieldsEditorView.isEmpty();
        }
        if (view instanceof KindSectionView) {
            final KindSectionView kindSectionView = (KindSectionView) view;
            return kindSectionView.hasEmptyEditor();
        }
        return false;
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
                /* showOneEmptyEditor =*/ false,
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
                /* showOneEmptyEditor =*/ false,
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
