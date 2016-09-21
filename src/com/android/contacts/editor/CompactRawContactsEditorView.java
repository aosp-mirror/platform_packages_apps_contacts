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

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
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
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountDisplayInfo;
import com.android.contacts.common.model.account.AccountDisplayInfoFactory;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.DeviceLocalAccountType;
import com.android.contacts.common.model.account.SimAccountType;
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
        void onRawContactSelected(long rawContactId, boolean isReadOnly);
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
            if (TextUtils.isEmpty(accountName) || accountType instanceof DeviceLocalAccountType
                    || accountType instanceof SimAccountType) {
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
    private boolean mHasNewContact;
    private boolean mIsUserProfile;
    private AccountWithDataSet mPrimaryAccount;
    private RawContactDeltaList mRawContactDeltas;
    private RawContactDelta mCurrentRawContactDelta;
    private long mRawContactIdToDisplayAlone = -1;
    private boolean mIsEditingReadOnlyRawContactWithNewContact;
    private Map<String, KindSectionData> mKindSectionDataMap = new HashMap<>();
    private Set<String> mSortedMimetypes = new TreeSet<>(new MimeTypeComparator());

    // Account header
    private View mAccountHeaderContainer;
    private TextView mAccountHeaderType;
    private TextView mAccountHeaderName;
    private ImageView mAccountHeaderIcon;
    private ImageView mAccountHeaderExpanderIcon;

    private CompactPhotoEditorView mPhotoView;
    private ViewGroup mKindSectionViews;
    private Map<String, CompactKindSectionView> mKindSectionViewMap = new HashMap<>();
    private View mMoreFields;

    private boolean mIsExpanded;

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
        for (int i = 0; i < mRawContactDeltas.size(); i++) {
            final RawContactDelta rawContactDelta = mRawContactDeltas.get(i);
            if (!rawContactDelta.hasMimeEntries(Photo.CONTENT_ITEM_TYPE)) {
                continue;
            }
            final List<ValuesDelta> photosDeltas =
                    mRawContactDeltas.get(i).getMimeEntries(Photo.CONTENT_ITEM_TYPE);
            if (photosDeltas == null) {
                continue;
            }
            for (int j = 0; j < photosDeltas.size(); j++) {
                photosDeltas.get(j).setSuperPrimary(false);
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
        return mCurrentRawContactDelta.getRawContactId();
    }

    public StructuredNameEditorView getPrimaryNameEditorView() {
        final CompactKindSectionView primaryNameKindSectionView = getPrimaryNameKindSectionView();
        return primaryNameKindSectionView == null
                ? null : primaryNameKindSectionView.getPrimaryNameEditorView();
    }


    /**
     * Marks the raw contact photo given as primary for the aggregate contact.
     */
    public void setPrimaryPhoto() {

        // Update values delta
        final ValuesDelta valuesDelta = mCurrentRawContactDelta
                .getSuperPrimaryEntry(Photo.CONTENT_ITEM_TYPE);
        if (valuesDelta == null) {
            Log.wtf(TAG, "setPrimaryPhoto: had no ValuesDelta for the current RawContactDelta");
            return;
        }
        valuesDelta.setFromTemplate(false);
        unsetSuperPrimaryFromAllPhotos();
        valuesDelta.setSuperPrimary(true);
    }

    public View getAggregationAnchorView() {
        final StructuredNameEditorView nameEditorView = getPrimaryNameEditorView();
        return nameEditorView != null ? nameEditorView.findViewById(R.id.anchor_view) : null;
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
            boolean hasNewContact, boolean isUserProfile, AccountWithDataSet primaryAccount,
            long rawContactIdToDisplayAlone, boolean isEditingReadOnlyRawContactWithNewContact) {

        mRawContactDeltas = rawContactDeltas;
        mRawContactIdToDisplayAlone = rawContactIdToDisplayAlone;
        mIsEditingReadOnlyRawContactWithNewContact = isEditingReadOnlyRawContactWithNewContact;

        mKindSectionViewMap.clear();
        mKindSectionViews.removeAllViews();
        mMoreFields.setVisibility(View.VISIBLE);

        mMaterialPalette = materialPalette;
        mViewIdGenerator = viewIdGenerator;

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
        pickRawContactDelta();
        parseRawContactDelta();
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
        if (isReadOnlyRawContact()) {
            // We're want to display the inputs fields for a single read only raw contact
            addReadOnlyRawContactEditorViews();
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

    private boolean isReadOnlyRawContact() {
        return !mCurrentRawContactDelta.getAccountType(mAccountTypeManager).areContactsWritable();
    }

    private void pickRawContactDelta() {
        // Build the kind section data list map
        vlog("parse: " + mRawContactDeltas.size() + " rawContactDelta(s)");
        for (int j = 0; j < mRawContactDeltas.size(); j++) {
            final RawContactDelta rawContactDelta = mRawContactDeltas.get(j);
            vlog("parse: " + j + " rawContactDelta" + rawContactDelta);
            if (rawContactDelta == null || !rawContactDelta.isVisible()) continue;
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
            if (accountType == null) continue;

            if (mRawContactIdToDisplayAlone > 0) {
                // Look for the raw contact if specified.
                if (rawContactDelta.getRawContactId().equals(mRawContactIdToDisplayAlone)) {
                    mCurrentRawContactDelta = rawContactDelta;
                    return;
                }
            } else if (mPrimaryAccount != null
                    && mPrimaryAccount.equals(rawContactDelta.getAccountWithDataSet())) {
                // Otherwise try to find the one that matches the default.
                mCurrentRawContactDelta = rawContactDelta;
                return;
            } else if (accountType.areContactsWritable()){
                // TODO: Find better raw contact delta
                // Just select an arbitrary writable contact.
                mCurrentRawContactDelta = rawContactDelta;
            }
        }

    }

    private void parseRawContactDelta() {
        mKindSectionDataMap.clear();
        mSortedMimetypes.clear();

        final AccountType accountType = mCurrentRawContactDelta.getAccountType(mAccountTypeManager);
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

            final KindSectionData kindSectionData =
                    new KindSectionData(accountType, dataKind, mCurrentRawContactDelta);
            mKindSectionDataMap.put(mimeType, kindSectionData);
            mSortedMimetypes.add(mimeType);

            vlog("parse: " + i + " " + dataKind.mimeType + " " +
                    kindSectionData.getValuesDeltas().size() + " value(s) " +
                    kindSectionData.getNonEmptyValuesDeltas().size() + " non-empty value(s) " +
                    kindSectionData.getVisibleValuesDeltas().size() +
                    " visible value(s)");
        }
    }

    private void addReadOnlyRawContactEditorViews() {
        mKindSectionViews.removeAllViews();
        addAccountInfo();
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(
                getContext());
        final AccountType type = mCurrentRawContactDelta.getAccountType(accountTypes);

        // Bail if invalid state or source
        if (type == null) return;

        // Make sure we have StructuredName
        RawContactModifier.ensureKindExists(
                mCurrentRawContactDelta, type, StructuredName.CONTENT_ITEM_TYPE);

        ValuesDelta primary;

        // Name
        final Context context = getContext();
        final Resources res = context.getResources();
        primary = mCurrentRawContactDelta.getPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
        final String name = primary != null ? primary.getAsString(StructuredName.DISPLAY_NAME) :
            getContext().getString(R.string.missing_name);
        final Drawable nameDrawable = context.getDrawable(R.drawable.ic_person_24dp);
        final String nameContentDescription = res.getString(R.string.header_name_entry);
        bindData(nameDrawable, nameContentDescription, name, /* type */ null,
                /* isFirstEntry */ true);

        // Phones
        final ArrayList<ValuesDelta> phones = mCurrentRawContactDelta
                .getMimeEntries(Phone.CONTENT_ITEM_TYPE);
        final Drawable phoneDrawable = context.getDrawable(R.drawable.ic_phone_24dp);
        final String phoneContentDescription = res.getString(R.string.header_phone_entry);
        if (phones != null) {
            boolean isFirstPhoneBound = true;
            for (ValuesDelta phone : phones) {
                final String phoneNumber = phone.getPhoneNumber();
                if (TextUtils.isEmpty(phoneNumber)) {
                    continue;
                }
                final String formattedNumber = PhoneNumberUtilsCompat.formatNumber(
                        phoneNumber, phone.getPhoneNormalizedNumber(),
                        GeoUtil.getCurrentCountryIso(getContext()));
                CharSequence phoneType = null;
                if (phone.hasPhoneType()) {
                    phoneType = Phone.getTypeLabel(
                            res, phone.getPhoneType(), phone.getPhoneLabel());
                }
                bindData(phoneDrawable, phoneContentDescription, formattedNumber, phoneType,
                        isFirstPhoneBound, true);
                isFirstPhoneBound = false;
            }
        }

        // Emails
        final ArrayList<ValuesDelta> emails = mCurrentRawContactDelta
                .getMimeEntries(Email.CONTENT_ITEM_TYPE);
        final Drawable emailDrawable = context.getDrawable(R.drawable.ic_email_24dp);
        final String emailContentDescription = res.getString(R.string.header_email_entry);
        if (emails != null) {
            boolean isFirstEmailBound = true;
            for (ValuesDelta email : emails) {
                final String emailAddress = email.getEmailData();
                if (TextUtils.isEmpty(emailAddress)) {
                    continue;
                }
                CharSequence emailType = null;
                if (email.hasEmailType()) {
                    emailType = Email.getTypeLabel(
                            res, email.getEmailType(), email.getEmailLabel());
                }
                bindData(emailDrawable, emailContentDescription, emailAddress, emailType,
                        isFirstEmailBound);
                isFirstEmailBound = false;
            }
        }

        mKindSectionViews.setVisibility(mKindSectionViews.getChildCount() > 0 ? VISIBLE : GONE);
        // Hide the "More fields" link
        mMoreFields.setVisibility(GONE);
    }

    private void bindData(Drawable icon, String iconContentDescription, CharSequence data,
            CharSequence type, boolean isFirstEntry) {
        bindData(icon, iconContentDescription, data, type, isFirstEntry, false);
    }

    private void bindData(Drawable icon, String iconContentDescription, CharSequence data,
            CharSequence type, boolean isFirstEntry, boolean forceLTR) {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final View field = inflater.inflate(R.layout.item_read_only_field, mKindSectionViews,
                false);
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
        mKindSectionViews.addView(field);
    }

    private void addAccountInfo() {
        mAccountHeaderContainer.setVisibility(View.GONE);

        final AccountDisplayInfo account =
                mAccountDisplayInfoFactory.getAccountDisplayInfoFor(mCurrentRawContactDelta);

        // Get the account information for the primary raw contact delta
        final String accountLabel = mIsUserProfile
                ? EditorUiUtils.getAccountHeaderLabelForMyProfile(getContext(), account)
                : account.getNameLabel().toString();

        // Either the account header or selector should be shown, not both.
        final List<AccountWithDataSet> accounts =
                AccountTypeManager.getInstance(getContext()).getAccounts(true);

        if (mHasNewContact && !mIsUserProfile) {
            if (accounts.size() > 1) {
                addAccountSelector(mCurrentRawContactDelta, accountLabel);
            } else {
                addAccountHeader(accountLabel);
            }
        } else {
            // The raw contact selector should only display linked raw contacts that can be edited
            // in the full editor (i.e. they are not newly created raw contacts)
            final RawContactAccountListAdapter adapter =  new RawContactAccountListAdapter(
                    getContext(), getRawContactDeltaListForSelector(mRawContactDeltas));
            if (adapter.getCount() > 0 && !mIsEditingReadOnlyRawContactWithNewContact) {
                addRawContactAccountSelector(accountLabel, adapter);
            } else {
                addAccountHeader(accountLabel);
            }
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

    private void addAccountHeader(String accountLabel) {
        mAccountHeaderContainer.setVisibility(View.VISIBLE);

        // Set the account name
        mAccountHeaderName.setVisibility(View.VISIBLE);
        mAccountHeaderName.setText(accountLabel);

        // Set the account type
        final String selectorTitle = getResources().getString(isReadOnlyRawContact() ?
                R.string.compact_editor_account_selector_read_only_title :
                R.string.compact_editor_account_selector_title);
        mAccountHeaderType.setText(selectorTitle);

        // Set the icon
        final AccountType accountType =
                mCurrentRawContactDelta.getRawContactAccountType(getContext());
        mAccountHeaderIcon.setImageDrawable(accountType.getDisplayIcon(getContext()));

        // Set the content description
        mAccountHeaderContainer.setContentDescription(
                EditorUiUtils.getAccountInfoContentDescription(accountLabel,
                        selectorTitle));
    }

    private void addAccountSelector(final RawContactDelta rawContactDelta, CharSequence nameLabel) {
        final View.OnClickListener onClickListener = new View.OnClickListener() {
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
                            mIsExpanded = false;
                            mListener.onRebindEditorsForNewContact(
                                    rawContactDelta,
                                    mPrimaryAccount,
                                    newAccount);
                        }
                    }
                });
                popup.show();
            }
        };
        setUpAccountSelector(nameLabel.toString(), onClickListener);
    }

    private void addRawContactAccountSelector(String nameLabel,
            final RawContactAccountListAdapter adapter) {
        final View.OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                final ListPopupWindow popup = new ListPopupWindow(getContext(), null);
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
                        final long rawContactId = adapter.getItemId(position);
                        // Only switch if it's actually a different raw contact.
                        if (rawContactId != mCurrentRawContactDelta.getRawContactId()
                                && mListener != null) {
                            final RawContactDelta rawContactDelta = adapter.getItem(position);
                            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(
                                    getContext());
                            final AccountType accountType = rawContactDelta.getAccountType(
                                    accountTypes);
                            final boolean isReadOnly = !accountType.areContactsWritable();
                            // Reset state.
                            mIsExpanded = false;
                            mListener.onRawContactSelected(rawContactId, isReadOnly);
                        }
                    }
                });
                popup.show();
            }
        };
        setUpAccountSelector(nameLabel, onClickListener);
    }

    private void setUpAccountSelector(String nameLabel, OnClickListener listener) {
        addAccountHeader(nameLabel);
        // Add handlers for choosing another account to save to.
        mAccountHeaderExpanderIcon.setVisibility(View.VISIBLE);
        mAccountHeaderContainer.setOnClickListener(listener);
    }

    private void addPhotoView() {
        if (!mCurrentRawContactDelta.hasMimeEntries(Photo.CONTENT_ITEM_TYPE)) {
            wlog("No photo mimetype for this raw contact.");
            mPhotoView.setVisibility(GONE);
            return;
        } else {
            mPhotoView.setVisibility(VISIBLE);
        }

        final ValuesDelta superPrimaryDelta = mCurrentRawContactDelta
                .getSuperPrimaryEntry(Photo.CONTENT_ITEM_TYPE);
        if (superPrimaryDelta == null) {
            Log.wtf(TAG, "addPhotoView: no ValueDelta found for current RawContactDelta"
                    + "that supports a photo.");
            mPhotoView.setVisibility(GONE);
            return;
        }
        // Set the photo view
        mPhotoView.setPhoto(superPrimaryDelta, mMaterialPalette);

        if (isReadOnlyRawContact()) {
            mPhotoView.setReadOnly(true);
            return;
        }
        mPhotoView.setReadOnly(false);
        mPhotoValuesDelta = superPrimaryDelta;
    }

    private void addKindSectionViews() {
        int i = -1;

        for (String mimeType : mSortedMimetypes) {
            i++;
            // Ignore mime types that we've already handled
            if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                vlog("kind: " + i + " " + mimeType + " dropped");
                continue;
            }
            final CompactKindSectionView kindSectionView;
            final KindSectionData kindSectionData = mKindSectionDataMap.get(mimeType);
            final ValuesDelta primaryDelta = mCurrentRawContactDelta.getPrimaryEntry(mimeType);
            kindSectionView = inflateKindSectionView(mKindSectionViews, kindSectionData, mimeType,
                    primaryDelta);
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
