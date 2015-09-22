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
import com.android.contacts.editor.CompactContactEditorFragment.PhotoHandler;
import com.android.contacts.util.UiClosables;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
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
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * View to display information from multiple {@link RawContactDelta}s grouped together.
 */
public class CompactRawContactsEditorView extends LinearLayout implements View.OnClickListener {

    private static final String TAG = "CompactEditorView";

    private static final MimeTypeComparator MIME_TYPE_COMPARATOR = new MimeTypeComparator();

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
    }

    /** Used to sort kind sections. */
    private static final class MimeTypeComparator implements
            Comparator<Map.Entry<String,List<KindSectionData>>> {

        // Order of kinds roughly matches quick contacts; we diverge in the following ways:
        // 1) all names are together at the top (in quick contacts the nickname and phonetic name
        //    are in the about card)
        // 2) IM is moved up after addresses
        // 3) SIP addresses are moved to below phone numbers
        private static final List<String> MIME_TYPE_ORDER = Arrays.asList(new String[] {
                StructuredName.CONTENT_ITEM_TYPE,
                DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME,
                DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME,
                Nickname.CONTENT_ITEM_TYPE,
                Phone.CONTENT_ITEM_TYPE,
                SipAddress.CONTENT_ITEM_TYPE,
                Email.CONTENT_ITEM_TYPE,
                StructuredPostal.CONTENT_ITEM_TYPE,
                Im.CONTENT_ITEM_TYPE,
                Website.CONTENT_ITEM_TYPE,
                Organization.CONTENT_ITEM_TYPE,
                Event.CONTENT_ITEM_TYPE,
                Relation.CONTENT_ITEM_TYPE,
                Note.CONTENT_ITEM_TYPE
        });

        @Override
        public int compare(Map.Entry<String, List<KindSectionData>> entry1,
                Map.Entry<String, List<KindSectionData>> entry2) {
            if (entry1 == entry2) return 0;
            if (entry1 == null) return -1;
            if (entry2 == null) return 1;

            final String mimeType1 = entry1.getKey();
            final String mimeType2 = entry2.getKey();

            int index1 = MIME_TYPE_ORDER.indexOf(mimeType1);
            int index2 = MIME_TYPE_ORDER.indexOf(mimeType2);

            // Fallback to alphabetical ordering of the mime type if both are not found
            if (index1 < 0 && index2 < 0) return mimeType1.compareTo(mimeType2);
            if (index1 < 0) return 1;
            if (index2 < 0) return -1;

            return index1 < index2 ? -1 : 1;
        }
    }

    private CompactRawContactsEditorView.Listener mListener;

    private AccountTypeManager mAccountTypeManager;
    private LayoutInflater mLayoutInflater;

    private ViewIdGenerator mViewIdGenerator;
    private MaterialColorMapUtils.MaterialPalette mMaterialPalette;
    private long mPhotoId;
    private boolean mHasNewContact;
    private boolean mIsUserProfile;
    private AccountWithDataSet mPrimaryAccount;
    private RawContactDelta mPrimaryRawContactDelta;
    private Map<String,List<KindSectionData>> mKindSectionDataMap = new HashMap<>();

    // Account header
    private View mAccountHeaderContainer;
    private TextView mAccountHeaderType;
    private TextView mAccountHeaderName;

    // Account selector
    private View mAccountSelectorContainer;
    private View mAccountSelector;
    private TextView mAccountSelectorType;
    private TextView mAccountSelectorName;

    private CompactPhotoEditorView mPhotoView;
    private ViewGroup mKindSectionViews;
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

        // Account header
        mAccountHeaderContainer = findViewById(R.id.account_container);
        mAccountHeaderType = (TextView) findViewById(R.id.account_type);
        mAccountHeaderName = (TextView) findViewById(R.id.account_name);

        // Account selector
        mAccountSelectorContainer = findViewById(R.id.account_selector_container);
        mAccountSelector = findViewById(R.id.account);
        mAccountSelectorType = (TextView) findViewById(R.id.account_type_selector);
        mAccountSelectorName = (TextView) findViewById(R.id.account_name_selector);

        mPhotoView = (CompactPhotoEditorView) findViewById(R.id.photo_editor);
        mKindSectionViews = (LinearLayout) findViewById(R.id.kind_section_views);
        mMoreFields = findViewById(R.id.more_fields);
        mMoreFields.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.more_fields) {
            // Stop hiding empty editors and allow the user to enter values for all kinds now
            for (int i = 0; i < mKindSectionViews.getChildCount(); i++) {
                final CompactKindSectionView kindSectionView =
                        (CompactKindSectionView) mKindSectionViews.getChildAt(i);
                kindSectionView.setHideWhenEmpty(false);
                // Prevent the user from adding new names
                if (!StructuredName.CONTENT_ITEM_TYPE.equals(kindSectionView.getMimeType())) {
                    kindSectionView.setShowOneEmptyEditor(true);
                }
                kindSectionView.updateEmptyEditors(/* shouldAnimate =*/ false);
            }

            updateMoreFieldsButton();
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

    /**
     * Pass through to {@link CompactPhotoEditorView#setPhotoHandler}.
     */
    public void setPhotoHandler(PhotoHandler photoHandler) {
        mPhotoView.setPhotoHandler(photoHandler);
    }

    /**
     * Pass through to {@link CompactPhotoEditorView#setPhoto}.
     */
    public void setPhoto(Bitmap bitmap) {
        mPhotoView.setPhoto(bitmap);
    }

    /**
     * Pass through to {@link CompactPhotoEditorView#setFullSizedPhoto(Uri)}.
     */
    public void setFullSizePhoto(Uri photoUri) {
        mPhotoView.setFullSizedPhoto(photoUri);
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

    public View getAggregationAnchorView() {
        // TODO: since there may be more than one structured name now we should return the one
        // being edited instead of just the first one.
        for (int i = 0; i < mKindSectionViews.getChildCount(); i++) {
            final CompactKindSectionView kindSectionView =
                    (CompactKindSectionView) mKindSectionViews.getChildAt(i);
            if (!StructuredName.CONTENT_ITEM_TYPE.equals(kindSectionView.getMimeType())) {
                return kindSectionView.findViewById(R.id.anchor_view);
            }
        }
        return null;
    }

    public void setState(RawContactDeltaList rawContactDeltas,
            MaterialColorMapUtils.MaterialPalette materialPalette, ViewIdGenerator viewIdGenerator,
            long photoId, boolean hasNewContact, boolean isUserProfile,
            AccountWithDataSet primaryAccount) {
        // Clear previous state and reset views
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
            return;
        }
        vlog("state: setting state from " + rawContactDeltas.size() + " RawContactDelta(s)");
        parseRawContactDeltas(rawContactDeltas);
        if (mKindSectionDataMap == null || mKindSectionDataMap.isEmpty()) {
            elog("No kind section data parsed from RawContactDelta(s)");
            return;
        }

        // Setup the view
        setId(mViewIdGenerator.getId(rawContactDeltas.get(0), /* dataKind =*/ null,
                /* valuesDelta =*/ null, ViewIdGenerator.NO_VIEW_INDEX));
        addAccountInfo();
        addPhotoView();
        addKindSectionViews();
        updateMoreFieldsButton();
    }

    private void parseRawContactDeltas(RawContactDeltaList rawContactDeltas) {
        if (mPrimaryAccount != null) {
            // Use the first writable contact that matches the primary account
            for (RawContactDelta rawContactDelta : rawContactDeltas) {
                if (!rawContactDelta.isVisible()) continue;
                final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
                if (accountType == null || !accountType.areContactsWritable()) continue;
                if (matchesPrimaryAccount(rawContactDelta)) {
                    vlog("parse: matched primary account raw contact");
                    mPrimaryRawContactDelta = rawContactDelta;
                    break;
                }
            }
        }
        if (mPrimaryRawContactDelta == null) {
            // Fall back to the first writable raw contact
            for (RawContactDelta rawContactDelta : rawContactDeltas) {
                if (!rawContactDelta.isVisible()) continue;
                final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
                if (accountType != null && accountType.areContactsWritable()) {
                    vlog("parse: falling back to the first writable raw contact as primary");
                    mPrimaryRawContactDelta = rawContactDelta;
                    break;
                }
            }
        }
        if (mPrimaryRawContactDelta != null) {
            RawContactModifier.ensureKindExists(mPrimaryRawContactDelta,
                    mPrimaryRawContactDelta.getAccountType(mAccountTypeManager),
                    StructuredName.CONTENT_ITEM_TYPE);

            RawContactModifier.ensureKindExists(mPrimaryRawContactDelta,
                    mPrimaryRawContactDelta.getAccountType(mAccountTypeManager),
                    Photo.CONTENT_ITEM_TYPE);
        }

        // Build the kind section data list map
        for (RawContactDelta rawContactDelta : rawContactDeltas) {
            if (rawContactDelta == null || !rawContactDelta.isVisible()) continue;
            vlog("parse: " + rawContactDelta);
            final AccountType accountType = rawContactDelta.getAccountType(mAccountTypeManager);
            if (accountType == null) continue;
            final List<DataKind> dataKinds = accountType.getSortedDataKinds();
            final int dataKindSize = dataKinds == null ? 0 : dataKinds.size();
            vlog("parse: " + dataKindSize + " dataKinds(s)");
            for (int i = 0; i < dataKindSize; i++) {
                final DataKind dataKind = dataKinds.get(i);
                if (dataKind == null || !dataKind.editable) continue;
                final List<KindSectionData> kindSectionDataList =
                        getKindSectionDataList(dataKind.mimeType);
                final KindSectionData kindSectionData =
                        new KindSectionData(accountType, dataKind, rawContactDelta);
                kindSectionDataList.add(kindSectionData);
                vlog("parse: " + i + " " + dataKind.mimeType + " " +
                        (kindSectionData.hasValuesDeltas()
                                ? kindSectionData.getValuesDeltas().size() : 0) + " value(s)");
            }
        }
    }

    private List<KindSectionData> getKindSectionDataList(String mimeType) {
        List<KindSectionData> kindSectionDataList = mKindSectionDataMap.get(mimeType);
        if (kindSectionDataList == null) {
            kindSectionDataList = new ArrayList<>();
            mKindSectionDataMap.put(mimeType, kindSectionDataList);
        }
        return kindSectionDataList;
    }

    /** Whether the given RawContactDelta is from the primary account. */
    private boolean matchesPrimaryAccount(RawContactDelta rawContactDelta) {
        return Objects.equals(mPrimaryAccount.name, rawContactDelta.getAccountName())
                && Objects.equals(mPrimaryAccount.type, rawContactDelta.getAccountType())
                && Objects.equals(mPrimaryAccount.dataSet, rawContactDelta.getDataSet());
    }

    private void addAccountInfo() {
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
        if (mHasNewContact && !mIsUserProfile && accounts.size() > 1) {
            mAccountHeaderContainer.setVisibility(View.GONE);
            addAccountSelector(accountInfo);
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

    private void addPhotoView() {
        // Get the kind section data and values delta that will back the photo view
        Pair<KindSectionData,ValuesDelta> pair = getPrimaryKindSectionData(mPhotoId);
        if (pair == null) {
            wlog("photo: no kind section data parsed");
            return;
        }
        final KindSectionData kindSectionData = pair.first;
        final ValuesDelta valuesDelta = pair.second;

        // If we're editing a read-only contact we want to display the photo from the
        // read-only contact in a photo editor backed by the new raw contact
        // that was created. The new raw contact is the first writable one.
        if (mHasNewContact) {
            mPhotoRawContactId = mPrimaryRawContactDelta == null
                    ? null : mPrimaryRawContactDelta.getRawContactId();
        }

        mPhotoRawContactId = kindSectionData.getRawContactDelta().getRawContactId();
        mPhotoView.setValues(kindSectionData.getDataKind(), valuesDelta,
                kindSectionData.getRawContactDelta(),
                !kindSectionData.getAccountType().areContactsWritable(), mMaterialPalette,
                mViewIdGenerator);
    }

    private Pair<KindSectionData,ValuesDelta> getPrimaryKindSectionData(long id) {
        final String mimeType = Photo.CONTENT_ITEM_TYPE;
        final List<KindSectionData> kindSectionDataList = mKindSectionDataMap.get(mimeType);
        if (kindSectionDataList == null || kindSectionDataList.isEmpty()) {
            wlog("photo: no kind section data parsed");
            return null;
        }

        KindSectionData resultKindSectionData = null;
        ValuesDelta resultValuesDelta = null;
        if (id > 0) {
            // Look for a match for the ID that was passed in
            for (KindSectionData kindSectionData : kindSectionDataList) {
                resultValuesDelta = kindSectionData.getValuesDeltaById(id);
                if (resultValuesDelta != null) {
                    vlog("photo: matched kind section data by ID");
                    resultKindSectionData = kindSectionData;
                    break;
                }
            }
        }
        if (resultKindSectionData == null) {
            // Look for a super primary photo
            for (KindSectionData kindSectionData : kindSectionDataList) {
                resultValuesDelta = kindSectionData.getSuperPrimaryValuesDelta();
                if (resultValuesDelta != null) {
                    wlog("photo: matched super primary kind section data");
                    resultKindSectionData = kindSectionData;
                    break;
                }
            }
        }
        if (resultKindSectionData == null) {
            // Fall back to the first non-empty value
            for (KindSectionData kindSectionData : kindSectionDataList) {
                resultValuesDelta = kindSectionData.getFirstNonEmptyValuesDelta();
                if (resultValuesDelta != null) {
                    vlog("photo: using first non empty value");
                    resultKindSectionData = kindSectionData;
                    break;
                }
            }
        }
        if (resultKindSectionData == null || resultValuesDelta == null) {
            final List<ValuesDelta> valuesDeltaList = kindSectionDataList.get(0).getValuesDeltas();
            if (valuesDeltaList != null && !valuesDeltaList.isEmpty()) {
                vlog("photo: falling back to first empty entry");
                resultValuesDelta = valuesDeltaList.get(0);
                resultKindSectionData = kindSectionDataList.get(0);
            }
        }
        return resultKindSectionData != null && resultValuesDelta != null
                ? new Pair<>(resultKindSectionData, resultValuesDelta) : null;
    }

    private void addKindSectionViews() {
        // Sort the kinds
        final TreeSet<Map.Entry<String,List<KindSectionData>>> entries =
                new TreeSet<>(MIME_TYPE_COMPARATOR);
        entries.addAll(mKindSectionDataMap.entrySet());

        vlog("kind: " + entries.size() + " kindSection(s)");
        int i = -1;
        for (Map.Entry<String, List<KindSectionData>> entry : entries) {
            i++;

            final String mimeType = entry.getKey();
            final List<KindSectionData> kindSectionDataList = entry.getValue();

            // Ignore mime types that we've already handled
            if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                vlog("kind: " + i + " " + mimeType + " dropped");
                continue;
            }

            // Ignore mime types that we don't handle
            if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)
                    || DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME.equals(mimeType)) {
                vlog("kind: " + i + " " + mimeType + " dropped");
                continue;
            }

            if (kindSectionDataList != null) {
                vlog("kind: " + i + " " + mimeType + ": " + kindSectionDataList.size() +
                        " kindSectionData(s)");

                final CompactKindSectionView kindSectionView = inflateKindSectionView(
                        mKindSectionViews, kindSectionDataList, mimeType);
                mKindSectionViews.addView(kindSectionView);
            }
        }
    }

    private CompactKindSectionView inflateKindSectionView(ViewGroup viewGroup,
            List<KindSectionData> kindSectionDataList, String mimeType) {
        final CompactKindSectionView kindSectionView = (CompactKindSectionView)
                mLayoutInflater.inflate(R.layout.compact_item_kind_section, viewGroup,
                        /* attachToRoot =*/ false);

        if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)
                || Phone.CONTENT_ITEM_TYPE.equals(mimeType)
                || Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            // Names, phone numbers, and email addresses are always displayed.
            // Phone numbers and email addresses are the only types you add new values
            // to initially.
            kindSectionView.setHideWhenEmpty(false);
        }

        // Prevent the user from adding any new names
        if (!StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
            kindSectionView.setShowOneEmptyEditor(true);
        }

        kindSectionView.setState(kindSectionDataList, /* readOnly =*/ false, mViewIdGenerator,
                mListener);

        return kindSectionView;
    }

    private void updateMoreFieldsButton() {
        // If any kind section views are hidden then show the link
        for (int i = 0; i < mKindSectionViews.getChildCount(); i++) {
            final CompactKindSectionView kindSectionView =
                    (CompactKindSectionView) mKindSectionViews.getChildAt(i);
            if (kindSectionView.getVisibility() == View.GONE) {
                // Show the more fields button
                mMoreFields.setVisibility(View.VISIBLE);
                return;
            }
        }
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
