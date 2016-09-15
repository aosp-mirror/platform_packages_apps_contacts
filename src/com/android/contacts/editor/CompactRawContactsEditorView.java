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

import com.android.contacts.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountDisplayInfo;
import com.android.contacts.common.model.account.AccountDisplayInfoFactory;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.CustomDataItem;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.util.AccountsListAdapter;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.util.UiClosables;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * View to display information from multiple {@link RawContactDelta}s grouped together.
 */
public class CompactRawContactsEditorView extends LinearLayout implements View.OnClickListener {

    static final String TAG = "CompactEditorView";

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
    private AccountDisplayInfoFactory mAccountDisplayInfoFactory;
    private LayoutInflater mLayoutInflater;

    private ViewIdGenerator mViewIdGenerator;
    private MaterialColorMapUtils.MaterialPalette mMaterialPalette;
    private long mAggregatePhotoId = -1;
    private boolean mHasNewContact;
    private boolean mIsUserProfile;
    private AccountWithDataSet mPrimaryAccount;
    private RawContactDeltaList mRawContactDeltas;
    private long mRawContactIdToDisplayAlone = -1;
    private boolean mRawContactDisplayAloneIsReadOnly;
    private boolean mIsEditingReadOnlyRawContactWithNewContact;
    private KindSectionDataList mPhotoKindSectionDataList = new KindSectionDataList();
    private Map<String, KindSectionData> mKindSectionDataMap = new HashMap<>();
    private Set<String> mSortedMimetypes = new TreeSet<>(new MimeTypeComparator());

    // Account header
    private View mAccountHeaderContainer;
    private TextView mAccountHeaderType;
    private TextView mAccountHeaderName;
    private ImageView mAccountHeaderIcon;
    private ImageView mAccountHeaderExpanderIcon;

    // Raw contacts selector
    private View mRawContactContainer;
    private TextView mRawContactSummary;

    private CompactPhotoEditorView mPhotoView;
    private ViewGroup mKindSectionViews;
    private Map<String, CompactKindSectionView> mKindSectionViewMap = new HashMap<>();
    private View mMoreFields;

    private boolean mIsExpanded;

