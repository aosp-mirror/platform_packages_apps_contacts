/*
 * Copyright (C) 2010 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.detail;

import com.android.contacts.Collapser;
import com.android.contacts.Collapser.Collapsible;
import com.android.contacts.ContactLoader;
import com.android.contacts.ContactOptionsActivity;
import com.android.contacts.ContactPresenceIconUtil;
import com.android.contacts.ContactsUtils;
import com.android.contacts.GroupMetaData;
import com.android.contacts.R;
import com.android.contacts.TypePrecedence;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountType.EditType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.DataKind;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DataStatus;
import com.android.contacts.util.DateUtils;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.contacts.widget.TransitionAnimationView;
import com.android.internal.telephony.ITelephony;

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ParseException;
import android.net.Uri;
import android.net.WebAddress;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.StatusUpdates;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactDetailFragment extends Fragment implements
        OnItemClickListener, OnItemLongClickListener, SelectAccountDialogFragment.Listener {

    private static final String TAG = "ContactDetailFragment";

    private static final int LOADER_DETAILS = 1;

    private static final String KEY_CONTACT_URI = "contactUri";
    private static final String LOADER_ARG_CONTACT_URI = "contactUri";

    private Context mContext;
    private View mView;
    private Uri mLookupUri;
    private Listener mListener;

    private ContactLoader.Result mContactData;
    private ContactDetailHeaderView mHeaderView;
    private ListView mListView;
    private ViewAdapter mAdapter;
    private Uri mPrimaryPhoneUri = null;

    private Button mCopyGalToLocalButton;
    private boolean mAllRestricted;
    private final ArrayList<Long> mWritableRawContactIds = new ArrayList<Long>();
    private int mNumPhoneNumbers = 0;
    private String mDefaultCountryIso;
    private boolean mContactDataDisplayed;

    private boolean mOptionsMenuOptions;
    private boolean mOptionsMenuEditable;
    private boolean mOptionsMenuShareable;

    /**
     * Device capability: Set during buildEntries and used in the long-press context menu
     */
    private boolean mHasPhone;

    /**
     * Device capability: Set during buildEntries and used in the long-press context menu
     */
    private boolean mHasSms;

    /**
     * Device capability: Set during buildEntries and used in the long-press context menu
     */
    private boolean mHasSip;

    /**
     * The view shown if the detail list is empty.
     * We set this to the list view when first bind the adapter, so that it won't be shown while
     * we're loading data.
     */
    private View mEmptyView;

    /**
     * A list of distinct contact IDs included in the current contact.
     */
    private ArrayList<Long> mRawContactIds = new ArrayList<Long>();
    private ArrayList<DetailViewEntry> mPhoneEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mSmsEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mEmailEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mPostalEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mImEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mNicknameEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mGroupEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mRelationEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mNoteEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mWebsiteEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mSipEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mEventEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mOtherEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<ViewEntry> mAllEntries = new ArrayList<ViewEntry>();
    private LayoutInflater mInflater;

    private boolean mTransitionAnimationRequested;

    public ContactDetailFragment() {
        // Explicit constructor for inflation
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLookupUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CONTACT_URI, mLookupUri);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mDefaultCountryIso = ContactsUtils.getCurrentCountryIso(mContext);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mView = inflater.inflate(R.layout.contact_detail_fragment, container, false);

        setHasOptionsMenu(true);

        mInflater = inflater;

        mListView = (ListView) mView.findViewById(android.R.id.list);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);

        // Don't set it to mListView yet.  We do so later when we bind the adapter.
        mEmptyView = mView.findViewById(android.R.id.empty);

        mCopyGalToLocalButton = (Button) mView.findViewById(R.id.copyLocal);
        mCopyGalToLocalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                makePersonalCopy();
            }
        });

        mView.setVisibility(View.INVISIBLE);
        return mView;
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public Uri getUri() {
        return mLookupUri;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mLookupUri != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().initLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    public void loadUri(Uri lookupUri) {
        if ((lookupUri != null && lookupUri.equals(mLookupUri))
                || (lookupUri == null && mLookupUri == null)) {
            return;
        }

        mLookupUri = lookupUri;
        mTransitionAnimationRequested = mContactDataDisplayed;
        mContactDataDisplayed = true;
        if (mLookupUri == null) {
            getLoaderManager().destroyLoader(LOADER_DETAILS);
            mContactData = null;
            bindData();
        } else if (getActivity() != null) {
            Bundle args = new Bundle();
            args.putParcelable(LOADER_ARG_CONTACT_URI, mLookupUri);
            getLoaderManager().restartLoader(LOADER_DETAILS, args, mDetailLoaderListener);
        }
    }

    private void bindData() {
        if (mView == null) {
            return;
        }

        if (isAdded()) {
            getActivity().invalidateOptionsMenu();
        }

        if (mTransitionAnimationRequested) {
            TransitionAnimationView.startAnimation(mView, mContactData == null);
            mTransitionAnimationRequested = false;
        }

        if (mContactData == null) {
            mView.setVisibility(View.INVISIBLE);
            return;
        }

        // Clear old header
        mHeaderView = null;

        // Build up the contact entries
        buildEntries();

        // Collapse similar data items for select {@link DataKind}s.
        Collapser.collapseList(mPhoneEntries);
        Collapser.collapseList(mSmsEntries);
        Collapser.collapseList(mEmailEntries);
        Collapser.collapseList(mPostalEntries);
        Collapser.collapseList(mImEntries);

        // Make one aggregated list of all entries for display to the user.
        flattenAllLists();

        if (mAdapter == null) {
            mAdapter = new ViewAdapter();
            mListView.setAdapter(mAdapter);
        } else {
            mAdapter.notifyDataSetChanged();
        }
        mListView.setEmptyView(mEmptyView);

        // Configure copy gal button
        if (mContactData.isDirectoryEntry()) {
            final int exportSupport = mContactData.getDirectoryExportSupport();
            if (exportSupport == Directory.EXPORT_SUPPORT_ANY_ACCOUNT
                    || exportSupport == Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY) {
                mCopyGalToLocalButton.setVisibility(View.VISIBLE);
            } else {
                mCopyGalToLocalButton.setVisibility(View.GONE);
            }
        } else {
            mCopyGalToLocalButton.setVisibility(View.GONE);
        }

        mView.setVisibility(View.VISIBLE);
    }

    /**
     * Build up the entries to display on the screen.
     */
    private final void buildEntries() {
        mHasPhone = PhoneCapabilityTester.isPhone(mContext);
        mHasSms = PhoneCapabilityTester.isSmsIntentRegistered(mContext);
        mHasSip = PhoneCapabilityTester.isSipPhone(mContext);

        // Clear out the old entries
        mAllEntries.clear();

        mRawContactIds.clear();

        mAllRestricted = true;
        mPrimaryPhoneUri = null;
        mNumPhoneNumbers = 0;

        mWritableRawContactIds.clear();

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);

        // Build up method entries
        if (mContactData == null) {
            return;
        }

        ArrayList<String> groups = new ArrayList<String>();
        for (Entity entity: mContactData.getEntities()) {
            final ContentValues entValues = entity.getEntityValues();
            final String accountType = entValues.getAsString(RawContacts.ACCOUNT_TYPE);
            final long rawContactId = entValues.getAsLong(RawContacts._ID);

            // Mark when this contact has any unrestricted components
            Integer restricted = entValues.getAsInteger(RawContacts.IS_RESTRICTED);
            final boolean isRestricted = restricted != null && restricted != 0;
            if (!isRestricted) mAllRestricted = false;

            if (!mRawContactIds.contains(rawContactId)) {
                mRawContactIds.add(rawContactId);
            }
            AccountType type = accountTypes.getAccountType(accountType);
            if (type == null || !type.readOnly) {
                mWritableRawContactIds.add(rawContactId);
            }

            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                entryValues.put(Data.RAW_CONTACT_ID, rawContactId);

                final long dataId = entryValues.getAsLong(Data._ID);
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);
                if (mimeType == null) continue;

                if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    Long groupId = entryValues.getAsLong(GroupMembership.GROUP_ROW_ID);
                    if (groupId != null) {
                        handleGroupMembership(groups, mContactData.getGroupMetaData(), groupId);
                    }
                    continue;
                }

                final DataKind kind = accountTypes.getKindOrFallback(
                        accountType, mimeType);
                if (kind == null) continue;

                final DetailViewEntry entry = DetailViewEntry.fromValues(mContext, mimeType, kind,
                        dataId, entryValues, mContactData.isDirectoryEntry(),
                        mContactData.getDirectoryId());

                final boolean hasData = !TextUtils.isEmpty(entry.data);
                Integer superPrimary = entryValues.getAsInteger(Data.IS_SUPER_PRIMARY);
                final boolean isSuperPrimary = superPrimary != null && superPrimary != 0;

                if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Always ignore the name. It is shown in the header if set
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build phone entries
                    mNumPhoneNumbers++;
                    String phoneNumberE164 =
                            entryValues.getAsString(PhoneLookup.NORMALIZED_NUMBER);
                    entry.data = PhoneNumberUtils.formatNumber(
                            entry.data, phoneNumberE164, mDefaultCountryIso);
                    final Intent phoneIntent = mHasPhone ? new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts(Constants.SCHEME_TEL, entry.data, null)) : null;
                    final Intent smsIntent = mHasSms ? new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts(Constants.SCHEME_SMSTO, entry.data, null)) : null;

                    // Configure Icons and Intents. Notice actionIcon is already set to the phone
                    if (mHasPhone && mHasSms) {
                        entry.intent = phoneIntent;
                        entry.secondaryIntent = smsIntent;
                        entry.secondaryActionIcon = kind.iconAltRes;
                    } else if (mHasPhone) {
                        entry.intent = phoneIntent;
                    } else if (mHasSms) {
                        entry.intent = smsIntent;
                        entry.actionIcon = kind.iconAltRes;
                    } else {
                        entry.intent = null;
                        entry.actionIcon = -1;
                    }

                    // Remember super-primary phone
                    if (isSuperPrimary) mPrimaryPhoneUri = entry.uri;

                    entry.isPrimary = isSuperPrimary;
                    mPhoneEntries.add(entry);
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build email entries
                    entry.intent = new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts(Constants.SCHEME_MAILTO, entry.data, null));
                    entry.isPrimary = isSuperPrimary;
                    mEmailEntries.add(entry);

                    // When Email rows have status, create additional Im row
                    final DataStatus status = mContactData.getStatuses().get(entry.id);
                    if (status != null) {
                        final String imMime = Im.CONTENT_ITEM_TYPE;
                        final DataKind imKind = accountTypes.getKindOrFallback(accountType,
                                imMime);
                        final DetailViewEntry imEntry = DetailViewEntry.fromValues(mContext, imMime,
                                imKind, dataId, entryValues, mContactData.isDirectoryEntry(),
                                mContactData.getDirectoryId());
                        buildImActions(imEntry, entryValues);
                        imEntry.applyStatus(status, false);
                        mImEntries.add(imEntry);
                    }
                } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build postal entries
                    entry.maxLines = 4;
                    entry.intent = new Intent(
                            Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(entry.data)));
                    mPostalEntries.add(entry);
                } else if (Im.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build IM entries
                    buildImActions(entry, entryValues);

                    // Apply presence and status details when available
                    final DataStatus status = mContactData.getStatuses().get(entry.id);
                    if (status != null) {
                        entry.applyStatus(status, false);
                    }
                    mImEntries.add(entry);
                } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Organizations are not shown. The first one is shown in the header
                    // and subsequent ones are not supported anymore
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build nickname entries
                    final boolean isNameRawContact =
                        (mContactData.getNameRawContactId() == rawContactId);

                    final boolean duplicatesTitle =
                        isNameRawContact
                        && mContactData.getDisplayNameSource() == DisplayNameSources.NICKNAME;

                    if (!duplicatesTitle) {
                        entry.uri = null;
                        mNicknameEntries.add(entry);
                    }
                } else if (Note.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build note entries
                    entry.uri = null;
                    entry.maxLines = 100;
                    mNoteEntries.add(entry);
                } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build Website entries
                    entry.uri = null;
                    entry.maxLines = 10;
                    try {
                        WebAddress webAddress = new WebAddress(entry.data);
                        entry.intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(webAddress.toString()));
                    } catch (ParseException e) {
                        Log.e(TAG, "Couldn't parse website: " + entry.data);
                    }
                    mWebsiteEntries.add(entry);
                } else if (SipAddress.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build SipAddress entries
                    entry.uri = null;
                    entry.maxLines = 1;
                    if (mHasSip) {
                        entry.intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                Uri.fromParts(Constants.SCHEME_SIP, entry.data, null));
                    } else {
                        entry.intent = null;
                        entry.actionIcon = -1;
                    }
                    mSipEntries.add(entry);
                    // TODO: Now that SipAddress is in its own list of entries
                    // (instead of grouped in mOtherEntries), consider
                    // repositioning it right under the phone number.
                    // (Then, we'd also update FallbackAccountType.java to set
                    // secondary=false for this field, and tweak the weight
                    // of its DataKind.)
                } else if (Event.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    entry.data = DateUtils.formatDate(mContext, entry.data);
                    entry.uri = null;
                    mEventEntries.add(entry);
                } else if (Relation.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    entry.intent = new Intent(Intent.ACTION_SEARCH);
                    entry.intent.putExtra(SearchManager.QUERY, entry.data);
                    entry.intent.setType(Contacts.CONTENT_TYPE);
                    mRelationEntries.add(entry);
                } else {
                    // Handle showing custom rows
                    entry.intent = new Intent(Intent.ACTION_VIEW);
                    entry.intent.setDataAndType(entry.uri, entry.mimetype);

                    // Use social summary when requested by external source
                    final DataStatus status = mContactData.getStatuses().get(entry.id);
                    final boolean hasSocial = kind.actionBodySocial && status != null;
                    if (hasSocial) {
                        entry.applyStatus(status, true);
                    }

                    if (hasSocial || hasData) {
                        mOtherEntries.add(entry);
                    }
                }
            }
        }

        if (!groups.isEmpty()) {
            DetailViewEntry entry = new DetailViewEntry();
            Collections.sort(groups);
            StringBuilder sb = new StringBuilder();
            int size = groups.size();
            for (int i = 0; i < size; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(groups.get(i));
            }
            entry.mimetype = GroupMembership.MIMETYPE;
            entry.kind = mContext.getString(R.string.groupsLabel);
            entry.data = sb.toString();
            mGroupEntries.add(entry);
        }
    }

    /**
     * Collapse all contact detail entries into one aggregated list with a {@link HeaderViewEntry}
     * at the top.
     */
    private void flattenAllLists() {
        // All contacts should have a header view (even if there is no data for the contact).
        mAllEntries.add(new HeaderViewEntry());

        flattenList(mPhoneEntries);
        flattenList(mSmsEntries);
        flattenList(mEmailEntries);
        flattenList(mImEntries);
        flattenList(mPostalEntries);
        flattenList(mNicknameEntries);
        flattenList(mNoteEntries);
        flattenList(mWebsiteEntries);
        flattenList(mSipEntries);
        flattenList(mEventEntries);
        flattenList(mOtherEntries);
        flattenList(mRelationEntries);
        flattenList(mGroupEntries);
    }

    /**
     * Iterate through {@link DetailViewEntry} in the given list and add it to a list of all
     * entries. Add a {@link SeparatorViewEntry} at the end if the length of the list was not 0.
     * Clear the original list.
     */
    private void flattenList(ArrayList<DetailViewEntry> entries) {
        int count = entries.size();

        for (int i = 0; i < count; i++) {
            mAllEntries.add(entries.get(i));
        }

        if (count > 0) {
            mAllEntries.add(new SeparatorViewEntry());
        }

        // Clear old list because it's not needed anymore.
        entries.clear();
    }

    /**
     * Maps group ID to the corresponding group name, collapses all synonymous groups.
     * Ignores default groups (e.g. My Contacts) and favorites groups.
     */
    private void handleGroupMembership(
            ArrayList<String> groups, List<GroupMetaData> groupMetaData, long groupId) {
        if (groupMetaData == null) {
            return;
        }

        for (GroupMetaData group : groupMetaData) {
            if (group.getGroupId() == groupId) {
                if (!group.isDefaultGroup() && !group.isFavorites()) {
                    String title = group.getTitle();
                    if (!groups.contains(title)) {
                        groups.add(title);
                    }
                }
                break;
            }
        }
    }

    private static String buildDataString(DataKind kind, ContentValues values,
            Context context) {
        if (kind.actionBody == null) {
            return null;
        }
        CharSequence actionBody = kind.actionBody.inflateUsing(context, values);
        return actionBody == null ? null : actionBody.toString();
    }

    /**
     * Build {@link Intent} to launch an action for the given {@link Im} or
     * {@link Email} row. If the result is non-null, it either contains one or two Intents
     * (e.g. [Text, Videochat] or just [Text])
     */
    public static void buildImActions(DetailViewEntry entry, ContentValues values) {
        final boolean isEmail = Email.CONTENT_ITEM_TYPE.equals(values.getAsString(Data.MIMETYPE));

        if (!isEmail && !isProtocolValid(values)) {
            return;
        }

        final String data = values.getAsString(isEmail ? Email.DATA : Im.DATA);
        if (TextUtils.isEmpty(data)) {
            return;
        }

        final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK : values.getAsInteger(Im.PROTOCOL);

        if (protocol == Im.PROTOCOL_GOOGLE_TALK) {
            final Integer chatCapabilityObj = values.getAsInteger(Im.CHAT_CAPABILITY);
            final int chatCapability = chatCapabilityObj == null ? 0 : chatCapabilityObj;
            entry.chatCapability = chatCapability;
            if ((chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0) {
                entry.actionIcon = R.drawable.sym_action_talk_holo_light;
                entry.intent =
                        new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
                entry.secondaryIntent =
                        new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?call"));
            } else if ((chatCapability & Im.CAPABILITY_HAS_VOICE) != 0) {
                // Allow Talking and Texting
                entry.actionIcon = R.drawable.sym_action_talk_holo_light;
                entry.intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
                entry.secondaryIntent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?call"));
            } else {
                entry.actionIcon = R.drawable.sym_action_talk_holo_light;
                entry.intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
            }
        } else {
            // Build an IM Intent
            String host = values.getAsString(Im.CUSTOM_PROTOCOL);

            if (protocol != Im.PROTOCOL_CUSTOM) {
                // Try bringing in a well-known host for specific protocols
                host = ContactsUtils.lookupProviderNameFromId(protocol);
            }

            if (!TextUtils.isEmpty(host)) {
                final String authority = host.toLowerCase();
                final Uri imUri = new Uri.Builder().scheme(Constants.SCHEME_IMTO).authority(
                        authority).appendPath(data).build();
                entry.actionIcon = R.drawable.sym_action_talk_holo_light;
                entry.intent = new Intent(Intent.ACTION_SENDTO, imUri);
            }
        }
    }

    private static boolean isProtocolValid(ContentValues values) {
        String protocolString = values.getAsString(Im.PROTOCOL);
        if (protocolString == null) {
            return false;
        }
        try {
            Integer.valueOf(protocolString);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /**
     * Base class for an item in the {@link ViewAdapter} list of data, which is
     * supplied to the {@link ListView}.
     */
    static class ViewEntry {
        private final int viewTypeForAdapter;
        protected long id = -1;
        /** Whether or not the entry can be focused on or not. */
        protected boolean isEnabled = false;

        ViewEntry(int viewType) {
            viewTypeForAdapter = viewType;
        }

        int getViewType() {
            return viewTypeForAdapter;
        }

        long getId() {
            return id;
        }

        boolean isEnabled(){
            return isEnabled;
        }
    }

    /**
     * Header item in the {@link ViewAdapter} list of data.
     */
    static class HeaderViewEntry extends ViewEntry {

        HeaderViewEntry() {
            super(ViewAdapter.VIEW_TYPE_HEADER_ENTRY);
        }

    }

    /**
     * Separator between items of the same {@link DataKind} in the
     * {@link ViewAdapter} list of data.
     */
    static class SeparatorViewEntry extends ViewEntry {

        SeparatorViewEntry() {
            super(ViewAdapter.VIEW_TYPE_SEPARATOR_ENTRY);
        }

    }

    /**
     * An item with a single detail for a contact in the {@link ViewAdapter}
     * list of data.
     */
    static class DetailViewEntry extends ViewEntry implements Collapsible<DetailViewEntry> {
        public int type = -1;
        public String kind;
        public String typeString;
        public String data;
        public Uri uri;
        public int maxLines = 1;
        public String mimetype;

        public Context context = null;
        public String resPackageName = null;
        public int actionIcon = -1;
        public boolean isPrimary = false;
        public int secondaryActionIcon = -1;
        public Intent intent;
        public Intent secondaryIntent = null;
        public ArrayList<Long> ids = new ArrayList<Long>();
        public int collapseCount = 0;

        public int presence = -1;
        public int chatCapability = 0;

        public CharSequence footerLine = null;

        DetailViewEntry() {
            super(ViewAdapter.VIEW_TYPE_DETAIL_ENTRY);
            isEnabled = true;
        }

        /**
         * Build new {@link DetailViewEntry} and populate from the given values.
         */
        public static DetailViewEntry fromValues(Context context, String mimeType, DataKind kind,
                long dataId, ContentValues values, boolean isDirectoryEntry, long directoryId) {
            final DetailViewEntry entry = new DetailViewEntry();
            entry.id = dataId;
            entry.context = context;
            entry.uri = ContentUris.withAppendedId(Data.CONTENT_URI, entry.id);
            if (isDirectoryEntry) {
                entry.uri = entry.uri.buildUpon().appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId)).build();
            }
            entry.mimetype = mimeType;
            entry.kind = (kind.titleRes == -1 || kind.titleRes == 0) ? ""
                    : context.getString(kind.titleRes);
            entry.data = buildDataString(kind, values, context);

            if (kind.typeColumn != null && values.containsKey(kind.typeColumn)) {
                entry.type = values.getAsInteger(kind.typeColumn);

                // get type string
                entry.typeString = "";
                for (EditType type : kind.typeList) {
                    if (type.rawValue == entry.type) {
                        if (!type.unspecifiedType) {
                            if (type.customColumn == null) {
                                // Non-custom type. Get its description from the resource
                                entry.typeString = context.getString(type.labelRes);
                            } else {
                                // Custom type. Read it from the database
                                entry.typeString = values.getAsString(type.customColumn);
                            }
                        }
                        break;
                    }
                }
            } else {
                entry.typeString = "";
            }

            if (kind.iconRes > 0) {
                entry.resPackageName = kind.resPackageName;
                entry.actionIcon = kind.iconRes;
            }

            return entry;
        }

        /**
         * Apply given {@link DataStatus} values over this {@link DetailViewEntry}
         *
         * @param fillData When true, the given status replaces {@link #data}
         *            and {@link #footerLine}. Otherwise only {@link #presence}
         *            is updated.
         */
        public DetailViewEntry applyStatus(DataStatus status, boolean fillData) {
            presence = status.getPresence();
            if (fillData && status.isValid()) {
                this.data = status.getStatus().toString();
                this.footerLine = status.getTimestampLabel(context);
            }

            return this;
        }

        @Override
        public boolean collapseWith(DetailViewEntry entry) {
            // assert equal collapse keys
            if (!shouldCollapseWith(entry)) {
                return false;
            }

            // Choose the label associated with the highest type precedence.
            if (TypePrecedence.getTypePrecedence(mimetype, type)
                    > TypePrecedence.getTypePrecedence(entry.mimetype, entry.type)) {
                type = entry.type;
                kind = entry.kind;
                typeString = entry.typeString;
            }

            // Choose the max of the maxLines and maxLabelLines values.
            maxLines = Math.max(maxLines, entry.maxLines);

            // Choose the presence with the highest precedence.
            if (StatusUpdates.getPresencePrecedence(presence)
                    < StatusUpdates.getPresencePrecedence(entry.presence)) {
                presence = entry.presence;
            }

            // If any of the collapsed entries are primary make the whole thing primary.
            isPrimary = entry.isPrimary ? true : isPrimary;

            // uri, and contactdId, shouldn't make a difference. Just keep the original.

            // Keep track of all the ids that have been collapsed with this one.
            ids.add(entry.getId());
            collapseCount++;
            return true;
        }

        @Override
        public boolean shouldCollapseWith(DetailViewEntry entry) {
            if (entry == null) {
                return false;
            }

            if (!ContactsUtils.shouldCollapse(context, mimetype, data, entry.mimetype,
                    entry.data)) {
                return false;
            }

            if (!TextUtils.equals(mimetype, entry.mimetype)
                    || !ContactsUtils.areIntentActionEqual(intent, entry.intent)
                    || !ContactsUtils.areIntentActionEqual(secondaryIntent, entry.secondaryIntent)
                    || actionIcon != entry.actionIcon) {
                return false;
            }

            return true;
        }
    }

    /** Cache of the children views of a row */
    private static class ViewCache {
        public TextView kind;
        public TextView type;
        public TextView data;
        public TextView footer;
        public ImageView presenceIcon;
        public ImageView secondaryActionButton;
        public View secondaryActionButtonContainer;
        public View secondaryActionDivider;
    }

    private final class ViewAdapter extends BaseAdapter {

        public static final int VIEW_TYPE_DETAIL_ENTRY = 0;
        public static final int VIEW_TYPE_HEADER_ENTRY = 1;
        public static final int VIEW_TYPE_SEPARATOR_ENTRY = 2;
        private static final int VIEW_TYPE_COUNT = 3;

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            switch (getItemViewType(position)) {
                case VIEW_TYPE_HEADER_ENTRY:
                    return getHeaderEntryView(convertView, parent);
                case VIEW_TYPE_SEPARATOR_ENTRY:
                    return getSeparatorEntryView(convertView, parent);
                case VIEW_TYPE_DETAIL_ENTRY:
                    return getDetailEntryView(position, convertView, parent);
                default:
                    throw new IllegalStateException("Invalid view type ID " +
                            getItemViewType(position));
            }
        }

        private View getHeaderEntryView(View convertView, ViewGroup parent) {
            // We don't want to rely on the recycled header view because it may
            // have been left over from a previously viewed contact (since we
            // reuse the adapter), so we would have to bind the data to the
            // header each time. However, since there is only 1 header per list,
            // just hold onto the original header view for this contact and
            // return that each time.
            if (mHeaderView != null) {
                return mHeaderView;
            }
            mHeaderView = (ContactDetailHeaderView) mInflater.inflate(
                    R.layout.contact_detail_header_view_list_item, parent, false);
            mHeaderView.setListener(mHeaderViewListener);
            mHeaderView.loadData(mContactData);
            return mHeaderView;
        }

        private View getSeparatorEntryView(View convertView, ViewGroup parent) {
            if (convertView != null) {
                return convertView;
            }
            return mInflater.inflate(R.layout.contact_detail_separator_list_item, parent, false);
        }

        private View getDetailEntryView(int position, View convertView, ViewGroup parent) {
            final DetailViewEntry entry = (DetailViewEntry) getItem(position);
            final View v;
            final ViewCache viewCache;

            // Check to see if we can reuse convertView
            if (convertView != null) {
                v = convertView;
                viewCache = (ViewCache) v.getTag();
            } else {
                // Create a new view if needed
                v = mInflater.inflate(R.layout.contact_detail_list_item, parent, false);

                // Cache the children
                viewCache = new ViewCache();
                viewCache.kind = (TextView) v.findViewById(R.id.kind);
                viewCache.type = (TextView) v.findViewById(R.id.type);
                viewCache.data = (TextView) v.findViewById(R.id.data);
                viewCache.footer = (TextView) v.findViewById(R.id.footer);
                viewCache.presenceIcon = (ImageView) v.findViewById(R.id.presence_icon);
                viewCache.secondaryActionButton = (ImageView) v.findViewById(
                        R.id.secondary_action_button);
                viewCache.secondaryActionButtonContainer = v.findViewById(
                        R.id.secondary_action_button_container);
                viewCache.secondaryActionButtonContainer.setOnClickListener(
                        mSecondaryActionClickListener);
                viewCache.secondaryActionDivider = v.findViewById(R.id.divider);
                v.setTag(viewCache);
            }

            final ViewEntry previousEntry = position == 0 ? null : getItem(position - 1);
            final boolean isFirstOfItsKind = (previousEntry == null) ? true :
                    (previousEntry.getViewType() != VIEW_TYPE_DETAIL_ENTRY);

            // Bind the data to the view
            bindView(v, entry, isFirstOfItsKind);
            return v;
        }

        private void bindView(View view, DetailViewEntry entry, boolean isFirstOfItsKind) {
            final Resources resources = mContext.getResources();
            ViewCache views = (ViewCache) view.getTag();

            if (isFirstOfItsKind) {
                views.kind.setText(entry.kind != null ? entry.kind.toUpperCase() : "");
                views.kind.setVisibility(View.VISIBLE);
            } else {
                views.kind.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(entry.typeString)) {
                views.type.setText(entry.typeString.toUpperCase());
                views.type.setVisibility(View.VISIBLE);
                if (isFirstOfItsKind) {
                    views.kind.setVisibility(View.GONE);
                }
            } else {
                views.type.setVisibility(View.GONE);
            }

            views.data.setText(entry.data);
            setMaxLines(views.data, entry.maxLines);

            // Set the footer
            if (!TextUtils.isEmpty(entry.footerLine)) {
                views.footer.setText(entry.footerLine);
                views.footer.setVisibility(View.VISIBLE);
            } else {
                views.footer.setVisibility(View.GONE);
            }

            // Set the presence icon
            final Drawable presenceIcon = ContactPresenceIconUtil.getPresenceIcon(
                    mContext, entry.presence);
            final ImageView presenceIconView = views.presenceIcon;
            if (presenceIcon != null) {
                presenceIconView.setImageDrawable(presenceIcon);
                presenceIconView.setVisibility(View.VISIBLE);
            } else {
                presenceIconView.setVisibility(View.GONE);
            }

            // Set the secondary action button
            final ImageView secondaryActionView = views.secondaryActionButton;
            Drawable secondaryActionIcon = null;
            if (entry.secondaryActionIcon != -1) {
                secondaryActionIcon = resources.getDrawable(entry.secondaryActionIcon);
            } else if ((entry.chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0) {
                secondaryActionIcon =
                        resources.getDrawable(R.drawable.sym_action_videochat_holo_light);
            } else if ((entry.chatCapability & Im.CAPABILITY_HAS_VOICE) != 0) {
                secondaryActionIcon =
                        resources.getDrawable(R.drawable.sym_action_audiochat_holo_light);
            }

            if (entry.secondaryIntent != null && secondaryActionIcon != null) {
                secondaryActionView.setImageDrawable(secondaryActionIcon);
                views.secondaryActionButtonContainer.setTag(entry);
                views.secondaryActionButtonContainer.setVisibility(View.VISIBLE);
                views.secondaryActionDivider.setVisibility(View.VISIBLE);
            } else {
                views.secondaryActionButtonContainer.setVisibility(View.GONE);
                views.secondaryActionDivider.setVisibility(View.GONE);
            }
        }

        private void setMaxLines(TextView textView, int maxLines) {
            if (maxLines == 1) {
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                textView.setSingleLine(false);
                textView.setMaxLines(maxLines);
                textView.setEllipsize(null);
            }
        }

        private OnClickListener mSecondaryActionClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener == null) return;
                if (v == null) return;
                final ViewEntry entry = (ViewEntry) v.getTag();
                if (entry == null || !(entry instanceof DetailViewEntry)) return;
                final DetailViewEntry detailViewEntry = (DetailViewEntry) entry;
                final Intent intent = detailViewEntry.secondaryIntent;
                if (intent == null) return;
                mListener.onItemClicked(intent);
            }
        };

        @Override
        public int getCount() {
            return mAllEntries.size();
        }

        @Override
        public ViewEntry getItem(int position) {
            return mAllEntries.get(position);
        }

        @Override
        public int getItemViewType(int position) {
            return mAllEntries.get(position).getViewType();
        }

        @Override
        public int getViewTypeCount() {
            return VIEW_TYPE_COUNT;
        }

        @Override
        public long getItemId(int position) {
            final ViewEntry entry = mAllEntries.get(position);
            if (entry != null) {
                return entry.getId();
            }
            return -1;
        }

        @Override
        public boolean areAllItemsEnabled() {
            // Header will always be an item that is not enabled.
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).isEnabled();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view, menu);
    }

    public boolean isOptionsMenuChanged() {
        return mOptionsMenuOptions != isContactOptionsChangeEnabled()
                || mOptionsMenuEditable != isContactEditable()
                || mOptionsMenuShareable != isContactShareable();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuOptions = isContactOptionsChangeEnabled();
        mOptionsMenuEditable = isContactEditable();
        mOptionsMenuShareable = isContactShareable();

        // Options only shows telephony-related settings (ringtone, send to voicemail).
        // ==> Hide if we don't have a telephone
        final MenuItem optionsMenu = menu.findItem(R.id.menu_options);
        optionsMenu.setVisible(mOptionsMenuOptions);

        final MenuItem editMenu = menu.findItem(R.id.menu_edit);
        editMenu.setVisible(mOptionsMenuEditable);

        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete);
        deleteMenu.setVisible(mOptionsMenuEditable);

        final MenuItem shareMenu = menu.findItem(R.id.menu_share);
        shareMenu.setVisible(mOptionsMenuShareable);
    }

    public boolean isContactOptionsChangeEnabled() {
        return mContactData != null && !mContactData.isDirectoryEntry()
                && PhoneCapabilityTester.isPhone(mContext);
    }

    public boolean isContactEditable() {
        return mContactData != null && !mContactData.isDirectoryEntry();
    }

    public boolean isContactShareable() {
        return mContactData != null && !mContactData.isDirectoryEntry() && !mAllRestricted;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit: {
                if (mListener != null) mListener.onEditRequested(mLookupUri);
                break;
            }
            case R.id.menu_delete: {
                if (mListener != null) mListener.onDeleteRequested(mLookupUri);
                return true;
            }
            case R.id.menu_options: {
                if (mContactData == null) return false;
                final Intent intent = new Intent(mContext, ContactOptionsActivity.class);
                intent.setData(mContactData.getLookupUri());
                mContext.startActivity(intent);
                return true;
            }
            case R.id.menu_share: {
                if (mAllRestricted) return false;
                if (mContactData == null) return false;

                final String lookupKey = mContactData.getLookupKey();
                final Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, shareUri);

                // Launch chooser to share contact via
                final CharSequence chooseTitle = mContext.getText(R.string.share_via);
                final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);

                try {
                    mContext.startActivity(chooseIntent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(mContext, R.string.share_error, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        }
        return false;
    }

    private void makePersonalCopy() {
        if (mListener == null) {
            return;
        }

        int exportSupport = mContactData.getDirectoryExportSupport();
        switch (exportSupport) {
            case Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY: {
                createCopy(new Account(mContactData.getDirectoryAccountName(),
                                mContactData.getDirectoryAccountType()));
                break;
            }
            case Directory.EXPORT_SUPPORT_ANY_ACCOUNT: {
                final ArrayList<Account> accounts =
                        AccountTypeManager.getInstance(mContext).getAccounts(true);
                if (accounts.isEmpty()) {
                    createCopy(null);
                    return;  // Don't show a dialog.
                }

                // In the common case of a single writable account, auto-select
                // it without showing a dialog.
                if (accounts.size() == 1) {
                    createCopy(accounts.get(0));
                    return;  // Don't show a dialog.
                }

                final SelectAccountDialogFragment dialog = new SelectAccountDialogFragment();
                dialog.setTargetFragment(this, 0);
                dialog.show(getFragmentManager(), SelectAccountDialogFragment.TAG);
                break;
            }
        }
    }

    @Override
    public void onAccountSelectorCancelled() {
    }

    @Override
    public void onAccountChosen(Account account) {
        createCopy(account);
    }

    private void createCopy(Account account) {
        if (mListener != null) {
            mListener.onCreateRawContactRequested(mContactData.getContentValues(), account);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mListener == null) return;
        final ViewEntry entry = mAdapter.getItem(position);
        if (entry == null || !(entry instanceof DetailViewEntry)) return;
        final Intent intent = ((DetailViewEntry) entry).intent;
        if (intent == null) return;
        mListener.onItemClicked(intent);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (mListener == null) return false;
        final ViewCache cache = (ViewCache) view.getTag();
        if (cache == null) return false;
        CharSequence text = cache.data.getText();
        if (TextUtils.isEmpty(text)) return false;

        ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(
                Context.CLIPBOARD_SERVICE);
        cm.setText(text);
        Toast.makeText(getActivity(), R.string.toast_text_copied, Toast.LENGTH_SHORT).show();
        return true;
    }

    public boolean handleKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                try {
                    ITelephony phone = ITelephony.Stub.asInterface(
                            ServiceManager.checkService("phone"));
                    if (phone != null && !phone.isIdle()) {
                        // Skip out and let the key be handled at a higher level
                        break;
                    }
                } catch (RemoteException re) {
                    // Fall through and try to call the contact
                }

                int index = mListView.getSelectedItemPosition();
                if (index != -1) {
                    final DetailViewEntry entry = (DetailViewEntry) mAdapter.getItem(index);
                    if (entry != null && entry.intent != null &&
                            entry.intent.getAction() == Intent.ACTION_CALL_PRIVILEGED) {
                        mContext.startActivity(entry.intent);
                        return true;
                    }
                } else if (mPrimaryPhoneUri != null) {
                    // There isn't anything selected, call the default number
                    final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            mPrimaryPhoneUri);
                    mContext.startActivity(intent);
                    return true;
                }
                return false;
            }

            case KeyEvent.KEYCODE_DEL: {
                if (mListener != null) mListener.onDeleteRequested(mLookupUri);
                return true;
            }
        }

        return false;
    }

    /**
     * The listener for the detail loader
     */
    private final LoaderManager.LoaderCallbacks<ContactLoader.Result> mDetailLoaderListener =
            new LoaderCallbacks<ContactLoader.Result>() {
        @Override
        public Loader<ContactLoader.Result> onCreateLoader(int id, Bundle args) {
            Uri lookupUri = args.getParcelable(LOADER_ARG_CONTACT_URI);
            return new ContactLoader(mContext, lookupUri, true /* loadGroupMetaData */);
        }

        @Override
        public void onLoadFinished(Loader<ContactLoader.Result> loader, ContactLoader.Result data) {
            if (!mLookupUri.equals(data.getUri())) {
                return;
            }

            if (data != ContactLoader.Result.NOT_FOUND && data != ContactLoader.Result.ERROR) {
                mContactData = data;
            } else {
                Log.i(TAG, "No contact found: " + ((ContactLoader)loader).getLookupUri());
                mContactData = null;
            }

            bindData();

            if (mContactData == null && mListener != null) {
                mListener.onContactNotFound();
            }
        }

        public void onLoaderReset(Loader<ContactLoader.Result> loader) {
            mContactData = null;
            bindData();
        }
    };

    private ContactDetailHeaderView.Listener mHeaderViewListener =
            new ContactDetailHeaderView.Listener() {
        @Override
        public void onDisplayNameClick(View view) {
        }

        @Override
        public void onPhotoClick(View view) {
        }
    };

    public static interface Listener {
        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete
         */
        public void onContactNotFound();

        /**
         * User decided to go to Edit-Mode
         */
        public void onEditRequested(Uri lookupUri);

        /**
         * User clicked a single item (e.g. mail)
         */
        public void onItemClicked(Intent intent);

        /**
         * User decided to delete the contact
         */
        public void onDeleteRequested(Uri lookupUri);

        /**
         * User requested creation of a new contact with the specified values.
         *
         * @param values ContentValues containing data rows for the new contact.
         * @param account Account where the new contact should be created
         */
        public void onCreateRawContactRequested(ArrayList<ContentValues> values, Account account);
    }
}
