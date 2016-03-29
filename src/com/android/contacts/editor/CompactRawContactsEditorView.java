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
import java.util.List;
import java.util.Map;
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
         * Invoked when a rawcontact from linked contacts is selected in editor.
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
                if (rawContactDelta.isVisible() && rawContactDelta.getRawContactId() > 0) {
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

            final TextView text1 = (TextView) resultView.findViewById(android.R.id.text1);
            final AccountType accountType = rawContactDelta.getRawContactAccountType(mContext);
            text1.setText(accountType.getDisplayLabel(mContext));

            final TextView text2 = (TextView) resultView.findViewById(android.R.id.text2);
            final String accountName = rawContactDelta.getAccountName();
            if (TextUtils.isEmpty(accountName)) {
                text2.setVisibility(View.GONE);
            } else {
                // Truncate email addresses in the middle so we don't lose the domain
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
    private long mPhotoId = -1;
    private boolean mHasNewContact;
    private boolean mIsUserProfile;
    private AccountWithDataSet mPrimaryAccount;
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

    private CompactPhotoEditorView mPhotoView;
    private ViewGroup mKindSectionViews;
    private Map<String,List<CompactKindSectionView>> mKindSectionViewsMap = new HashMap<>();
    private View mMoreFields;

    private boolean mIsExpanded;

    private long mPhotoRawContactId;
    private ValuesDelta mPhotoValuesDelta;

    private Pair<KindSectionData, ValuesDelta> mPrimaryNameKindSectionData;

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
        mPhotoValuesDelta.setFromTemplate(true);
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
        mPhotoValuesDelta.setFromTemplate(false);
        // Unset primary for all photos
        unsetSuperPrimaryFromAllPhotos();
        // Mark the currently displayed photo as primary
        mPhotoValuesDelta.setSuperPrimary(true);

        // Even though high-res photos cannot be saved by passing them via
        // an EntityDeltaList (since they cause the Bundle size limit to be
        // exceeded), we still pass a low-res thumbnail. This simplifies
        // code all over the place, because we don't have to test whether
        // there is a change in EITHER the delta-list OR a changed photo...
        // this way, there is always a change in the delta-list.
        try {
            final byte[] bytes = EditorUiUtils.getCompressedThumbnailBitmapBytes(
                    getContext(), photoUri);
            if (bytes != null) {
                mPhotoValuesDelta.setPhoto(bytes);
            }
        } catch (FileNotFoundException e) {
            elog("Failed to get bitmap from photo Uri");
        }

        mPhotoView.setFullSizedPhoto(photoUri);
    }

    private void unsetSuperPrimaryFromAllPhotos() {
        final List<KindSectionData> kindSectionDataList =
                mKindSectionDataMap.get(Photo.CONTENT_ITEM_TYPE);
        for (KindSectionData kindSectionData : kindSectionDataList) {
            for (ValuesDelta valuesDelta : kindSectionData.getNonEmptyValuesDeltas()) {
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
        final CompactKindSectionView primaryNameKindSectionView = getPrimaryNameKindSectionView();
        return primaryNameKindSectionView == null
                ? null : primaryNameKindSectionView.getPrimaryNameEditorView();
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
            final List<ValuesDelta> valuesDeltas = kindSectionData.getNonEmptyValuesDeltas();
            if (valuesDeltas.isEmpty()) continue;
            for (int j = 0; j < valuesDeltas.size(); j++) {
                final ValuesDelta valuesDelta = valuesDeltas.get(j);
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
                photo.photoId = valuesDelta.getId();

                if (updatedPhotos != null) {
                    photo.updatedPhotoUri = (Uri) updatedPhotos.get(String.valueOf(
                            kindSectionData.getRawContactDelta().getRawContactId()));
                }

                final CharSequence accountTypeLabel = accountType.getDisplayLabel(getContext());
                photo.accountType = accountTypeLabel == null ? "" : accountTypeLabel.toString();
                final String accountName = kindSectionData.getRawContactDelta().getAccountName();
                photo.accountName = accountName == null ? "" : accountName;

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
        final List<ValuesDelta> valuesDeltaList = kindSectionData.getNonEmptyValuesDeltas();
        if (photo.valuesDeltaListIndex >= valuesDeltaList.size()) {
            wlog("Invalid values delta list index");
            return;
        }

        // Update values delta
        final ValuesDelta valuesDelta = valuesDeltaList.get(photo.valuesDeltaListIndex);
        valuesDelta.setFromTemplate(false);
        unsetSuperPrimaryFromAllPhotos();
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
            long photoId, boolean hasNewContact, boolean isUserProfile,
            AccountWithDataSet primaryAccount) {
        mKindSectionDataMap.clear();
        mKindSectionViews.removeAllViews();
        mMoreFields.setVisibility(View.VISIBLE);

        mMaterialPalette = materialPalette;
        mViewIdGenerator = viewIdGenerator;
        mPhotoId = photoId;

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

        // Get the primary name kind section data
        mPrimaryNameKindSectionData = mKindSectionDataMap.get(StructuredName.CONTENT_ITEM_TYPE)
                .getEntryToWrite(/* id =*/ -1, mPrimaryAccount, mIsUserProfile);
        if (mPrimaryNameKindSectionData != null) {
            // Ensure that a structured name and photo exists
            final RawContactDelta rawContactDelta =
                    mPrimaryNameKindSectionData.first.getRawContactDelta();
            RawContactModifier.ensureKindExists(
                    rawContactDelta,
                    rawContactDelta.getAccountType(mAccountTypeManager),
                    StructuredName.CONTENT_ITEM_TYPE);
            RawContactModifier.ensureKindExists(
                    rawContactDelta,
                    rawContactDelta.getAccountType(mAccountTypeManager),
                    Photo.CONTENT_ITEM_TYPE);
        }

        // Setup the view
        addAccountInfo(rawContactDeltas);
        addPhotoView();
        addKindSectionViews();

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

                vlog("parse: " + i + " " + dataKind.mimeType + " " +
                        kindSectionData.getValuesDeltas().size() + " value(s) " +
                        kindSectionData.getNonEmptyValuesDeltas().size() + " non-empty value(s) " +
                        kindSectionData.getVisibleValuesDeltas().size() +
                        " visible value(s)");
            }
        }
    }

    private KindSectionDataList getOrCreateKindSectionDataList(String mimeType) {
        KindSectionDataList kindSectionDataList = mKindSectionDataMap.get(mimeType);
        if (kindSectionDataList == null) {
            kindSectionDataList = new KindSectionDataList();
            mKindSectionDataMap.put(mimeType, kindSectionDataList);
        }
        return kindSectionDataList;
    }

    private void addAccountInfo(RawContactDeltaList rawContactDeltas) {
        mAccountHeaderContainer.setVisibility(View.GONE);
        mAccountSelectorContainer.setVisibility(View.GONE);
        mRawContactContainer.setVisibility(View.GONE);

        if (mPrimaryNameKindSectionData == null) return;
        final RawContactDelta rawContactDelta =
                mPrimaryNameKindSectionData.first.getRawContactDelta();

        // Get the account information for the primary raw contact delta
        final Pair<String,String> accountInfo = mIsUserProfile
                ? EditorUiUtils.getLocalAccountInfo(getContext(),
                        rawContactDelta.getAccountName(),
                        rawContactDelta.getAccountType(mAccountTypeManager))
                : EditorUiUtils.getAccountInfo(getContext(),
                        rawContactDelta.getAccountName(),
                        rawContactDelta.getAccountType(mAccountTypeManager));

        // Either the account header or selector should be shown, not both.
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(getContext()).getAccounts(true);
        if (mHasNewContact && !mIsUserProfile) {
            if (accounts.size() > 1) {
                addAccountSelector(accountInfo, rawContactDelta);
            } else {
                addAccountHeader(accountInfo);
            }
        } else if (mIsUserProfile || !shouldHideAccountContainer(rawContactDeltas)) {
            addAccountHeader(accountInfo);
        }

        // The raw contact selector should only display linked raw contacts that can be edited in
        // the full editor (i.e. they are not newly created raw contacts)
        final RawContactAccountListAdapter adapter =  new RawContactAccountListAdapter(getContext(),
                getRawContactDeltaListForSelector(rawContactDeltas));
        if (adapter.getCount() > 0) {
            final String accountsSummary = getResources().getQuantityString(
                    R.plurals.compact_editor_linked_contacts_selector_title,
                    adapter.getCount(), adapter.getCount());
            addRawContactAccountSelector(accountsSummary, adapter);
        }
    }

    private RawContactDeltaList getRawContactDeltaListForSelector(
            RawContactDeltaList rawContactDeltas) {
        // Sort raw contacts so google accounts come first
        Collections.sort(rawContactDeltas, new RawContactDeltaComparator(getContext()));

        final RawContactDeltaList result = new RawContactDeltaList();
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (rawContactDelta.isVisible() && rawContactDelta.getRawContactId() > 0) {
                // Only add raw contacts that can be opened in the editor
                result.add(rawContactDelta);
            }
        }
        // Don't return a list of size 1 that would just open the raw contact being edited
        // in the compact editor in the full editor
        if (result.size() == 1 && result.get(0).getRawContactAccountType(
                getContext()).areContactsWritable()) {
            result.clear();
            return result;
        }
        return result;
    }

    // Returns true if there are multiple writable rawcontacts and no read-only ones,
    // or there are both writable and read-only rawcontacts.
    private boolean shouldHideAccountContainer(RawContactDeltaList rawContactDeltas) {
        int writable = 0;
        int readonly = 0;
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (rawContactDelta.isVisible() && rawContactDelta.getRawContactId() > 0) {
                if (rawContactDelta.getRawContactAccountType(getContext()).areContactsWritable()) {
                    writable++;
                } else {
                    readonly++;
                }
            }
        }
        return (writable > 1 || (writable > 0 && readonly > 0));
    }

    private void addAccountHeader(Pair<String,String> accountInfo) {
        mAccountHeaderContainer.setVisibility(View.VISIBLE);

        // Set the account name
        final String accountName = TextUtils.isEmpty(accountInfo.first)
                ? accountInfo.second : accountInfo.first;
        mAccountHeaderName.setVisibility(View.VISIBLE);
        mAccountHeaderName.setText(accountName);

        // Set the account type
        final String selectorTitle = getResources().getString(
                R.string.compact_editor_account_selector_title);
        mAccountHeaderType.setText(selectorTitle);

        // Set the icon
        if (mPrimaryNameKindSectionData != null) {
            final RawContactDelta rawContactDelta =
                    mPrimaryNameKindSectionData.first.getRawContactDelta();
            if (rawContactDelta != null) {
                final AccountType accountType =
                        rawContactDelta.getRawContactAccountType(getContext());
                mAccountHeaderIcon.setImageDrawable(accountType.getDisplayIcon(getContext()));
            }
        }

        // Set the content description
        mAccountHeaderContainer.setContentDescription(
                EditorUiUtils.getAccountInfoContentDescription(accountName, selectorTitle));
    }

    private void addAccountSelector(Pair<String,String> accountInfo,
            final RawContactDelta rawContactDelta) {
        mAccountSelectorContainer.setVisibility(View.VISIBLE);

        if (TextUtils.isEmpty(accountInfo.first)) {
            // Hide this view so the other text view will be centered vertically
            mAccountSelectorName.setVisibility(View.GONE);
        } else {
            mAccountSelectorName.setVisibility(View.VISIBLE);
            mAccountSelectorName.setText(accountInfo.first);
        }

        final String selectorTitle = getResources().getString(
                R.string.compact_editor_account_selector_title);
        mAccountSelectorType.setText(selectorTitle);

        mAccountSelectorContainer.setContentDescription(getResources().getString(
                R.string.compact_editor_account_selector_description, accountInfo.first));

        mAccountSelectorContainer.setOnClickListener(new View.OnClickListener() {
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
                                    rawContactDelta,
                                    mPrimaryAccount,
                                    newAccount);
                        }
                    }
                });
                popup.show();
            }
        });
    }

    private void addRawContactAccountSelector(String accountsSummary,
            final RawContactAccountListAdapter adapter) {
        mRawContactContainer.setVisibility(View.VISIBLE);

        mRawContactSummary.setText(accountsSummary);

        mRawContactContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ListPopupWindow popup = new ListPopupWindow(getContext(), null);
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
        final Pair<KindSectionData, ValuesDelta> photoToWrite = kindSectionDataList.getEntryToWrite(
                mPhotoId, mPrimaryAccount, mIsUserProfile);
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

            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                if (mPrimaryNameKindSectionData == null) {
                    vlog("kind: " + i + " " + mimeType + " dropped");
                    continue;
                }
                vlog("kind: " + i + " " + mimeType + " using first entry only");
                final KindSectionDataList kindSectionDataList = new KindSectionDataList();
                kindSectionDataList.add(mPrimaryNameKindSectionData.first);
                final CompactKindSectionView kindSectionView = inflateKindSectionView(
                        mKindSectionViews, kindSectionDataList, mimeType,
                        mPrimaryNameKindSectionData.second);
                mKindSectionViews.addView(kindSectionView);

                // Keep a pointer to all the KindSectionsViews for each mimeType
                getKindSectionViews(mimeType).add(kindSectionView);
            } else {
                final KindSectionDataList kindSectionDataList = entry.getValue();

                // Ignore mime types that we've already handled
                if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    vlog("kind: " + i + " " + mimeType + " dropped");
                    continue;
                }

                // Don't show more than one group editor on the compact editor.
                // Groups will still be editable for each raw contact individually on the full editor.
                if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)
                        && kindSectionDataList.size() > 1) {
                    vlog("kind: " + i + " " + mimeType + " dropped");
                    continue;
                }

                if (kindSectionDataList != null && !kindSectionDataList.isEmpty()) {
                    vlog("kind: " + i + " " + mimeType + " " + kindSectionDataList.size() +
                            " kindSectionData(s)");

                    final CompactKindSectionView kindSectionView = inflateKindSectionView(
                            mKindSectionViews, kindSectionDataList, mimeType,
                            /* primaryValueDelta =*/ null);
                    mKindSectionViews.addView(kindSectionView);

                    // Keep a pointer to all the KindSectionsViews for each mimeType
                    getKindSectionViews(mimeType).add(kindSectionView);
                }
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
            KindSectionDataList kindSectionDataList, String mimeType,
            ValuesDelta primaryValuesDelta) {
        final CompactKindSectionView kindSectionView = (CompactKindSectionView)
                mLayoutInflater.inflate(R.layout.compact_item_kind_section, viewGroup,
                        /* attachToRoot =*/ false);
        kindSectionView.setIsUserProfile(mIsUserProfile);

        if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)
                || Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            // Phone numbers and email addresses are always displayed,
            // even if they are empty
            kindSectionView.setHideWhenEmpty(false);
        }

        // Since phone numbers and email addresses displayed even if they are empty,
        // they will be the only types you add new values to initially for new contacts
        kindSectionView.setShowOneEmptyEditor(true);

        // Sort non-name editors so they wind up in the order we want
        if (!StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
            Collections.sort(kindSectionDataList, new EditorComparator(getContext()));
        }

        kindSectionView.setState(kindSectionDataList, mViewIdGenerator, mListener,
                primaryValuesDelta);

        return kindSectionView;
    }

    void maybeSetReadOnlyDisplayNameAsPrimary(String readOnlyDisplayName) {
        if (TextUtils.isEmpty(readOnlyDisplayName)) return;
        final CompactKindSectionView primaryNameKindSectionView = getPrimaryNameKindSectionView();
        if (primaryNameKindSectionView != null && primaryNameKindSectionView.isEmptyName()) {
            vlog("name: using read only display name as primary name");
            primaryNameKindSectionView.setName(readOnlyDisplayName);
        }
    }

    private CompactKindSectionView getPrimaryNameKindSectionView() {
        final List<CompactKindSectionView> kindSectionViews
                = mKindSectionViewsMap.get(StructuredName.CONTENT_ITEM_TYPE);
        return kindSectionViews == null || kindSectionViews.isEmpty()
                ? null : kindSectionViews.get(0);
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