    private long mPhotoRawContactId;
    private ValuesDelta mPhotoValuesDelta;

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
        mAccountDisplayInfoFactory = AccountDisplayInfoFactory.forWritableAccounts(getContext());
        mLayoutInflater = (LayoutInflater)
                getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Account header
        mAccountHeaderContainer = findViewById(R.id.account_header_container);
        mAccountHeaderType = (TextView) findViewById(R.id.account_type);
        mAccountHeaderName = (TextView) findViewById(R.id.account_name);
        mAccountHeaderIcon = (ImageView) findViewById(R.id.account_type_icon);
        mAccountHeaderExpanderIcon = (ImageView) findViewById(R.id.account_expander_icon);

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
        for (int i = 0; i < mPhotoKindSectionDataList.size(); i++) {
            final KindSectionData kindSectionData = mPhotoKindSectionDataList.get(0);
            final List<ValuesDelta> valuesDeltas = kindSectionData.getNonEmptyValuesDeltas();
            for (int j = 0; j < valuesDeltas.size(); j++) {
                valuesDeltas.get(j).setSuperPrimary(false);
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

        for (int i = 0; i < mPhotoKindSectionDataList.size(); i++) {
            final KindSectionData kindSectionData = mPhotoKindSectionDataList.get(i);
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
        if (photo.kindSectionDataListIndex < 0
                || photo.kindSectionDataListIndex >= mPhotoKindSectionDataList.size()) {
            wlog("Invalid kind section data list index");
            return;
        }
        final KindSectionData kindSectionData =
                mPhotoKindSectionDataList.get(photo.kindSectionDataListIndex);
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
        CompactKindSectionView nameView = mKindSectionViewMap.get(StructuredName.CONTENT_ITEM_TYPE);
        return nameView != null ? nameView.getChildAt(0).findViewById(R.id.anchor_view) : null;
    }

    public void setGroupMetaData(Cursor groupMetaData) {
        final CompactKindSectionView groupKindSectionView =
                mKindSectionViewMap.get(GroupMembership.CONTENT_ITEM_TYPE);
        if (groupKindSectionView == null) {
            return;
        }
        groupKindSectionView.setGroupMetaData(groupMetaData);
        if (mIsExpanded) {
            groupKindSectionView.setHideWhenEmpty(false);
            groupKindSectionView.updateEmptyEditors(/* shouldAnimate =*/ true);
        }
    }

    public void setState(RawContactDeltaList rawContactDeltas,
            MaterialColorMapUtils.MaterialPalette materialPalette, ViewIdGenerator viewIdGenerator,
            long photoId, boolean hasNewContact, boolean isUserProfile,
            AccountWithDataSet primaryAccount, long rawContactIdToDisplayAlone,
            boolean rawContactDisplayAloneIsReadOnly,
            boolean isEditingReadOnlyRawContactWithNewContact) {

        mRawContactDeltas = rawContactDeltas;
        mRawContactIdToDisplayAlone = rawContactIdToDisplayAlone;
        mRawContactDisplayAloneIsReadOnly = rawContactDisplayAloneIsReadOnly;
        mIsEditingReadOnlyRawContactWithNewContact = isEditingReadOnlyRawContactWithNewContact;

        mKindSectionDataMap.clear();
        mKindSectionViewMap.clear();
        mKindSectionViews.removeAllViews();
        mMoreFields.setVisibility(View.VISIBLE);

        mMaterialPalette = materialPalette;
        mViewIdGenerator = viewIdGenerator;
        mAggregatePhotoId = photoId;

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

        final KindSectionData nameSectionData =
                mKindSectionDataMap.get(StructuredName.CONTENT_ITEM_TYPE);
        // Ensure that a structured name and photo exists
        if (nameSectionData != null) {
            final RawContactDelta rawContactDelta =
                    nameSectionData.getRawContactDelta();
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
        addPhotoView();
        if (isSingleReadOnlyRawContact()) {
            // We're want to display the inputs fields for a single read only raw contact
            addReadOnlyRawContactEditorViews();
            // Hide the "More fields" link
            mMoreFields.setVisibility(View.GONE);
        } else if (mIsEditingReadOnlyRawContactWithNewContact) {
            // A new writable raw contact was created and joined with the read only contact
            // that the user is trying to edit.
            setupCompactEditorNormally();

            // TODO: Hide the raw contact selector since it will just contain the read-only raw
            // contact and clicking that will just open the exact same editor.  When we clean up
            // the whole account header, selector, and raw contact selector mess, we can prevent
            // the selector from being displayed in a less hacky way.
            mRawContactContainer.setVisibility(View.GONE);
        } else if (mRawContactDeltas.size() > 1) {
            // We're editing an aggregate composed of more than one writable raw contacts

            // TODO: Don't render any input fields. Eventually we will show a list of account
            // types and names but for now just show the account selector and hide the "More fields"
            // link.
            addAccountInfo();
            mMoreFields.setVisibility(View.GONE);
        } else {
            setupCompactEditorNormally();
        }
        if (mListener != null) mListener.onEditorsBound();
    }

    private void setupCompactEditorNormally() {
        addAccountInfo();
        addKindSectionViews();

        mMoreFields.setVisibility(hasMoreFields() ? View.VISIBLE : View.GONE);

        if (mIsExpanded) showAllFields();
    }

    private boolean isSingleReadOnlyRawContact() {
        return mRawContactDeltas.size() == 1
                && mRawContactDeltas.get(0).getRawContactId() == mRawContactIdToDisplayAlone
                && mRawContactDisplayAloneIsReadOnly;
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
                if (dataKind == null) {
                    vlog("parse: " + i + " " + dataKind.mimeType + " dropped null data kind");
                    continue;
                }
                final String mimeType = dataKind.mimeType;

                // Skip psuedo mime types
                if (DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME.equals(mimeType)
                        || DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(mimeType)) {
                    vlog("parse: " + i + " " + dataKind.mimeType + " dropped pseudo type");
                    continue;
                }

                // Skip custom fields
                // TODO: Handle them when we implement editing custom fields.
                if (CustomDataItem.MIMETYPE_CUSTOM_FIELD.equals(mimeType)) {
                    vlog("parse: " + i + " " + dataKind.mimeType + " dropped custom field");
                    continue;
                }

                // Add all photo data.
                if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final KindSectionData photoKindSectionData =
                            new KindSectionData(accountType, dataKind, rawContactDelta);
                    mPhotoKindSectionDataList.add(photoKindSectionData);
                    vlog("parse: " + i + " " + dataKind.mimeType + " " +
                            photoKindSectionData.getValuesDeltas().size() + " value(s) " +
                            photoKindSectionData.getNonEmptyValuesDeltas().size() +
                            " non-empty value(s) " +
                            photoKindSectionData.getVisibleValuesDeltas().size() +
                            " visible value(s)");
                    continue;
                }

                // Skip the non-writable names when we're auto creating a new writable contact.
                if (mIsEditingReadOnlyRawContactWithNewContact
                        && !accountType.areContactsWritable()
                        && StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    vlog("parse: " + i + " " + dataKind.mimeType + " dropped non-writable name");
                    continue;
                }

                // Skip non-photo data that doesn't belong to the single raw contact we're editing.
                if (mRawContactIdToDisplayAlone > 0 &&
                        !rawContactDelta.getRawContactId().equals(mRawContactIdToDisplayAlone)) {
                    continue;
                }

                final KindSectionData kindSectionData =
                        new KindSectionData(accountType, dataKind, rawContactDelta);
                mKindSectionDataMap.put(mimeType, kindSectionData);
                mSortedMimetypes.add(mimeType);

                vlog("parse: " + i + " " + dataKind.mimeType + " " +
                        kindSectionData.getValuesDeltas().size() + " value(s) " +
                        kindSectionData.getNonEmptyValuesDeltas().size() + " non-empty value(s) " +
                        kindSectionData.getVisibleValuesDeltas().size() +
                        " visible value(s)");
            }
        }
    }

