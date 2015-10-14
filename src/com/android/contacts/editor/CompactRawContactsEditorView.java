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
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.util.AccountsListAdapter;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.util.UiClosables;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * View to display information from multiple {@link RawContactDelta}s grouped together.
 */
public class CompactRawContactsEditorView extends LinearLayout implements View.OnClickListener {

    static final String TAG = "CompactEditorView";

    private static final KindSectionDataMapEntryComparator
            KIND_SECTION_DATA_MAP_ENTRY_COMPARATOR = new KindSectionDataMapEntryComparator();

    /**
     * Callbacks for hosts of {@link CompactRawContactsEditorView}s.
     */
    public interface Listener {

        /**
         * Invoked when the structured name editor field has changed.
         *
         * @param rawContactId The raw contact ID from the underlying {@link RawContactDelta}.
         * @param valuesDelta The values from the underlying {@link RawContactDelta}.
         */
        public void onNameFieldChanged(long rawContactId, ValuesDelta valuesDelta);

        /**
         * Invoked when the compact editor should rebind editors for a new account.
         *
         * @param oldState Old data being edited.
         * @param oldAccount Old account associated with oldState.
         * @param newAccount New account to be used.
         */
        public void onRebindEditorsForNewContact(RawContactDelta oldState,
                AccountWithDataSet oldAccount, AccountWithDataSet newAccount);

        /**
         * Invoked when no editors could be bound for the contact.
         */
        public void onBindEditorsFailed();

        /**
         * Invoked after editors have been bound for the contact.
         */
        public void onEditorsBound();

        /**
         * Invoked when a rawcontact from merged contacts is selected in editor.
         */
        public void onRawContactSelected(Uri uri, long rawContactId, boolean isReadOnly);

        /**
         * Returns the map of raw contact IDs to newly taken or selected photos that have not
         * yet been saved to CP2.
         */
        public Bundle getUpdatedPhotos();
    }

    /**
     * Used to list the account info for the given raw contacts list.
     */
    private static final class RawContactAccountListAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final Context mContext;
        private final RawContactDeltaList mRawContactDeltas;

        public RawContactAccountListAdapter(Context context, RawContactDeltaList rawContactDeltas) {
            mContext = context;
            mRawContactDeltas = new RawContactDeltaList();
            for (RawContactDelta rawContactDelta : rawContactDeltas) {
                if (rawContactDelta.isVisible()) {
                    mRawContactDeltas.add(rawContactDelta);
                }
            }
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View resultView = convertView != null ? convertView
                    : mInflater.inflate(R.layout.account_selector_list_item, parent, false);

            final RawContactDelta rawContactDelta = mRawContactDeltas.get(position);
            final String accountName = rawContactDelta.getAccountName();
            final AccountType accountType = rawContactDelta.getRawContactAccountType(mContext);

            final TextView text1 = (TextView) resultView.findViewById(android.R.id.text1);
            text1.setText(accountType.getDisplayLabel(mContext));

            // For email addresses, we don't want to truncate at end, which might cut off the domain
            // name.
            final TextView text2 = (TextView) resultView.findViewById(android.R.id.text2);
            if (TextUtils.isEmpty(accountName)) {
                text2.setVisibility(View.GONE);
            } else {
                text2.setText(accountName);
                text2.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            }

            final ImageView icon = (ImageView) resultView.findViewById(android.R.id.icon);
            icon.setImageDrawable(accountType.getDisplayIcon(mContext));

            return resultView;
        }

        @Override
        public int getCount() {
            return mRawContactDeltas.size();
        }

