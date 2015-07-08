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
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.editor.CompactContactEditorFragment.PhotoHandler;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        /**
         * Invoked when the structured name editor field has changed.
         *
         * @param rawContactId The raw contact ID from the underlying {@link RawContactDelta}.
         * @param valuesDelta The values from the underlying {@link RawContactDelta}.
         */
        public void onNameFieldChanged(long rawContactId, ValuesDelta valuesDelta);
    }

    /**
     * Marks a name as super primary when it is changed.
     *
     * This is for the case when two or more raw contacts with names are joined where neither is
     * marked as super primary.  If the user hits back (which causes a save) after changing the
     * name that was arbitrarily displayed, we want that to be the name that is used.
     *
     * Should only be set when a super primary name does not already exist since we only show
     * one name field.
     */
    static final class NameEditorListener implements Editor.EditorListener {

        private final ValuesDelta mValuesDelta;
        private final long mRawContactId;
        private final Listener mListener;

        public NameEditorListener(ValuesDelta valuesDelta, long rawContactId,
                Listener listener) {
            mValuesDelta = valuesDelta;
            mRawContactId = rawContactId;
            mListener = listener;
        }

        @Override
        public void onRequest(int request) {
            if (request == Editor.EditorListener.FIELD_CHANGED) {
                mValuesDelta.setSuperPrimary(true);
                if (mListener != null) {
                    mListener.onNameFieldChanged(mRawContactId, mValuesDelta);
                }
            } else if (request == Editor.EditorListener.FIELD_TURNED_EMPTY) {
                mValuesDelta.setSuperPrimary(false);
            }
        }

        @Override
        public void onDeleteRequested(Editor editor) {
        }
    }

    private Listener mListener;

    private AccountTypeManager mAccountTypeManager;
    private LayoutInflater mLayoutInflater;

    private ViewIdGenerator mViewIdGenerator;
    private MaterialColorMapUtils.MaterialPalette mMaterialPalette;

    private View mAccountContainer;
    private TextView mAccountTypeView;
    private TextView mAccountNameView;
    private CompactPhotoEditorView mPhoto;
    private ViewGroup mNames;
    private ViewGroup mPhoneticNames;
    private ViewGroup mNicknames;
    private ViewGroup mPhoneNumbers;
    private ViewGroup mEmails;
    private ViewGroup mOtherTypes;
    private Map<String,LinearLayout> mOtherTypesMap = new HashMap<>();
    private View mMoreFields;

    // The ValuesDelta for the non super primary name that was displayed to the user.
    private ValuesDelta mNameValuesDelta;

    private long mPhotoRawContactId;

    private StructuredNameEditorView mDefaultNameEditorView;

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

        mAccountContainer = findViewById(R.id.account_container);
        mAccountTypeView = (TextView) findViewById(R.id.account_type);
        mAccountNameView = (TextView) findViewById(R.id.account_name);
        mPhoto = (CompactPhotoEditorView) findViewById(R.id.photo_editor);
        mNames = (LinearLayout) findViewById(R.id.names);
        mPhoneticNames = (LinearLayout) findViewById(R.id.phonetic_names);
        mNicknames = (LinearLayout) findViewById(R.id.nicknames);
        mPhoneNumbers = (LinearLayout) findViewById(R.id.phone_numbers);
        mEmails = (LinearLayout) findViewById(R.id.emails);
        mOtherTypes = (LinearLayout) findViewById(R.id.other);
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
        for (Map.Entry<String,LinearLayout> otherType : mOtherTypesMap.entrySet()) {
            setEnabled(enabled, otherType.getValue());
        }
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
     * Pass through to {@link CompactPhotoEditorView#setFullSizedPhoto(Uri)}.
     */
    public void setFullSizePhoto(Uri photoUri) {
        mPhoto.setFullSizedPhoto(photoUri);
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

    public StructuredNameEditorView getDefaultNameEditorView() {
        return mDefaultNameEditorView;
    }

    public StructuredNameEditorView getStructuredNameEditorView() {
        // We only ever show one StructuredName
        return mNames.getChildCount() == 0
                ? null : (StructuredNameEditorView) mNames.getChildAt(0);
    }

    public PhoneticNameEditorView getFirstPhoneticNameEditorView() {
        // There should only ever be one phonetic name
        return mPhoneticNames.getChildCount() == 0
                ? null : (PhoneticNameEditorView) mPhoneticNames.getChildAt(0);
    }

    public View getAggregationAnchorView() {
        // Since there is only one structured name we can just return it as the anchor for
        // the aggregation suggestions popup
        if (mNames.getChildCount() == 0) {
            return null;
        }
        return mNames.getChildAt(0).findViewById(R.id.anchor_view);
    }

    /**
     * @param readOnlyDisplayName The display name to set on the new raw contact created in order
     *         to edit a read-only contact.
     */
    public void setState(RawContactDeltaList rawContactDeltas,
            MaterialColorMapUtils.MaterialPalette materialPalette, ViewIdGenerator viewIdGenerator,
            long photoId, long nameId, String readOnlyDisplayName, boolean hasNewContact,
            boolean isUserProfile) {
        mNames.removeAllViews();
        mPhoneticNames.removeAllViews();
        mNicknames.removeAllViews();
        mPhoneNumbers.removeAllViews();
        mEmails.removeAllViews();
        mOtherTypes.removeAllViews();
        mOtherTypesMap.clear();

        if (rawContactDeltas == null || rawContactDeltas.isEmpty()) {
            return;
        }

        mViewIdGenerator = viewIdGenerator;
        setId(mViewIdGenerator.getId(rawContactDeltas.get(0), /* dataKind =*/ null,
                /* valuesDelta =*/ null, ViewIdGenerator.NO_VIEW_INDEX));
        mMaterialPalette = materialPalette;

        vlog("Setting compact editor state from " + rawContactDeltas);
        addAccountInfo(rawContactDeltas, hasNewContact, readOnlyDisplayName, isUserProfile);
        addPhotoView(rawContactDeltas, viewIdGenerator, photoId, readOnlyDisplayName);
        addStructuredNameView(rawContactDeltas, nameId, readOnlyDisplayName);
        addEditorViews(rawContactDeltas);
        updateKindEditorEmptyFields(mPhoneNumbers);
        updateKindEditorIcons(mPhoneNumbers);
        updateKindEditorEmptyFields(mEmails);
        updateKindEditorIcons(mEmails);
        for (Map.Entry<String,LinearLayout> otherTypes : mOtherTypesMap.entrySet()) {
            updateKindEditorIcons(otherTypes.getValue());
        }
    }

    private void addAccountInfo(RawContactDeltaList rawContactDeltas, boolean hasNewContact,
            String readOnlyDisplayName, boolean isUserProfile) {
        // Only show account info for inserts and first time edits of read-only accounts
        if (!hasNewContact && TextUtils.isEmpty(readOnlyDisplayName)) {
            vlog("Account info hidden");
            mAccountContainer.setVisibility(View.GONE);
            return;
        }

        // Get the default account
        final AccountWithDataSet defaultAccountWithDataSet =
                ContactEditorUtils.getInstance(getContext()).getDefaultAccount();
        if (defaultAccountWithDataSet == null) {
            vlog("Account info hidden because default account not set");
            mAccountContainer.setVisibility(View.GONE);
            return;
        }

        // We can assume the first writable raw contact is the newly created one because
        // inserts have a delta list of size 1 and read-only contacts are should be of size 2
        RawContactDelta defaultAccountRawContactDelta = null;
        AccountType accountType = null;
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            accountType = rawContactDelta.getAccountType(mAccountTypeManager);
            if (accountType != null && accountType.areContactsWritable()) {
                defaultAccountRawContactDelta = rawContactDelta;
                break;
            }
        }
        if (defaultAccountRawContactDelta == null) {
            vlog("Account info hidden because no raw contact delta matched");
            mAccountContainer.setVisibility(View.GONE);
            return;
        }
        if (accountType == null) {
            vlog("Account info hidden because no account type returned from raw contact delta");
            mAccountContainer.setVisibility(View.GONE);
            return;
        }

        // Get the account information for the default account RawContactDelta
        final Pair<String,String> accountInfo = EditorUiUtils.getAccountInfo(getContext(),
                isUserProfile, defaultAccountRawContactDelta.getAccountName(), accountType);

        // Set the account information already
        if (accountInfo == null) {
            vlog("Account info hidden because no account info could be composed");
            mAccountContainer.setVisibility(View.GONE);
            return;
        }
        vlog("Account info loaded");
        if (accountInfo.first == null) {
            mAccountNameView.setVisibility(View.GONE);
        } else {
            mAccountNameView.setVisibility(View.VISIBLE);
            mAccountNameView.setText(accountInfo.first);
        }
        mAccountTypeView.setText(accountInfo.second);

        mAccountContainer.setContentDescription(EditorUiUtils.getAccountInfoContentDescription(
                accountInfo.first, accountInfo.second));
    }

    private void addPhotoView(RawContactDeltaList rawContactDeltas,
            ViewIdGenerator viewIdGenerator, long photoId, String readOnlyDisplayName) {
        // If we're editing a read-only contact, the display name from the read-only
        // contact is non empty and we can use it determine whether to back the photo editor with
        // the empty new raw contact delta.  See go/editing-read-only-contacts
        final boolean readOnlyContact = !TextUtils.isEmpty(readOnlyDisplayName);
        if (readOnlyContact) {
            for (RawContactDelta rawContactDelta : rawContactDeltas) {
                if (!rawContactDelta.isVisible()) continue;
                final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

                // Make sure we have a photo
                RawContactModifier.ensureKindExists(
                        rawContactDelta, accountType, Photo.CONTENT_ITEM_TYPE);

                final DataKind dataKind = accountType.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
                if (accountType.areContactsWritable()) {
                    for (ValuesDelta valuesDelta : rawContactDelta.getMimeEntries(
                            Photo.CONTENT_ITEM_TYPE)) {
                        if (valuesDelta != null) {
                            // Break the loop but don't return because we need to keep going to
                            // in order to show the photo from the read-only contact.
                            mPhotoRawContactId = rawContactDelta.getRawContactId();
                            mPhoto.setValues(dataKind, valuesDelta, rawContactDelta,
                                    /* readOnly =*/ false, mMaterialPalette, viewIdGenerator);
                            break;
                        }
                    }
                }
            }
        }

        // Look for a match for the photo ID that was passed in
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

            // Make sure we have a photo
            RawContactModifier.ensureKindExists(
                    rawContactDelta, accountType, Photo.CONTENT_ITEM_TYPE);

            final DataKind dataKind = accountType.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
            if (dataKind != null && dataKind.editable) {
                for (ValuesDelta valuesDelta
                        : rawContactDelta.getMimeEntries(Photo.CONTENT_ITEM_TYPE)) {
                    if (valuesDelta != null && valuesDelta.getId() != null
                            && valuesDelta.getId().equals(photoId)) {
                        if (readOnlyContact) {
                            mPhoto.setPhoto(valuesDelta);
                        } else {
                            mPhotoRawContactId = rawContactDelta.getRawContactId();
                            mPhoto.setValues(dataKind, valuesDelta, rawContactDelta,
                                    !accountType.areContactsWritable(),
                                    mMaterialPalette, viewIdGenerator);
                        }
                        return;
                    }
                }
            }
        }

        // Look for a non-empty super primary photo
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
            final DataKind dataKind = accountType.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
            if (dataKind != null && dataKind.editable) {
                final ValuesDelta valuesDelta = getNonEmptySuperPrimaryValuesDeltas(
                        rawContactDelta, Photo.CONTENT_ITEM_TYPE, dataKind);
                if (valuesDelta != null) {
                    if (readOnlyContact) {
                        mPhoto.setPhoto(valuesDelta);
                    } else {
                        mPhotoRawContactId = rawContactDelta.getRawContactId();
                        mPhoto.setValues(dataKind, valuesDelta, rawContactDelta,
                                !accountType.areContactsWritable(), mMaterialPalette,
                                viewIdGenerator);
                    }
                    return;
                }
            }
        }
        // We didn't find a non-empty super primary photo, use the first non-empty one
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
            final DataKind dataKind = accountType.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
            if (dataKind != null && dataKind.editable) {
                final List<ValuesDelta> valuesDeltas = getNonEmptyValuesDeltas(
                        rawContactDelta, Photo.CONTENT_ITEM_TYPE, dataKind);
                if (valuesDeltas != null && !valuesDeltas.isEmpty()) {
                    if (readOnlyContact) {
                        mPhoto.setPhoto(valuesDeltas.get(0));
                    } else {
                        mPhotoRawContactId = rawContactDelta.getRawContactId();
                        mPhoto.setValues(dataKind, valuesDeltas.get(0), rawContactDelta,
                                !accountType.areContactsWritable(), mMaterialPalette,
                                viewIdGenerator);
                    }
                    return;
                }
            }
        }
        // No suitable non-empty photo
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
            final DataKind dataKind = accountType.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
            if (dataKind != null && dataKind.editable) {
                final ValuesDelta valuesDelta = rawContactDelta.getSuperPrimaryEntry(
                        dataKind.mimeType, /* forceSelection =*/ true);
                if (valuesDelta != null) {
                    if (readOnlyContact) {
                        mPhoto.setPhoto(valuesDelta);
                    } else {
                        mPhotoRawContactId = rawContactDelta.getRawContactId();
                        mPhoto.setValues(dataKind, valuesDelta, rawContactDelta,
                                !accountType.areContactsWritable(), mMaterialPalette,
                                viewIdGenerator);
                    }
                    return;
                }
            }
        }
        // Should not happen since we ensure the kind exists but if we unexpectedly get here
        // we must remove the photo section so that it does not take up the entire view
        mPhoto.setVisibility(View.GONE);
    }

    private void addStructuredNameView(RawContactDeltaList rawContactDeltas, long nameId,
            String readOnlyDisplayName) {
        // If we're editing a read-only contact we want to display the name from the read-only
        // contact in a structured name editor backed by the new raw contact that was created.
        // The new raw contact is writable and merging it with the read-only contact allows us
        // to edit the read-only contact. See go/editing-read-only-contacts
        if (!TextUtils.isEmpty(readOnlyDisplayName)) {
            for (RawContactDelta rawContactDelta : rawContactDeltas) {
                if (!rawContactDelta.isVisible()) continue;
                final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

                // Make sure we have a structured name
                RawContactModifier.ensureKindExists(
                        rawContactDelta, accountType, StructuredName.CONTENT_ITEM_TYPE);

                if (accountType.areContactsWritable()) {
                    for (ValuesDelta valuesDelta : rawContactDelta.getMimeEntries(
                            StructuredName.CONTENT_ITEM_TYPE)) {
                        if (valuesDelta != null) {
                            mNameValuesDelta = valuesDelta;
                            final NameEditorListener nameEditorListener = new NameEditorListener(
                                    mNameValuesDelta, rawContactDelta.getRawContactId(), mListener);
                            final StructuredNameEditorView nameEditorView =
                                    inflateStructuredNameEditorView(mNames, accountType,
                                            mNameValuesDelta, rawContactDelta, nameEditorListener,
                                            !accountType.areContactsWritable());
                            nameEditorView.setDisplayName(readOnlyDisplayName);
                            mNames.addView(nameEditorView);
                            mDefaultNameEditorView = nameEditorView;
                            return;
                        }
                    }
                }
            }
        }

        // Look for a match for the name ID that was passed in
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

            // Make sure we have a structured name
            RawContactModifier.ensureKindExists(
                    rawContactDelta, accountType, StructuredName.CONTENT_ITEM_TYPE);

            // Note use of pseudo mime type to get the DataKind and StructuredName to get value
            final DataKind dataKind = accountType.getKindForMimetype(
                    DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME);
            if (dataKind == null || !dataKind.editable) continue;

            for (ValuesDelta valuesDelta : rawContactDelta.getMimeEntries(
                    StructuredName.CONTENT_ITEM_TYPE)) {
                if (valuesDelta != null && valuesDelta.getId() != null
                        && valuesDelta.getId().equals(nameId)) {
                    mNameValuesDelta = valuesDelta;
                    final NameEditorListener nameEditorListener = new NameEditorListener(
                            mNameValuesDelta, rawContactDelta.getRawContactId(), mListener);
                    mNames.addView(inflateStructuredNameEditorView(mNames, accountType,
                            mNameValuesDelta, rawContactDelta, nameEditorListener,
                            !accountType.areContactsWritable()));
                    return;
                }
            }
        }
        // Look for a super primary name
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

            final DataKind dataKind = accountType.getKindForMimetype(
                    DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME);
            if (dataKind == null || !dataKind.editable) continue;

            final ValuesDelta superPrimaryValuesDelta = getNonEmptySuperPrimaryValuesDeltas(
                    rawContactDelta, StructuredName.CONTENT_ITEM_TYPE, dataKind);
            if (superPrimaryValuesDelta != null) {
                // Our first preference is for a non-empty super primary name
                final NameEditorListener nameEditorListener = new NameEditorListener(
                        superPrimaryValuesDelta, rawContactDelta.getRawContactId(), mListener);
                mNames.addView(inflateStructuredNameEditorView(mNames, accountType,
                        superPrimaryValuesDelta, rawContactDelta, nameEditorListener,
                        !accountType.areContactsWritable()));
                return;
            }
        }
        // We didn't find a super primary name
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

            final DataKind dataKind = accountType.getKindForMimetype(
                    DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME);
            if (dataKind == null || !dataKind.editable) continue;

            final List<ValuesDelta> nonEmptyValuesDeltas = getNonEmptyValuesDeltas(
                    rawContactDelta, StructuredName.CONTENT_ITEM_TYPE, dataKind);
            if (nonEmptyValuesDeltas != null && !nonEmptyValuesDeltas.isEmpty()) {
                // Take the first non-empty name
                mNameValuesDelta = nonEmptyValuesDeltas.get(0);
                final NameEditorListener nameEditorListener = new NameEditorListener(
                        mNameValuesDelta, rawContactDelta.getRawContactId(), mListener);
                mNames.addView(inflateStructuredNameEditorView(mNames, accountType,
                        mNameValuesDelta, rawContactDelta, nameEditorListener,
                        !accountType.areContactsWritable()));
                return;
            }
        }
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

            final DataKind dataKind = accountType.getKindForMimetype(
                    DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME);
            if (dataKind == null || !dataKind.editable) continue;

            // Fall back to the first entry
            final ArrayList<ValuesDelta> valuesDeltas = rawContactDelta.getMimeEntries(
                    StructuredName.CONTENT_ITEM_TYPE);
            if (valuesDeltas != null && !valuesDeltas.isEmpty()) {
                mNameValuesDelta = valuesDeltas.get(0);
                final NameEditorListener nameEditorListener = new NameEditorListener(
                        mNameValuesDelta, rawContactDelta.getRawContactId(), mListener);
                mNames.addView(inflateStructuredNameEditorView(mNames, accountType,
                        mNameValuesDelta, rawContactDelta, nameEditorListener,
                        !accountType.areContactsWritable()));
                return;
            }
        }
    }

    private void addEditorViews(RawContactDeltaList rawContactDeltas) {
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (!rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);

            for (DataKind dataKind : accountType.getSortedDataKinds()) {
                if (!dataKind.editable) continue;

                final String mimeType = dataKind.mimeType;
                vlog(mimeType + " " + dataKind.fieldList.size() + " field(s)");
                if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)
                        || StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)
                        || GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Photos and structured names are handled separately and
                    // group membership is not supported
                    continue;
                } else if (DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(mimeType)) {
                    // Only add phonetic names if there is a non-empty one. Note the use of
                    // StructuredName mimeType below, even though we matched a pseudo mime type.
                    final ValuesDelta valuesDelta = rawContactDelta.getSuperPrimaryEntry(
                            StructuredName.CONTENT_ITEM_TYPE, /* forceSelection =*/ true);
                    if (hasNonEmptyValue(dataKind, valuesDelta)) {
                        mPhoneticNames.addView(inflatePhoneticNameEditorView(
                                mPhoneticNames, accountType, valuesDelta, rawContactDelta));
                    }
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Add all non-empty nicknames
                    final List<ValuesDelta> valuesDeltas = getNonEmptyValuesDeltas(
                            rawContactDelta, Nickname.CONTENT_ITEM_TYPE, dataKind);
                    if (valuesDeltas != null && !valuesDeltas.isEmpty()) {
                        for (ValuesDelta valuesDelta : valuesDeltas) {
                            mNicknames.addView(inflateNicknameEditorView(
                                    mNicknames, dataKind, valuesDelta, rawContactDelta));
                        }
                    }
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final KindSectionView kindSectionView =
                            inflateKindSectionView(mPhoneNumbers, dataKind, rawContactDelta);
                    kindSectionView.setListener(new KindSectionView.Listener() {
                        @Override
                        public void onDeleteRequested(Editor editor) {
                            if (kindSectionView.getEditorCount() == 1) {
                                kindSectionView.markForRemoval();
                                EditorAnimator.getInstance().removeEditorView(kindSectionView);
                            } else {
                                editor.deleteEditor();
                            }
                            updateKindEditorEmptyFields(mPhoneNumbers);
                            updateKindEditorIcons(mPhoneNumbers);
                        }
                    });
                    mPhoneNumbers.addView(kindSectionView);
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final KindSectionView kindSectionView =
                            inflateKindSectionView(mEmails, dataKind, rawContactDelta);
                    kindSectionView.setListener(new KindSectionView.Listener() {
                        @Override
                        public void onDeleteRequested(Editor editor) {
                            if (kindSectionView.getEditorCount() == 1) {
                                kindSectionView.markForRemoval();
                                EditorAnimator.getInstance().removeEditorView(kindSectionView);
                            } else {
                                editor.deleteEditor();
                            }
                            updateKindEditorEmptyFields(mEmails);
                            updateKindEditorIcons(mEmails);
                        }
                    });
                    mEmails.addView(kindSectionView);
                } else if (hasNonEmptyValuesDelta(rawContactDelta, mimeType, dataKind)) {
                    final LinearLayout otherTypeViewGroup;
                    if (mOtherTypesMap.containsKey(mimeType)) {
                        otherTypeViewGroup = mOtherTypesMap.get(mimeType);
                    } else {
                        otherTypeViewGroup = new LinearLayout(getContext());
                        otherTypeViewGroup.setOrientation(LinearLayout.VERTICAL);
                        mOtherTypes.addView(otherTypeViewGroup);
                        mOtherTypesMap.put(mimeType, otherTypeViewGroup);
                    }
                    final KindSectionView kindSectionView =
                            inflateKindSectionView(mOtherTypes, dataKind, rawContactDelta);
                    kindSectionView.setListener(new KindSectionView.Listener() {
                        @Override
                        public void onDeleteRequested(Editor editor) {
                            if (kindSectionView.getEditorCount() == 1) {
                                kindSectionView.markForRemoval();
                                EditorAnimator.getInstance().removeEditorView(kindSectionView);
                            } else {
                                editor.deleteEditor();
                            }
                            updateKindEditorIcons(otherTypeViewGroup);
                        }
                    });
                    otherTypeViewGroup.addView(kindSectionView);
                }
            }
        }
    }

    private static void updateKindEditorEmptyFields(ViewGroup viewGroup) {
        KindSectionView lastVisibleKindSectionView = null;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            if (viewGroup.getChildAt(i).getVisibility() == View.VISIBLE) {
                lastVisibleKindSectionView = (KindSectionView) viewGroup.getChildAt(i);
            }
        }
        // Only the last editor should show an empty editor
        if (lastVisibleKindSectionView != null) {
            // Hide all empty kind sections except the last one
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                final KindSectionView kindSectionView = (KindSectionView) viewGroup.getChildAt(i);
                if (kindSectionView != lastVisibleKindSectionView
                        && kindSectionView.areAllEditorsEmpty()) {
                    kindSectionView.setVisibility(View.GONE);
                }
            }
            // Set the last editor to show empty editor fields
            lastVisibleKindSectionView.setShowOneEmptyEditor(true);
            lastVisibleKindSectionView.updateEmptyEditors(/* shouldAnimate =*/ false);
        }
    }

    private static void updateKindEditorIcons(ViewGroup viewGroup) {
        // Show the icon on the first visible kind editor
        boolean iconVisible = false;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            final KindSectionView kindSectionView = (KindSectionView) viewGroup.getChildAt(i);
            if (kindSectionView.getVisibility() != View.VISIBLE
                    || kindSectionView.isMarkedForRemoval()) {
                continue;
            }
            if (!iconVisible) {
                kindSectionView.setIconVisibility(true);
                iconVisible = true;
            } else {
                kindSectionView.setIconVisibility(false);
            }
        }
    }

    private static boolean hasNonEmptyValuesDelta(RawContactDelta rawContactDelta,
            String mimeType, DataKind dataKind) {
        return !getNonEmptyValuesDeltas(rawContactDelta, mimeType, dataKind).isEmpty();
    }

    private static ValuesDelta getNonEmptySuperPrimaryValuesDeltas(RawContactDelta rawContactDelta,
            String mimeType, DataKind dataKind) {
        for (ValuesDelta valuesDelta : getNonEmptyValuesDeltas(
                rawContactDelta, mimeType, dataKind)) {
            if (valuesDelta.isSuperPrimary()) {
                return valuesDelta;
            }
        }
        return null;
    }

    static List<ValuesDelta> getNonEmptyValuesDeltas(RawContactDelta rawContactDelta,
            String mimeType, DataKind dataKind) {
        final List<ValuesDelta> result = new ArrayList<>();
        if (rawContactDelta == null) {
            vlog("Null RawContactDelta");
            return result;
        }
        if (!rawContactDelta.hasMimeEntries(mimeType)) {
            vlog("No ValueDeltas");
            return result;
        }
        for (ValuesDelta valuesDelta : rawContactDelta.getMimeEntries(mimeType)) {
            if (hasNonEmptyValue(dataKind, valuesDelta)) {
                result.add(valuesDelta);
            }
        }
        return result;
    }

    private static boolean hasNonEmptyValue(DataKind dataKind, ValuesDelta valuesDelta) {
        if (valuesDelta == null) {
            vlog("Null valuesDelta");
            return false;
        }
        for (EditField editField : dataKind.fieldList) {
            final String column = editField.column;
            final String value = valuesDelta == null ? null : valuesDelta.getAsString(column);
            vlog("Field " + column + " empty=" + TextUtils.isEmpty(value) + " value=" + value);
            if (!TextUtils.isEmpty(value)) {
                return true;
            }
        }
        return false;
    }

    private StructuredNameEditorView inflateStructuredNameEditorView(ViewGroup viewGroup,
            AccountType accountType, ValuesDelta valuesDelta, RawContactDelta rawContactDelta,
            NameEditorListener nameEditorListener, boolean readOnly) {
        final StructuredNameEditorView result = (StructuredNameEditorView) mLayoutInflater.inflate(
                R.layout.structured_name_editor_view, viewGroup, /* attachToRoot =*/ false);
        if (nameEditorListener != null) {
            result.setEditorListener(nameEditorListener);
        }
        result.setDeletable(false);
        result.setValues(
                accountType.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME),
                valuesDelta,
                rawContactDelta,
                readOnly,
                mViewIdGenerator);
        return result;
    }

    private PhoneticNameEditorView inflatePhoneticNameEditorView(ViewGroup viewGroup,
            AccountType accountType, ValuesDelta valuesDelta, RawContactDelta rawContactDelta) {
        final PhoneticNameEditorView result = (PhoneticNameEditorView) mLayoutInflater.inflate(
                R.layout.phonetic_name_editor_view, viewGroup, /* attachToRoot =*/ false);
        result.setDeletable(false);
        result.setValues(
                accountType.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME),
                valuesDelta,
                rawContactDelta,
                /* readOnly =*/ false,
                mViewIdGenerator);
        return result;
    }

    private TextFieldsEditorView inflateNicknameEditorView(ViewGroup viewGroup, DataKind dataKind,
            ValuesDelta valuesDelta, RawContactDelta rawContactDelta) {
        final TextFieldsEditorView result = (TextFieldsEditorView) mLayoutInflater.inflate(
                R.layout.nick_name_editor_view, viewGroup, /* attachToRoot =*/ false);
        result.setDeletable(false);
        result.setValues(
                dataKind,
                valuesDelta,
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

    private static void vlog(String message) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, message);
        }
    }
}