    private void addReadOnlyRawContactEditorViews() {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(
                getContext());

        for (int i = 0; i < mRawContactDeltas.size(); i++) {
            final RawContactDelta rawContactDelta = mRawContactDeltas.get(i);
            if (!rawContactDelta.isVisible()) continue;
            final AccountType type = rawContactDelta.getAccountType(accountTypes);
            if (type.areContactsWritable()) continue;

            final BaseRawContactEditorView editor = (BaseRawContactEditorView) inflater.inflate(
                        R.layout.raw_contact_readonly_editor_view, mKindSectionViews, false);
            editor.setCollapsed(false);
            mKindSectionViews.addView(editor);
            editor.setState(rawContactDelta, type, mViewIdGenerator, mIsUserProfile);
        }
    }

    private void addAccountInfo() {
        mAccountHeaderContainer.setVisibility(View.GONE);
        mRawContactContainer.setVisibility(View.GONE);
        final KindSectionData nameSectionData =
                mKindSectionDataMap.get(StructuredName.CONTENT_ITEM_TYPE);
        if (nameSectionData == null) return;
        final RawContactDelta rawContactDelta =
                nameSectionData.getRawContactDelta();

        final AccountDisplayInfo account =
                mAccountDisplayInfoFactory.getAccountDisplayInfoFor(rawContactDelta);

        // Get the account information for the primary raw contact delta
        final String accountLabel = mIsUserProfile
                ? EditorUiUtils.getAccountHeaderLabelForMyProfile(getContext(), account)
                : account.getNameLabel().toString();

        // Either the account header or selector should be shown, not both.
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(getContext()).getAccounts(true);
        if (mHasNewContact && !mIsUserProfile) {
            if (accounts.size() > 1) {
                addAccountSelector(rawContactDelta, accountLabel);
            } else {
                addAccountHeader(accountLabel);
            }
        } else if (mIsUserProfile || !shouldHideAccountContainer(mRawContactDeltas)) {
            addAccountHeader(accountLabel);
        }

        // The raw contact selector should only display linked raw contacts that can be edited in
        // the full editor (i.e. they are not newly created raw contacts)
        final RawContactAccountListAdapter adapter =  new RawContactAccountListAdapter(getContext(),
                getRawContactDeltaListForSelector(mRawContactDeltas));
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
        for (int i = 0; i < rawContactDeltas.size(); i++) {
            final RawContactDelta rawContactDelta = rawContactDeltas.get(i);
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

    private void addAccountHeader(String accountLabel) {
        mAccountHeaderContainer.setVisibility(View.VISIBLE);

        // Set the account name
        mAccountHeaderName.setVisibility(View.VISIBLE);
        mAccountHeaderName.setText(accountLabel);

        // Set the account type
        final String selectorTitle = getResources().getString(
                R.string.compact_editor_account_selector_title);
        mAccountHeaderType.setText(selectorTitle);

        // Set the icon
        final KindSectionData nameSectionData =
                mKindSectionDataMap.get(StructuredName.CONTENT_ITEM_TYPE);
        if (nameSectionData != null) {
            final RawContactDelta rawContactDelta =
                    nameSectionData.getRawContactDelta();
            if (rawContactDelta != null) {
                final AccountType accountType =
                        rawContactDelta.getRawContactAccountType(getContext());
                mAccountHeaderIcon.setImageDrawable(accountType.getDisplayIcon(getContext()));
            }
        }

        // Set the content description
        mAccountHeaderContainer.setContentDescription(
                EditorUiUtils.getAccountInfoContentDescription(accountLabel,
                        selectorTitle));
    }

    private void addAccountSelector(final RawContactDelta rawContactDelta, CharSequence nameLabel) {
        // Show save to default account.
        addAccountHeader(nameLabel.toString());
        // Add handlers for choosing another account to save to.
        mAccountHeaderExpanderIcon.setVisibility(View.VISIBLE);
        mAccountHeaderContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ListPopupWindow popup = new ListPopupWindow(getContext(), null);
                final AccountsListAdapter adapter =
                        new AccountsListAdapter(getContext(),
                                AccountsListAdapter.AccountListFilter.ACCOUNTS_CONTACT_WRITABLE,
                                mPrimaryAccount);
                popup.setWidth(mAccountHeaderContainer.getWidth());
                popup.setAnchorView(mAccountHeaderContainer);
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
        // Get the kind section data and values delta that we will display in the photo view. Either
        // the aggregate photo or the photo from the raw contact that is being edited.
        final Pair<KindSectionData, ValuesDelta> photo =
                mPhotoKindSectionDataList.getEntryToDisplay(
                        mRawContactIdToDisplayAlone > 0
                                ? mRawContactIdToDisplayAlone
                                : mAggregatePhotoId);
        if (photo == null) {
            wlog("photo: no kind section data parsed");
            mPhotoView.setVisibility(View.GONE);
            return;
        } else {
            mPhotoView.setVisibility(View.VISIBLE);
        }

        // Set the photo view
        mPhotoView.setPhoto(photo.second, mMaterialPalette);

        // If we're showing an aggregate photo, set it to read only.
        if (mRawContactIdToDisplayAlone < 1) {
            mPhotoView.setReadOnly(true);
            return;
        }
        mPhotoView.setReadOnly(false);
        mPhotoRawContactId = photo.first.getRawContactDelta().getRawContactId();
        mPhotoValuesDelta = photo.second;
    }

    private void addKindSectionViews() {
        int i = -1;

        for (String mimeType : mSortedMimetypes) {
            i++;
            final CompactKindSectionView kindSectionView;
            // TODO: Since we don't have a primary name kind anymore, refactor and collapse
            // these two branches and the following code paths.
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final KindSectionData nameSectionData = mKindSectionDataMap.get(mimeType);
                if (nameSectionData == null) {
                    vlog("kind: " + i + " " + mimeType + " dropped");
                    continue;
                }
                vlog("kind: " + i + " " + mimeType + " using first entry only");
                kindSectionView = inflateKindSectionView(
                        mKindSectionViews, nameSectionData, mimeType,
                        nameSectionData.getValuesDeltas().get(0));
            } else {
                final KindSectionData kindSectionData = mKindSectionDataMap.get(mimeType);

                // Ignore mime types that we've already handled
                if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    vlog("kind: " + i + " " + mimeType + " dropped");
                    continue;
                }
                kindSectionView = inflateKindSectionView(
                        mKindSectionViews, kindSectionData, mimeType,
                        /* primaryValueDelta =*/ null);
            }
            mKindSectionViews.addView(kindSectionView);

            // Keep a pointer to the KindSectionView for each mimeType
            mKindSectionViewMap.put(mimeType, kindSectionView);
        }
    }

    private CompactKindSectionView inflateKindSectionView(ViewGroup viewGroup,
            KindSectionData kindSectionData, String mimeType,
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

        kindSectionView.setState(kindSectionData, mViewIdGenerator, mListener,
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
        return mKindSectionViewMap.get(StructuredName.CONTENT_ITEM_TYPE);
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

    private boolean hasMoreFields() {
        for (CompactKindSectionView section : mKindSectionViewMap.values()) {
            if (section.getVisibility() != View.VISIBLE) {
                return true;
            }
        }
        return false;
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