        @Override
        public RawContactDelta getItem(int position) {
            return mRawContactDeltas.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).getRawContactId();
        }
    }

    /** Used to sort entire kind sections. */
    private static final class KindSectionDataMapEntryComparator implements
            Comparator<Map.Entry<String,KindSectionDataList>> {

        final MimeTypeComparator mMimeTypeComparator = new MimeTypeComparator();

        @Override
        public int compare(Map.Entry<String, KindSectionDataList> entry1,
                Map.Entry<String, KindSectionDataList> entry2) {
            if (entry1 == entry2) return 0;
            if (entry1 == null) return -1;
            if (entry2 == null) return 1;

            final String mimeType1 = entry1.getKey();
            final String mimeType2 = entry2.getKey();

            return mMimeTypeComparator.compare(mimeType1, mimeType2);
        }
    }

    /**
     * Sorts kinds roughly the same as quick contacts; we diverge in the following ways:
     * <ol>
     *     <li>All names are together at the top.</li>
     *     <li>IM is moved up after addresses</li>
     *     <li>SIP addresses are moved to below phone numbers</li>
     *     <li>Group membership is placed at the end</li>
     * </ol>
     */
    private static final class MimeTypeComparator implements Comparator<String> {

        private static final List<String> MIME_TYPE_ORDER = Arrays.asList(new String[] {
                StructuredName.CONTENT_ITEM_TYPE,
                Nickname.CONTENT_ITEM_TYPE,
                Organization.CONTENT_ITEM_TYPE,
                Phone.CONTENT_ITEM_TYPE,
                SipAddress.CONTENT_ITEM_TYPE,
                Email.CONTENT_ITEM_TYPE,
                StructuredPostal.CONTENT_ITEM_TYPE,
                Im.CONTENT_ITEM_TYPE,
                Website.CONTENT_ITEM_TYPE,
                Event.CONTENT_ITEM_TYPE,
                Relation.CONTENT_ITEM_TYPE,
                Note.CONTENT_ITEM_TYPE,
                GroupMembership.CONTENT_ITEM_TYPE
        });

        @Override
        public int compare(String mimeType1, String mimeType2) {
            if (mimeType1 == mimeType2) return 0;
            if (mimeType1 == null) return -1;
            if (mimeType2 == null) return 1;

            int index1 = MIME_TYPE_ORDER.indexOf(mimeType1);
            int index2 = MIME_TYPE_ORDER.indexOf(mimeType2);

            // Fallback to alphabetical ordering of the mime type if both are not found
            if (index1 < 0 && index2 < 0) return mimeType1.compareTo(mimeType2);
            if (index1 < 0) return 1;
            if (index2 < 0) return -1;

            return index1 < index2 ? -1 : 1;
        }
    }

    /**
     * Sorts primary accounts and google account types before others.
     */
    private static final class EditorComparator implements Comparator<KindSectionData> {

        private RawContactDeltaComparator mRawContactDeltaComparator;

        private EditorComparator(Context context) {
            mRawContactDeltaComparator = new RawContactDeltaComparator(context);
        }

        @Override
        public int compare(KindSectionData kindSectionData1, KindSectionData kindSectionData2) {
            if (kindSectionData1 == kindSectionData2) return 0;
            if (kindSectionData1 == null) return -1;
            if (kindSectionData2 == null) return 1;

            final RawContactDelta rawContactDelta1 = kindSectionData1.getRawContactDelta();
            final RawContactDelta rawContactDelta2 = kindSectionData2.getRawContactDelta();

            if (rawContactDelta1 == rawContactDelta2) return 0;
            if (rawContactDelta1 == null) return -1;
            if (rawContactDelta2 == null) return 1;

            return mRawContactDeltaComparator.compare(rawContactDelta1, rawContactDelta2);
        }
    }

    /**
     * Sorts primary account names first, followed by google account types, and other account
     * types last.  For names from the same account we order structured names before nicknames,
     * but still keep names from the same account together.
     */
    private static final class NameEditorComparator implements Comparator<KindSectionData> {

        private final RawContactDeltaComparator mRawContactDeltaComparator;
        private final MimeTypeComparator mMimeTypeComparator;
        private final RawContactDelta mPrimaryRawContactDelta;

        private NameEditorComparator(Context context, RawContactDelta primaryRawContactDelta) {
            mRawContactDeltaComparator = new RawContactDeltaComparator(context);
            mMimeTypeComparator = new MimeTypeComparator();
            mPrimaryRawContactDelta = primaryRawContactDelta;
        }

        @Override
        public int compare(KindSectionData kindSectionData1, KindSectionData kindSectionData2) {
            if (kindSectionData1 == kindSectionData2) return 0;
            if (kindSectionData1 == null) return -1;
            if (kindSectionData2 == null) return 1;

            final RawContactDelta rawContactDelta1 = kindSectionData1.getRawContactDelta();
            final RawContactDelta rawContactDelta2 = kindSectionData2.getRawContactDelta();

            if (rawContactDelta1 == rawContactDelta2) return 0;
            if (rawContactDelta1 == null) return -1;
            if (rawContactDelta2 == null) return 1;

            final boolean isRawContactDelta1Primary =
                mPrimaryRawContactDelta.equals(rawContactDelta1);
            final boolean isRawContactDelta2Primary =
                mPrimaryRawContactDelta.equals(rawContactDelta2);

            // If both names are from the primary account, sort my by mime type
            if (isRawContactDelta1Primary && isRawContactDelta2Primary) {
                final String mimeType1 = kindSectionData1.getDataKind().mimeType;
                final String mimeType2 = kindSectionData2.getDataKind().mimeType;
                return mMimeTypeComparator.compare(mimeType1, mimeType2);
            }

            // The primary account name should be before all others
            if (isRawContactDelta1Primary) return -1;
            if (isRawContactDelta2Primary) return 1;

           return mRawContactDeltaComparator.compare(rawContactDelta1, rawContactDelta2);
        }
    }

    public static class SavedState extends BaseSavedState {

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

        private boolean mIsExpanded;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mIsExpanded = in.readInt() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mIsExpanded ? 1 : 0);
        }
    }

    private CompactRawContactsEditorView.Listener mListener;

    private AccountTypeManager mAccountTypeManager;
    private LayoutInflater mLayoutInflater;

    private ViewIdGenerator mViewIdGenerator;
    private MaterialColorMapUtils.MaterialPalette mMaterialPalette;
    private long mPhotoId;
    private String mReadOnlyDisplayName;
    private boolean mHasNewContact;
    private boolean mIsUserProfile;
    private AccountWithDataSet mPrimaryAccount;
    private RawContactDelta mPrimaryRawContactDelta;
    private Map<String,KindSectionDataList> mKindSectionDataMap = new HashMap<>();

    // Account header
    private View mAccountHeaderContainer;
    private TextView mAccountHeaderType;
    private TextView mAccountHeaderName;
    private ImageView mAccountHeaderIcon;

    // Account selector
    private View mAccountSelectorContainer;
    private View mAccountSelector;
    private TextView mAccountSelectorType;
    private TextView mAccountSelectorName;

    // Raw contacts selector
    private View mRawContactContainer;
    private TextView mRawContactSummary;
    private ImageView mPrimaryAccountIcon;

    private CompactPhotoEditorView mPhotoView;
    private ViewGroup mKindSectionViews;
    private Map<String,List<CompactKindSectionView>> mKindSectionViewsMap = new HashMap<>();
    private View mMoreFields;

    private boolean mIsExpanded;

    private long mPhotoRawContactId;
    private ValuesDelta mPhotoValuesDelta;
    private StructuredNameEditorView mPrimaryNameEditorView;

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

        // Account header
        mAccountHeaderContainer = findViewById(R.id.account_container);
        mAccountHeaderType = (TextView) findViewById(R.id.account_type);
        mAccountHeaderName = (TextView) findViewById(R.id.account_name);
        mAccountHeaderIcon = (ImageView) findViewById(R.id.account_type_icon);

        // Account selector
        mAccountSelectorContainer = findViewById(R.id.account_selector_container);
        mAccountSelector = findViewById(R.id.account);
        mAccountSelectorType = (TextView) findViewById(R.id.account_type_selector);
        mAccountSelectorName = (TextView) findViewById(R.id.account_name_selector);

        // Raw contacts selector
        mRawContactContainer = findViewById(R.id.all_rawcontacts_accounts_container);
        mRawContactSummary = (TextView) findViewById(R.id.rawcontacts_accounts_summary);
        mPrimaryAccountIcon = (ImageView) findViewById(R.id.primary_account_icon);

        mPhotoView = (CompactPhotoEditorView) findViewById(R.id.photo_editor);
        mKindSectionViews = (LinearLayout) findViewById(R.id.kind_section_views);
        mMoreFields = findViewById(R.id.more_fields);
        mMoreFields.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.more_fields) {
            showAllFields();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        final int childCount = mKindSectionViews.getChildCount();
        for (int i = 0; i < childCount; i++) {
            mKindSectionViews.getChildAt(i).setEnabled(enabled);
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState savedState = new SavedState(superState);
        savedState.mIsExpanded = mIsExpanded;
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if(!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        final SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        mIsExpanded = savedState.mIsExpanded;
        if (mIsExpanded) {
            showAllFields();
        }
    }

    /**
     * Pass through to {@link CompactPhotoEditorView#setListener}.
     */
    public void setPhotoListener(CompactPhotoEditorView.Listener listener) {
        mPhotoView.setListener(listener);
    }

    public void removePhoto() {
        mPhotoValuesDelta.setFromTemplate(false);
        mPhotoValuesDelta.put(Photo.PHOTO, (byte[]) null);

        mPhotoView.removePhoto();
    }

    /**
     * Pass through to {@link CompactPhotoEditorView#setFullSizedPhoto(Uri)}.
     */
    public void setFullSizePhoto(Uri photoUri) {
        mPhotoView.setFullSizedPhoto(photoUri);
    }

    public void updatePhoto(Uri photoUri) {
        // Unset primary for all photos
        unsetSuperPrimary();

        // Mark the currently displayed photo as primary
        mPhotoValuesDelta.setSuperPrimary(true);

        mPhotoView.setFullSizedPhoto(photoUri);
    }

    private void unsetSuperPrimary() {
        final List<KindSectionData> kindSectionDataList =
                mKindSectionDataMap.get(Photo.CONTENT_ITEM_TYPE);
        for (KindSectionData kindSectionData : kindSectionDataList) {
            final List<ValuesDelta> valuesDeltaList = kindSectionData.getValuesDeltas();
            for (ValuesDelta valuesDelta : valuesDeltaList) {
                valuesDelta.setSuperPrimary(false);
            }
        }
    }

    /**
     * Pass through to {@link CompactPhotoEditorView#isWritablePhotoSet}.
     */
    public boolean isWritablePhotoSet() {
        return mPhotoView.isWritablePhotoSet();
    }

    /**
     * Get the raw contact ID for the CompactHeaderView photo.
     */
    public long getPhotoRawContactId() {
        return mPhotoRawContactId;
    }

    public StructuredNameEditorView getPrimaryNameEditorView() {
        return mPrimaryNameEditorView;
    }

    /**
     * Returns a data holder for every non-default/non-empty photo from each raw contact, whether
     * the raw contact is writable or not.
     */
    public ArrayList<CompactPhotoSelectionFragment.Photo> getPhotos() {
        final ArrayList<CompactPhotoSelectionFragment.Photo> photos = new ArrayList<>();

        final Bundle updatedPhotos = mListener == null ? null : mListener.getUpdatedPhotos();

        final List<KindSectionData> kindSectionDataList =
                mKindSectionDataMap.get(Photo.CONTENT_ITEM_TYPE);
        for (int i = 0; i < kindSectionDataList.size(); i++) {
            final KindSectionData kindSectionData = kindSectionDataList.get(i);
            final AccountType accountType = kindSectionData.getAccountType();
            final List<ValuesDelta> valuesDeltaList = kindSectionData.getValuesDeltas();
            if (valuesDeltaList == null || valuesDeltaList.isEmpty()) continue;
            for (int j = 0; j < valuesDeltaList.size(); j++) {
                final ValuesDelta valuesDelta = valuesDeltaList.get(j);
                final Bitmap bitmap = EditorUiUtils.getPhotoBitmap(valuesDelta);
                if (bitmap == null) continue;

                final CompactPhotoSelectionFragment.Photo photo =
                        new CompactPhotoSelectionFragment.Photo();
                photo.titleRes = accountType.titleRes;
                photo.iconRes = accountType.iconRes;
                photo.syncAdapterPackageName = accountType.syncAdapterPackageName;
                photo.valuesDelta = valuesDelta;
                photo.primary = valuesDelta.isSuperPrimary();
                photo.kindSectionDataListIndex = i;
                photo.valuesDeltaListIndex = j;

                if (updatedPhotos != null) {
                    photo.updatedPhotoUri = (Uri) updatedPhotos.get(String.valueOf(
                            kindSectionData.getRawContactDelta().getRawContactId()));
                }

                photos.add(photo);
            }
        }

        return photos;
    }

    /**
     * Marks the raw contact photo given as primary for the aggregate contact and updates the
     * UI.
     */
    public void setPrimaryPhoto(CompactPhotoSelectionFragment.Photo photo) {
        // Unset primary for all photos
        unsetSuperPrimary();

        // Find the values delta to mark as primary
        final KindSectionDataList kindSectionDataList =
                mKindSectionDataMap.get(Photo.CONTENT_ITEM_TYPE);
        if (photo.kindSectionDataListIndex < 0
                || photo.kindSectionDataListIndex >= kindSectionDataList.size()) {
            wlog("Invalid kind section data list index");
            return;
        }
        final KindSectionData kindSectionData =
                kindSectionDataList.get(photo.kindSectionDataListIndex);
        final List<ValuesDelta> valuesDeltaList = kindSectionData.getValuesDeltas();
        if (photo.valuesDeltaListIndex >= valuesDeltaList.size()) {
            wlog("Invalid values delta list index");
            return;
        }
        final ValuesDelta valuesDelta = valuesDeltaList.get(photo.valuesDeltaListIndex);
        valuesDelta.setFromTemplate(false);
        valuesDelta.setSuperPrimary(true);

        // Update the UI
        mPhotoView.setPhoto(valuesDelta, mMaterialPalette);
    }

    public View getAggregationAnchorView() {
        final List<CompactKindSectionView> kindSectionViews = getKindSectionViews(
                StructuredName.CONTENT_ITEM_TYPE);
        if (!kindSectionViews.isEmpty()) {
            return mKindSectionViews.getChildAt(0).findViewById(R.id.anchor_view);
        }
        return null;
    }

    public void setGroupMetaData(Cursor groupMetaData) {
        final List<CompactKindSectionView> kindSectionViews = getKindSectionViews(
                GroupMembership.CONTENT_ITEM_TYPE);
        for (CompactKindSectionView kindSectionView : kindSectionViews) {
            kindSectionView.setGroupMetaData(groupMetaData);
            if (mIsExpanded) {
                kindSectionView.setHideWhenEmpty(false);
                kindSectionView.updateEmptyEditors(/* shouldAnimate =*/ true);
            }
        }
    }

    public void setState(RawContactDeltaList rawContactDeltas,
            MaterialColorMapUtils.MaterialPalette materialPalette, ViewIdGenerator viewIdGenerator,
            long photoId, String readOnlyDisplayName, boolean hasNewContact,
            boolean isUserProfile, AccountWithDataSet primaryAccount) {
        mKindSectionDataMap.clear();
        mKindSectionViews.removeAllViews();
        mMoreFields.setVisibility(View.VISIBLE);

        mMaterialPalette = materialPalette;
        mViewIdGenerator = viewIdGenerator;
        mPhotoId = photoId;
        mReadOnlyDisplayName = readOnlyDisplayName;
        mHasNewContact = hasNewContact;
        mIsUserProfile = isUserProfile;
        mPrimaryAccount = primaryAccount;
        if (mPrimaryAccount == null) {
            mPrimaryAccount = ContactEditorUtils.getInstance(getContext()).getDefaultAccount();
        }
        vlog("state: primary " + mPrimaryAccount);

        // Parse the given raw contact deltas
        if (rawContactDeltas == null || rawContactDeltas.isEmpty()) {
            elog("No raw contact deltas");
            if (mListener != null) mListener.onBindEditorsFailed();
            return;
        }
        parseRawContactDeltas(rawContactDeltas);
        if (mKindSectionDataMap.isEmpty()) {
            elog("No kind section data parsed from RawContactDelta(s)");
            if (mListener != null) mListener.onBindEditorsFailed();
            return;
        }
        mPrimaryRawContactDelta = mKindSectionDataMap.get(StructuredName.CONTENT_ITEM_TYPE)
                .getEntryToWrite(mPrimaryAccount, mHasNewContact).first.getRawContactDelta();
        if (mPrimaryRawContactDelta != null) {
            RawContactModifier.ensureKindExists(mPrimaryRawContactDelta,
                    mPrimaryRawContactDelta.getAccountType(mAccountTypeManager),
                    StructuredName.CONTENT_ITEM_TYPE);
            RawContactModifier.ensureKindExists(mPrimaryRawContactDelta,
                    mPrimaryRawContactDelta.getAccountType(mAccountTypeManager),
                    Photo.CONTENT_ITEM_TYPE);
        }

        // Setup the view
        addAccountInfo(rawContactDeltas);
        addPhotoView();
        addKindSectionViews();
        if (mHasNewContact) {
            maybeCopyPrimaryDisplayName();
        }
        if (mIsExpanded) showAllFields();

        if (mListener != null) mListener.onEditorsBound();
    }

    private void parseRawContactDeltas(RawContactDeltaList rawContactDeltas) {
        // Build the kind section data list map
        vlog("parse: " + rawContactDeltas.size() + " rawContactDelta(s)");
        for (int j = 0; j < rawContactDeltas.size(); j++) {
            final RawContactDelta rawContactDelta = rawContactDeltas.get(j);
            vlog("parse: " + j + " rawContactDelta" + rawContactDelta);
            if (rawContactDelta == null || !rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
            if (accountType == null) continue;
            final List<DataKind> dataKinds = accountType.getSortedDataKinds();
            final int dataKindSize = dataKinds == null ? 0 : dataKinds.size();
            vlog("parse: " + dataKindSize + " dataKinds(s)");
            for (int i = 0; i < dataKindSize; i++) {
                final DataKind dataKind = dataKinds.get(i);
                if (dataKind == null || !dataKind.editable) {
                    vlog("parse: " + i + " " + dataKind.mimeType + " dropped read-only");
                    continue;
                }
                final String mimeType = dataKind.mimeType;

                // Skip psuedo mime types
                if (DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME.equals(mimeType)
                        || DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(mimeType)) {
                    vlog("parse: " + i + " " + dataKind.mimeType + " dropped pseudo type");
                    continue;
                }

                final KindSectionDataList kindSectionDataList =
                        getOrCreateKindSectionDataList(mimeType);
                final KindSectionData kindSectionData =
                        new KindSectionData(accountType, dataKind, rawContactDelta);
                kindSectionDataList.add(kindSectionData);

                // Note we must create nickname entries
                if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType)
                        && kindSectionData.getValuesDeltas().isEmpty()) {
                    RawContactModifier.insertChild(rawContactDelta, dataKind);
                }

                vlog("parse: " + i + " " + dataKind.mimeType + " " +
                        kindSectionData.getValuesDeltas().size() + " value(s)");
            }
        }
    }

    private KindSectionDataList getOrCreateKindSectionDataList(String mimeType) {
        // Put structured names and nicknames together
        mimeType = Nickname.CONTENT_ITEM_TYPE.equals(mimeType)
                ? StructuredName.CONTENT_ITEM_TYPE : mimeType;
        KindSectionDataList kindSectionDataList = mKindSectionDataMap.get(mimeType);
        if (kindSectionDataList == null) {
            kindSectionDataList = new KindSectionDataList();
            mKindSectionDataMap.put(mimeType, kindSectionDataList);
        }
        return kindSectionDataList;
    }

    private void addAccountInfo(RawContactDeltaList rawContactDeltas) {
        if (mPrimaryRawContactDelta == null) {
            mAccountHeaderContainer.setVisibility(View.GONE);
            mAccountSelectorContainer.setVisibility(View.GONE);
            return;
        }

        // Get the account information for the primary raw contact delta
        final Pair<String,String> accountInfo = EditorUiUtils.getAccountInfo(getContext(),
                mIsUserProfile, mPrimaryRawContactDelta.getAccountName(),
                mPrimaryRawContactDelta.getAccountType(mAccountTypeManager));

        // The account header and selector show the same information so both shouldn't be visible
        // at the same time
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(getContext()).getAccounts(true);
        mRawContactContainer.setVisibility(View.GONE);
        if (mHasNewContact && !mIsUserProfile && accounts.size() > 1) {
            mAccountHeaderContainer.setVisibility(View.GONE);
            addAccountSelector(accountInfo);
        } else if (mHasNewContact && !mIsUserProfile) {
            addAccountHeader(accountInfo);
            mAccountSelectorContainer.setVisibility(View.GONE);
        } else if (rawContactDeltas.size() > 1) {
            mAccountHeaderContainer.setVisibility(View.GONE);
            mAccountSelectorContainer.setVisibility(View.GONE);
            addRawContactAccountSelector(rawContactDeltas);
        } else {
            addAccountHeader(accountInfo);
            mAccountSelectorContainer.setVisibility(View.GONE);
        }
    }

    private void addAccountHeader(Pair<String,String> accountInfo) {
        if (TextUtils.isEmpty(accountInfo.first)) {
            // Hide this view so the other text view will be centered vertically
            mAccountHeaderName.setVisibility(View.GONE);
        } else {
            mAccountHeaderName.setVisibility(View.VISIBLE);
            mAccountHeaderName.setText(accountInfo.first);
        }
        mAccountHeaderType.setText(accountInfo.second);

        final AccountType primaryAccountType = mPrimaryRawContactDelta.getRawContactAccountType(
                getContext());
        mAccountHeaderIcon.setImageDrawable(primaryAccountType.getDisplayIcon(getContext()));

        mAccountHeaderContainer.setContentDescription(
                EditorUiUtils.getAccountInfoContentDescription(
                        accountInfo.first, accountInfo.second));
    }

    private void addAccountSelector(Pair<String,String> accountInfo) {
        mAccountSelectorContainer.setVisibility(View.VISIBLE);

        if (TextUtils.isEmpty(accountInfo.first)) {
            // Hide this view so the other text view will be centered vertically
            mAccountSelectorName.setVisibility(View.GONE);
        } else {
            mAccountSelectorName.setVisibility(View.VISIBLE);
            mAccountSelectorName.setText(accountInfo.first);
        }
        mAccountSelectorType.setText(accountInfo.second);

        mAccountSelectorContainer.setContentDescription(
                EditorUiUtils.getAccountInfoContentDescription(
                        accountInfo.first, accountInfo.second));

        mAccountSelector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ListPopupWindow popup = new ListPopupWindow(getContext(), null);
                final AccountsListAdapter adapter =
                        new AccountsListAdapter(getContext(),
                                AccountsListAdapter.AccountListFilter.ACCOUNTS_CONTACT_WRITABLE,
                                mPrimaryAccount);
                popup.setWidth(mAccountSelectorContainer.getWidth());
                popup.setAnchorView(mAccountSelectorContainer);
                popup.setAdapter(adapter);
                popup.setModal(true);
                popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
                        UiClosables.closeQuietly(popup);
                        final AccountWithDataSet newAccount = adapter.getItem(position);
                        if (mListener != null && !mPrimaryAccount.equals(newAccount)) {
                            mListener.onRebindEditorsForNewContact(
                                    mPrimaryRawContactDelta,
                                    mPrimaryAccount,
                                    newAccount);
                        }
                    }
                });
                popup.show();
            }
        });
    }

    private void addRawContactAccountSelector(final RawContactDeltaList rawContactDeltas) {
        mRawContactContainer.setVisibility(View.VISIBLE);

        Collections.sort(rawContactDeltas, new RawContactDeltaComparator(getContext()));

        final String accountsSummary = getRawContactsAccountsSummary(
                getContext(), rawContactDeltas);
        mRawContactSummary.setText(accountsSummary);
        mRawContactContainer.setContentDescription(accountsSummary);
        if (mPrimaryRawContactDelta != null) {
            final AccountType primaryAccountType = mPrimaryRawContactDelta.getRawContactAccountType(
                    getContext());
            mPrimaryAccountIcon.setImageDrawable(primaryAccountType.getDisplayIcon(getContext()));
        }

        mRawContactContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ListPopupWindow popup = new ListPopupWindow(getContext(), null);
                final RawContactAccountListAdapter adapter =
                        new RawContactAccountListAdapter(getContext(), rawContactDeltas);
                popup.setWidth(mRawContactContainer.getWidth());
                popup.setAnchorView(mRawContactContainer);
                popup.setAdapter(adapter);
                popup.setModal(true);
                popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
                popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position,
                                            long id) {
                        UiClosables.closeQuietly(popup);

                        if (mListener != null) {
                            final long rawContactId = adapter.getItemId(position);
                            final Uri rawContactUri = ContentUris.withAppendedId(
                                    ContactsContract.RawContacts.CONTENT_URI, rawContactId);
                            final RawContactDelta rawContactDelta = adapter.getItem(position);
                            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(
                                    getContext());
                            final AccountType accountType = rawContactDelta.getAccountType(
                                    accountTypes);
                            final boolean isReadOnly = !accountType.areContactsWritable();

                            mListener.onRawContactSelected(rawContactUri, rawContactId, isReadOnly);
                        }
                    }
                });
                popup.show();
            }
        });
    }

    private static String getRawContactsAccountsSummary(
            Context context, RawContactDeltaList rawContactDeltas) {
        final LinkedHashMap<String, Integer> accountTypeNumber = new LinkedHashMap<>();
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (rawContactDelta.isVisible()) {
                final AccountType accountType = rawContactDelta.getRawContactAccountType(context);
                final String accountTypeLabel = accountType.getDisplayLabel(context).toString();
                if (accountTypeNumber.containsKey(accountTypeLabel)) {
                    int number = accountTypeNumber.get(accountTypeLabel);
                    number++;
                    accountTypeNumber.put(accountTypeLabel, number);
                } else {
                    accountTypeNumber.put(accountTypeLabel, 1);
                }
            }
        }

        final LinkedHashSet<String> linkedAccounts = new LinkedHashSet<>();
        for (String accountTypeLabel : accountTypeNumber.keySet()) {
            final String number = context.getResources().getQuantityString(
                    R.plurals.quickcontact_suggestion_account_type_number,
                    accountTypeNumber.get(accountTypeLabel),
                    accountTypeNumber.get(accountTypeLabel));
            final String accountWithNumber = context.getResources().getString(
                    R.string.quickcontact_suggestion_account_type,
                    accountTypeLabel,
                    number);
            linkedAccounts.add(accountWithNumber);
        }
        return TextUtils.join(",", linkedAccounts);
    }

    private void addPhotoView() {
        // Get the kind section data and values delta that we will display in the photo view
        final KindSectionDataList kindSectionDataList =
                mKindSectionDataMap.get(Photo.CONTENT_ITEM_TYPE);
        final Pair<KindSectionData,ValuesDelta> photoToDisplay =
                kindSectionDataList.getEntryToDisplay(mPhotoId);
        if (photoToDisplay == null) {
            wlog("photo: no kind section data parsed");
            mPhotoView.setVisibility(View.GONE);
            return;
        }

        // Set the photo view
        mPhotoView.setPhoto(photoToDisplay.second, mMaterialPalette);

        // Find the raw contact ID and values delta that will be written when the photo is edited
        final Pair<KindSectionData,ValuesDelta> photoToWrite = kindSectionDataList.getEntryToWrite(
                mPrimaryAccount, mHasNewContact);
        if (photoToWrite == null) {
            mPhotoView.setReadOnly(true);
            return;
        }
        mPhotoView.setReadOnly(false);
        mPhotoRawContactId = photoToWrite.first.getRawContactDelta().getRawContactId();
        mPhotoValuesDelta = photoToWrite.second;
    }

    private void addKindSectionViews() {
        // Sort the kinds
        final TreeSet<Map.Entry<String,KindSectionDataList>> entries =
                new TreeSet<>(KIND_SECTION_DATA_MAP_ENTRY_COMPARATOR);
        entries.addAll(mKindSectionDataMap.entrySet());

        vlog("kind: " + entries.size() + " kindSection(s)");
        int i = -1;
        for (Map.Entry<String, KindSectionDataList> entry : entries) {
            i++;

            final String mimeType = entry.getKey();
            final KindSectionDataList kindSectionDataList = entry.getValue();

            // Ignore mime types that we've already handled
            if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                vlog("kind: " + i + " " + mimeType + " dropped");
                continue;
            }

            if (kindSectionDataList != null && !kindSectionDataList.isEmpty()) {
                vlog("kind: " + i + " " + mimeType + ": " + kindSectionDataList.size() +
                        " kindSectionData(s)");

                final CompactKindSectionView kindSectionView = inflateKindSectionView(
                        mKindSectionViews, kindSectionDataList, mimeType);
                mKindSectionViews.addView(kindSectionView);

                // Keep a pointer to all the KindSectionsViews for each mimeType
                getKindSectionViews(mimeType).add(kindSectionView);
            }
        }
    }

    private List<CompactKindSectionView> getKindSectionViews(String mimeType) {
        List<CompactKindSectionView> kindSectionViews = mKindSectionViewsMap.get(mimeType);
        if (kindSectionViews == null) {
            kindSectionViews = new ArrayList<>();
            mKindSectionViewsMap.put(mimeType, kindSectionViews);
        }
        return kindSectionViews;
    }

    private CompactKindSectionView inflateKindSectionView(ViewGroup viewGroup,
            KindSectionDataList kindSectionDataList, String mimeType) {
        final CompactKindSectionView kindSectionView = (CompactKindSectionView)
                mLayoutInflater.inflate(R.layout.compact_item_kind_section, viewGroup,
                        /* attachToRoot =*/ false);

        if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)
                || Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            // Phone numbers and email addresses are always displayed,
            // even if they are empty
            kindSectionView.setHideWhenEmpty(false);
        }

        // Since phone numbers and email addresses displayed even if they are empty,
        // they will be the only types you add new values to initially for new contacts
        kindSectionView.setShowOneEmptyEditor(true);

        // Sort so the editors wind up in the order we want
        if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
            Collections.sort(kindSectionDataList, new NameEditorComparator(getContext(),
                    mPrimaryRawContactDelta));
        } else {
            Collections.sort(kindSectionDataList, new EditorComparator(getContext()));
        }

        kindSectionView.setState(kindSectionDataList, mViewIdGenerator, mListener);

        return kindSectionView;
    }

    private void maybeCopyPrimaryDisplayName() {
        if (TextUtils.isEmpty(mReadOnlyDisplayName)) return;
        final List<CompactKindSectionView> kindSectionViews
                = mKindSectionViewsMap.get(StructuredName.CONTENT_ITEM_TYPE);
        if (kindSectionViews.isEmpty()) return;
        final CompactKindSectionView primaryNameKindSectionView = kindSectionViews.get(0);
        if (primaryNameKindSectionView.isEmptyName()) {
            vlog("name: using read only display name as primary name");
            primaryNameKindSectionView.setName(mReadOnlyDisplayName);
            mPrimaryNameEditorView = primaryNameKindSectionView.getPrimaryNameEditorView();
        }
    }

    private void showAllFields() {
        // Stop hiding empty editors and allow the user to enter values for all kinds now
        for (int i = 0; i < mKindSectionViews.getChildCount(); i++) {
            final CompactKindSectionView kindSectionView =
                    (CompactKindSectionView) mKindSectionViews.getChildAt(i);
            kindSectionView.setHideWhenEmpty(false);
            kindSectionView.updateEmptyEditors(/* shouldAnimate =*/ true);
        }
        mIsExpanded = true;

        // Hide the more fields button
        mMoreFields.setVisibility(View.GONE);
    }

    private static void vlog(String message) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, message);
        }
    }

    private static void wlog(String message) {
        if (Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, message);
        }
    }

    private static void elog(String message) {
        Log.e(TAG, message);
    }
}
