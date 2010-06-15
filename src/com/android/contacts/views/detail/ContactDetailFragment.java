/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.contacts.views.detail;

import com.android.contacts.Collapser;
import com.android.contacts.ContactOptionsActivity;
import com.android.contacts.ContactPresenceIconUtil;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.TypePrecedence;
import com.android.contacts.Collapser.Collapsible;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DataStatus;
import com.android.contacts.views.ContactLoader;
import com.android.internal.telephony.ITelephony;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.LoaderManagingFragment;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.Intent;
import android.content.Loader;
import android.content.Entity.NamedContentValues;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ParseException;
import android.net.Uri;
import android.net.WebAddress;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.StatusUpdates;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;

public class ContactDetailFragment extends LoaderManagingFragment<ContactLoader.Result>
        implements OnCreateContextMenuListener, OnItemClickListener {
    private static final String TAG = "ContactDetailFragment";

    private static final int MENU_ITEM_MAKE_DEFAULT = 3;

    private static final int LOADER_DETAILS = 1;

    private Context mContext;
    private Uri mLookupUri;
    private Listener mListener;

    private ContactLoader.Result mContactData;
    private ContactDetailHeaderView mHeaderView;
    private ListView mListView;
    private boolean mShowSmsLinksForAllPhones;
    private ViewAdapter mAdapter;
    private Uri mPrimaryPhoneUri = null;

    private int mReadOnlySourcesCnt;
    private int mWritableSourcesCnt;
    private boolean mAllRestricted;
    private final ArrayList<Long> mWritableRawContactIds = new ArrayList<Long>();
    private int mNumPhoneNumbers = 0;

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
    private ArrayList<ViewEntry> mPhoneEntries = new ArrayList<ViewEntry>();
    private ArrayList<ViewEntry> mSmsEntries = new ArrayList<ViewEntry>();
    private ArrayList<ViewEntry> mEmailEntries = new ArrayList<ViewEntry>();
    private ArrayList<ViewEntry> mPostalEntries = new ArrayList<ViewEntry>();
    private ArrayList<ViewEntry> mImEntries = new ArrayList<ViewEntry>();
    private ArrayList<ViewEntry> mNicknameEntries = new ArrayList<ViewEntry>();
    private ArrayList<ViewEntry> mOrganizationEntries = new ArrayList<ViewEntry>();
    private ArrayList<ViewEntry> mGroupEntries = new ArrayList<ViewEntry>();
    private ArrayList<ViewEntry> mOtherEntries = new ArrayList<ViewEntry>();
    private ArrayList<ArrayList<ViewEntry>> mSections = new ArrayList<ArrayList<ViewEntry>>();
    private LayoutInflater mInflater;

    public ContactDetailFragment() {
        // Explicit constructor for inflation

        // Build the list of sections. The order they're added to mSections dictates the
        // order they are displayed in the list.
        mSections.add(mPhoneEntries);
        mSections.add(mSmsEntries);
        mSections.add(mEmailEntries);
        mSections.add(mImEntries);
        mSections.add(mPostalEntries);
        mSections.add(mNicknameEntries);
        mSections.add(mOrganizationEntries);
        mSections.add(mGroupEntries);
        mSections.add(mOtherEntries);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View view = inflater.inflate(R.layout.contact_detail_fragment, container, false);

        setHasOptionsMenu(true);

        mInflater = inflater;

        mHeaderView = (ContactDetailHeaderView) view.findViewById(R.id.contact_header_widget);
        mHeaderView.setExcludeMimes(new String[] {
            Contacts.CONTENT_ITEM_TYPE
        });
        mHeaderView.setListener(mHeaderViewListener);

        mListView = (ListView) view.findViewById(android.R.id.list);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        mListView.setOnItemClickListener(this);
        // Don't set it to mListView yet.  We do so later when we bind the adapter.
        mEmptyView = view.findViewById(android.R.id.empty);

        //TODO Read this value from a preference
        mShowSmsLinksForAllPhones = true;

        return view;
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public void loadUri(Uri lookupUri) {
        mLookupUri = lookupUri;
        startLoading(LOADER_DETAILS, null);
    }

    @Override
    protected void onInitializeLoaders() {
    }

    @Override
    protected Loader<ContactLoader.Result> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case LOADER_DETAILS: {
                return new ContactLoader(mContext, mLookupUri);
            }
            default: {
                Log.wtf(TAG, "Unknown ID in onCreateLoader: " + id);
            }
        }
        return null;
    }

    @Override
    protected void onLoadFinished(Loader<ContactLoader.Result> loader,
            ContactLoader.Result data) {
        final int id = loader.getId();
        switch (id) {
            case LOADER_DETAILS:
                if (data == ContactLoader.Result.NOT_FOUND) {
                    // Item has been deleted
                    Log.i(TAG, "No contact found. Closing activity");
                    mListener.onContactNotFound();
                    return;
                }
                mContactData = data;
                bindData();
                break;
            default: {
                Log.wtf(TAG, "Unknown ID in onLoadFinished: " + id);
            }
        }
    }

    private void bindData() {
        // Set the header
        mHeaderView.loadData(mContactData);

        // Build up the contact entries
        buildEntries();

        // Collapse similar data items in select sections.
        Collapser.collapseList(mPhoneEntries);
        Collapser.collapseList(mSmsEntries);
        Collapser.collapseList(mEmailEntries);
        Collapser.collapseList(mPostalEntries);
        Collapser.collapseList(mImEntries);

        if (mAdapter == null) {
            mAdapter = new ViewAdapter();
            mListView.setAdapter(mAdapter);
        } else {
            mAdapter.notifyDataSetChanged();
        }
        mListView.setEmptyView(mEmptyView);
    }

    /**
     * Build up the entries to display on the screen.
     */
    private final void buildEntries() {
        // Clear out the old entries
        final int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        mRawContactIds.clear();

        mReadOnlySourcesCnt = 0;
        mWritableSourcesCnt = 0;
        mAllRestricted = true;
        mPrimaryPhoneUri = null;
        mNumPhoneNumbers = 0;

        mWritableRawContactIds.clear();

        // TODO: This should be done in the background thread
        final Sources sources = Sources.getInstance(mContext);

        // Build up method entries
        if (mContactData == null) {
            return;
        }

        for (Entity entity: mContactData.getEntities()) {
            final ContentValues entValues = entity.getEntityValues();
            final String accountType = entValues.getAsString(RawContacts.ACCOUNT_TYPE);
            final long rawContactId = entValues.getAsLong(RawContacts._ID);

            // Mark when this contact has any unrestricted components
            final boolean isRestricted = entValues.getAsInteger(RawContacts.IS_RESTRICTED) != 0;
            if (!isRestricted) mAllRestricted = false;

            if (!mRawContactIds.contains(rawContactId)) {
                mRawContactIds.add(rawContactId);
            }
            ContactsSource contactsSource = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_SUMMARY);
            if (contactsSource != null && contactsSource.readOnly) {
                mReadOnlySourcesCnt += 1;
            } else {
                mWritableSourcesCnt += 1;
                mWritableRawContactIds.add(rawContactId);
            }


            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                entryValues.put(Data.RAW_CONTACT_ID, rawContactId);

                final long dataId = entryValues.getAsLong(Data._ID);
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);
                if (mimeType == null) continue;

                final DataKind kind = sources.getKindOrFallback(accountType, mimeType, mContext,
                        ContactsSource.LEVEL_MIMETYPES);
                if (kind == null) continue;

                final ViewEntry entry = ViewEntry.fromValues(mContext, mimeType, kind,
                        rawContactId, dataId, entryValues);

                final boolean hasData = !TextUtils.isEmpty(entry.data);
                final boolean isSuperPrimary = entryValues.getAsInteger(
                        Data.IS_SUPER_PRIMARY) != 0;

                if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Always ignore the name. It is shown in the header if set
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build phone entries
                    mNumPhoneNumbers++;

                    entry.intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts(Constants.SCHEME_TEL, entry.data, null));
                    entry.secondaryIntent = new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts(Constants.SCHEME_SMSTO, entry.data, null));

                    // Remember super-primary phone
                    if (isSuperPrimary) mPrimaryPhoneUri = entry.uri;

                    entry.isPrimary = isSuperPrimary;
                    mPhoneEntries.add(entry);

                    if (entry.type == CommonDataKinds.Phone.TYPE_MOBILE
                            || mShowSmsLinksForAllPhones) {
                        // Add an SMS entry
                        if (kind.iconAltRes > 0) {
                            entry.secondaryActionIcon = kind.iconAltRes;
                        }
                    }
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
                        final DataKind imKind = sources.getKindOrFallback(accountType,
                                imMime, mContext, ContactsSource.LEVEL_MIMETYPES);
                        final ViewEntry imEntry = ViewEntry.fromValues(mContext,
                                imMime, imKind, rawContactId, dataId, entryValues);
                        imEntry.intent = ContactsUtils.buildImIntent(entryValues);
                        imEntry.applyStatus(status, false);
                        mImEntries.add(imEntry);
                    }
                } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build postal entries
                    entry.maxLines = 4;
                    entry.intent = new Intent(Intent.ACTION_VIEW, entry.uri);
                    mPostalEntries.add(entry);
                } else if (Im.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build IM entries
                    entry.intent = ContactsUtils.buildImIntent(entryValues);
                    if (TextUtils.isEmpty(entry.label)) {
                        entry.label = mContext.getString(R.string.chat).toLowerCase();
                    }

                    // Apply presence and status details when available
                    final DataStatus status = mContactData.getStatuses().get(entry.id);
                    if (status != null) {
                        entry.applyStatus(status, false);
                    }
                    mImEntries.add(entry);
                } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType) &&
                        (hasData || !TextUtils.isEmpty(entry.label))) {
                    // Build organization entries
                    final boolean isNameRawContact =
                            (mContactData.getNameRawContactId() == rawContactId);

                    final boolean duplicatesTitle =
                            isNameRawContact
                            && mContactData.getDisplayNameSource()
                                == DisplayNameSources.ORGANIZATION
                            && (!hasData || TextUtils.isEmpty(entry.label));

                    if (!duplicatesTitle) {
                        entry.uri = null;

                        if (TextUtils.isEmpty(entry.label)) {
                            entry.label = entry.data;
                            entry.data = "";
                        }

                        mOrganizationEntries.add(entry);
                    }
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
                    mOtherEntries.add(entry);
                } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build note entries
                    entry.uri = null;
                    entry.maxLines = 10;
                    try {
                        WebAddress webAddress = new WebAddress(entry.data);
                        entry.intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(webAddress.toString()));
                    } catch (ParseException e) {
                        Log.e(TAG, "Couldn't parse website: " + entry.data);
                    }
                    mOtherEntries.add(entry);
                } else {
                    // Handle showing custom rows
                    entry.intent = new Intent(Intent.ACTION_VIEW, entry.uri);

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
    }

    private static String buildActionString(DataKind kind, ContentValues values,
            boolean lowerCase, Context context) {
        if (kind.actionHeader == null) {
            return null;
        }
        CharSequence actionHeader = kind.actionHeader.inflateUsing(context, values);
        if (actionHeader == null) {
            return null;
        }
        return lowerCase ? actionHeader.toString().toLowerCase() : actionHeader.toString();
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
     * A basic structure with the data for a contact entry in the list.
     */
    private static class ViewEntry implements Collapsible<ViewEntry> {
        // Copied from baseclass
        public int type = -1;
        public String label;
        public String data;
        public Uri uri;
        public long id = 0;
        public int maxLines = 1;
        public String mimetype;

        public Context context = null;
        public String resPackageName = null;
        public int actionIcon = -1;
        public boolean isPrimary = false;
        public int secondaryActionIcon = -1;
        public Intent intent;
        public Intent secondaryIntent = null;
        public int maxLabelLines = 1;
        public ArrayList<Long> ids = new ArrayList<Long>();
        public int collapseCount = 0;

        public int presence = -1;

        public CharSequence footerLine = null;

        private ViewEntry() {
        }

        /**
         * Build new {@link ViewEntry} and populate from the given values.
         */
        public static ViewEntry fromValues(Context context, String mimeType, DataKind kind,
                long rawContactId, long dataId, ContentValues values) {
            final ViewEntry entry = new ViewEntry();
            entry.context = context;
            entry.id = dataId;
            entry.uri = ContentUris.withAppendedId(Data.CONTENT_URI, entry.id);
            entry.mimetype = mimeType;
            entry.label = buildActionString(kind, values, false, context);
            entry.data = buildDataString(kind, values, context);

            if (kind.typeColumn != null && values.containsKey(kind.typeColumn)) {
                entry.type = values.getAsInteger(kind.typeColumn);
            }
            if (kind.iconRes > 0) {
                entry.resPackageName = kind.resPackageName;
                entry.actionIcon = kind.iconRes;
            }

            return entry;
        }

        /**
         * Apply given {@link DataStatus} values over this {@link ViewEntry}
         *
         * @param fillData When true, the given status replaces {@link #data}
         *            and {@link #footerLine}. Otherwise only {@link #presence}
         *            is updated.
         */
        public ViewEntry applyStatus(DataStatus status, boolean fillData) {
            presence = status.getPresence();
            if (fillData && status.isValid()) {
                this.data = status.getStatus().toString();
                this.footerLine = status.getTimestampLabel(context);
            }

            return this;
        }

        public boolean collapseWith(ViewEntry entry) {
            // assert equal collapse keys
            if (!shouldCollapseWith(entry)) {
                return false;
            }

            // Choose the label associated with the highest type precedence.
            if (TypePrecedence.getTypePrecedence(mimetype, type)
                    > TypePrecedence.getTypePrecedence(entry.mimetype, entry.type)) {
                type = entry.type;
                label = entry.label;
            }

            // Choose the max of the maxLines and maxLabelLines values.
            maxLines = Math.max(maxLines, entry.maxLines);
            maxLabelLines = Math.max(maxLabelLines, entry.maxLabelLines);

            // Choose the presence with the highest precedence.
            if (StatusUpdates.getPresencePrecedence(presence)
                    < StatusUpdates.getPresencePrecedence(entry.presence)) {
                presence = entry.presence;
            }

            // If any of the collapsed entries are primary make the whole thing primary.
            isPrimary = entry.isPrimary ? true : isPrimary;

            // uri, and contactdId, shouldn't make a difference. Just keep the original.

            // Keep track of all the ids that have been collapsed with this one.
            ids.add(entry.id);
            collapseCount++;
            return true;
        }

        public boolean shouldCollapseWith(ViewEntry entry) {
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
        public TextView label;
        public TextView data;
        public TextView footer;
        public ImageView actionIcon;
        public ImageView presenceIcon;
        public ImageView primaryIcon;
        public ImageView secondaryActionButton;
        public View secondaryActionDivider;
    }

    private final class ViewAdapter extends BaseAdapter {
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewEntry entry = getEntry(position);
            final View v;
            final ViewCache viewCache;

            // Check to see if we can reuse convertView
            if (convertView != null) {
                v = convertView;
                viewCache = (ViewCache) v.getTag();
            } else {
                // Create a new view if needed
                v = mInflater.inflate(R.layout.list_item_text_icons, parent, false);

                // Cache the children
                viewCache = new ViewCache();
                viewCache.label = (TextView) v.findViewById(android.R.id.text1);
                viewCache.data = (TextView) v.findViewById(android.R.id.text2);
                viewCache.footer = (TextView) v.findViewById(R.id.footer);
                viewCache.actionIcon = (ImageView) v.findViewById(R.id.action_icon);
                viewCache.primaryIcon = (ImageView) v.findViewById(R.id.primary_icon);
                viewCache.presenceIcon = (ImageView) v.findViewById(R.id.presence_icon);
                viewCache.secondaryActionButton = (ImageView) v.findViewById(
                        R.id.secondary_action_button);
                viewCache.secondaryActionButton.setOnClickListener(mSecondaryActionClickListener);
                viewCache.secondaryActionDivider = v.findViewById(R.id.divider);
                v.setTag(viewCache);
            }

            // Bind the data to the view
            bindView(v, entry);
            return v;
        }

        protected void bindView(View view, ViewEntry entry) {
            final Resources resources = mContext.getResources();
            ViewCache views = (ViewCache) view.getTag();

            // Set the label
            TextView label = views.label;
            setMaxLines(label, entry.maxLabelLines);
            label.setText(entry.label);

            // Set the data
            TextView data = views.data;
            if (data != null) {
                if (entry.mimetype.equals(Phone.CONTENT_ITEM_TYPE)
                        || entry.mimetype.equals(Constants.MIME_SMS_ADDRESS)) {
                    data.setText(PhoneNumberUtils.formatNumber(entry.data));
                } else {
                    data.setText(entry.data);
                }
                setMaxLines(data, entry.maxLines);
            }

            // Set the footer
            if (!TextUtils.isEmpty(entry.footerLine)) {
                views.footer.setText(entry.footerLine);
                views.footer.setVisibility(View.VISIBLE);
            } else {
                views.footer.setVisibility(View.GONE);
            }

            // Set the primary icon
            views.primaryIcon.setVisibility(entry.isPrimary ? View.VISIBLE : View.GONE);

            // Set the action icon
            final ImageView action = views.actionIcon;
            if (entry.actionIcon != -1) {
                Drawable actionIcon;
                if (entry.resPackageName != null) {
                    // Load external resources through PackageManager
                    actionIcon = mContext.getPackageManager().getDrawable(entry.resPackageName,
                            entry.actionIcon, null);
                } else {
                    actionIcon = resources.getDrawable(entry.actionIcon);
                }
                action.setImageDrawable(actionIcon);
                action.setVisibility(View.VISIBLE);
            } else {
                // Things should still line up as if there was an icon, so make it invisible
                action.setVisibility(View.INVISIBLE);
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
            }
            if (entry.secondaryIntent != null && secondaryActionIcon != null) {
                secondaryActionView.setImageDrawable(secondaryActionIcon);
                secondaryActionView.setTag(entry);
                secondaryActionView.setVisibility(View.VISIBLE);
                views.secondaryActionDivider.setVisibility(View.VISIBLE);
            } else {
                secondaryActionView.setVisibility(View.GONE);
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
            public void onClick(View v) {
                if (mListener == null) return;
                if (v == null) return;
                final ViewEntry entry = (ViewEntry) v.getTag();
                if (entry == null) return;
                final Intent intent = entry.secondaryIntent;
                if (intent == null) return;
                mListener.onItemClicked(intent);
            }
        };

        public int getCount() {
            int count = 0;
            final int numSections = mSections.size();
            for (int i = 0; i < numSections; i++) {
                final ArrayList<ViewEntry> section = mSections.get(i);
                count += section.size();
            }
            return count;
        }

        public Object getItem(int position) {
            return getEntry(position);
        }

        public long getItemId(int position) {
            final ViewEntry entry = getEntry(position);
            if (entry != null) {
                return entry.id;
            } else {
                return -1;
            }
        }

        private ViewEntry getEntry(int position) {
            final int numSections = mSections.size();
            for (int i = 0; i < numSections; i++) {
                final ArrayList<ViewEntry> section = mSections.get(i);
                final int sectionSize = section.size();
                if (position < sectionSize) {
                    return section.get(position);
                }
                position -= sectionSize;
            }
            return null;
        }
    }

    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view, menu);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        // Only allow edit when we have at least one raw_contact id
        final boolean hasRawContact = (mRawContactIds.size() > 0);
        menu.findItem(R.id.menu_edit).setEnabled(hasRawContact);

        // Only allow share when unrestricted contacts available
        menu.findItem(R.id.menu_share).setEnabled(!mAllRestricted);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit: {
                mListener.onEditRequested(mLookupUri);
                break;
            }
            case R.id.menu_delete: {
                showDeleteConfirmationDialog();
                return true;
            }
            case R.id.menu_options: {
                final Intent intent = new Intent(mContext, ContactOptionsActivity.class);
                intent.setData(mContactData.getLookupUri());
                mContext.startActivity(intent);
                return true;
            }
            case R.id.menu_share: {
                if (mAllRestricted) return false;

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

    private void showDeleteConfirmationDialog() {
        final int id;
        if (mReadOnlySourcesCnt > 0 & mWritableSourcesCnt > 0) {
            id = R.id.detail_dialog_confirm_readonly_delete;
        } else if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt == 0) {
            id = R.id.detail_dialog_confirm_readonly_hide;
        } else if (mReadOnlySourcesCnt == 0 && mWritableSourcesCnt > 1) {
            id = R.id.detail_dialog_confirm_multiple_delete;
        } else {
            id = R.id.detail_dialog_confirm_delete;
        }
        if (mListener != null) mListener.onDialogRequested(id, null);
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        // This can be null sometimes, don't crash...
        if (info == null) {
            Log.e(TAG, "bad menuInfo");
            return;
        }

        final ViewEntry entry = mAdapter.getEntry(info.position);
        menu.setHeaderTitle(R.string.contactOptionsTitle);
        if (entry.mimetype.equals(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
            menu.add(0, 0, 0, R.string.menu_call).setIntent(entry.intent);
            menu.add(0, 0, 0, R.string.menu_sendSMS).setIntent(entry.secondaryIntent);
            if (!entry.isPrimary) {
                menu.add(0, MENU_ITEM_MAKE_DEFAULT, 0, R.string.menu_makeDefaultNumber);
            }
        } else if (entry.mimetype.equals(CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
            menu.add(0, 0, 0, R.string.menu_sendEmail).setIntent(entry.intent);
            if (!entry.isPrimary) {
                menu.add(0, MENU_ITEM_MAKE_DEFAULT, 0, R.string.menu_makeDefaultEmail);
            }
        } else if (entry.mimetype.equals(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)) {
            menu.add(0, 0, 0, R.string.menu_viewAddress).setIntent(entry.intent);
        }
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mListener == null) return;
        final ViewEntry entry = mAdapter.getEntry(position);
        if (entry == null) return;
        final Intent intent = entry.intent;
        if (intent == null) return;
        mListener.onItemClicked(intent);
    }

    private final DialogInterface.OnClickListener mDeleteListener =
            new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            mContext.getContentResolver().delete(mContactData.getLookupUri(), null, null);
        }
    };

    public Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.detail_dialog_confirm_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, mDeleteListener)
                        .setCancelable(false)
                        .create();
            case R.id.detail_dialog_confirm_readonly_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, mDeleteListener)
                        .setCancelable(false)
                        .create();
            case R.id.detail_dialog_confirm_multiple_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, mDeleteListener)
                        .setCancelable(false)
                        .create();
            case R.id.detail_dialog_confirm_readonly_hide: {
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setPositiveButton(android.R.string.ok, mDeleteListener)
                        .create();
            }
            default:
                return null;
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_MAKE_DEFAULT: {
                if (makeItemDefault(item)) {
                    return true;
                }
                break;
            }
        }

        return false;
    }

    private boolean makeItemDefault(MenuItem item) {
        ViewEntry entry = getViewEntryForMenuItem(item);
        if (entry == null) {
            return false;
        }

        // Update the primary values in the data record.
        ContentValues values = new ContentValues(1);
        values.put(Data.IS_SUPER_PRIMARY, 1);

        mContext.getContentResolver().update(ContentUris.withAppendedId(Data.CONTENT_URI, entry.id),
                values, null, null);
        return true;
    }

    private ViewEntry getViewEntryForMenuItem(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return null;
        }

        return mAdapter.getEntry(info.position);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
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
                    final ViewEntry entry = mAdapter.getEntry(index);
                    if (entry != null &&
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
                showDeleteConfirmationDialog();
                return true;
            }
        }

        return false;
    }

    private ContactDetailHeaderView.Listener mHeaderViewListener =
            new ContactDetailHeaderView.Listener() {
        public void onDisplayNameClick(View view) {
        }

        public void onEditClicked() {
            if (mListener != null) mListener.onEditRequested(mLookupUri);
        }

        public void onPhotoClick(View view) {
        }
    };


    public static interface Listener {
        /**
         * Contact was not found, so somehow close this fragment.
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
         * Show a dialog using the globally unique id
         */
        public void onDialogRequested(int id, Bundle bundle);
    }
}
