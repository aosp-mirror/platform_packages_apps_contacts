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

package com.android.contacts.views.editor;

import com.android.contacts.ContactOptionsActivity;
import com.android.contacts.R;
import com.android.contacts.TypePrecedence;
import com.android.contacts.activities.ContactFieldEditorActivity;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.ui.EditContactActivity;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DataStatus;
import com.android.contacts.views.ContactLoader;

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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.zip.Inflater;

public class ContactEditorFragment extends LoaderManagingFragment<ContactLoader.Result>
        implements OnCreateContextMenuListener, OnItemClickListener {
    private static final String TAG = "ContactEditorFragment";

    private static final String BUNDLE_RAW_CONTACT_ID = "rawContactId";

    private static final int MENU_ITEM_MAKE_DEFAULT = 3;

    private static final int LOADER_DETAILS = 1;

    private Context mContext;
    private Uri mLookupUri;
    private Listener mListener;

    private ContactLoader.Result mContactData;
    private ContactEditorHeaderView mHeaderView;
    private ListView mListView;
    private ViewAdapter mAdapter;

    private int mReadOnlySourcesCnt;
    private int mWritableSourcesCnt;
    private boolean mAllRestricted;

    /**
     * A list of RawContacts included in this Contact.
     */
    private ArrayList<RawContact> mRawContacts = new ArrayList<RawContact>();

    private LayoutInflater mInflater;

    public ContactEditorFragment() {
        // Explicit constructor for inflation
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View view = inflater.inflate(R.layout.contact_editor_fragment, container, false);

        setHasOptionsMenu(true);
        
        mInflater = inflater;

        mHeaderView =
                (ContactEditorHeaderView) view.findViewById(R.id.contact_header_widget);

        mListView = (ListView) view.findViewById(android.R.id.list);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        mListView.setOnItemClickListener(this);

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
                if (data == ContactLoader.Result.ERROR) {
                    // Item has been deleted
                    Log.i(TAG, "Error fetching contact. Closing activity");
                    mListener.onError();
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
        // Build up the contact entries
        buildEntries();

        mHeaderView.setMergeInfo(mRawContacts.size());

        if (mAdapter == null) {
            mAdapter = new ViewAdapter();
            mListView.setAdapter(mAdapter);
        } else {
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Build up the entries to display on the screen.
     */
    private final void buildEntries() {
        // Clear out the old entries
        mRawContacts.clear();

        mReadOnlySourcesCnt = 0;
        mWritableSourcesCnt = 0;
        mAllRestricted = true;

        // TODO: This should be done in the background thread
        final Sources sources = Sources.getInstance(mContext);

        // Build up method entries
        if (mContactData == null) {
            return;
        }

        for (Entity entity: mContactData.getEntities()) {
            final ContentValues entValues = entity.getEntityValues();
            final String accountType = entValues.getAsString(RawContacts.ACCOUNT_TYPE);
            final String accountName = entValues.getAsString(RawContacts.ACCOUNT_NAME);
            final long rawContactId = entValues.getAsLong(RawContacts._ID);
            final String rawContactUriString = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                    rawContactId).toString();

            // Mark when this contact has any unrestricted components
            final boolean isRestricted = entValues.getAsInteger(RawContacts.IS_RESTRICTED) != 0;
            if (!isRestricted) mAllRestricted = false;

            final ContactsSource contactsSource = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_SUMMARY);
            final boolean writable = contactsSource == null || !contactsSource.readOnly;
            if (writable) {
                mWritableSourcesCnt += 1;
            } else {
                mReadOnlySourcesCnt += 1;
            }

            final RawContact rawContact =
                    new RawContact(contactsSource, accountName, rawContactId, writable);
            mRawContacts.add(rawContact);

            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                entryValues.put(Data.RAW_CONTACT_ID, rawContactId);

                final long dataId = entryValues.getAsLong(Data._ID);
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);
                if (mimeType == null) continue;

                final DataKind kind = sources.getKindOrFallback(accountType, mimeType, mContext,
                        ContactsSource.LEVEL_MIMETYPES);
                if (kind == null) continue;

                final DataViewEntry entry = new DataViewEntry(mContext, mimeType, kind,
                        rawContact, dataId, entryValues);

                final boolean hasData = !TextUtils.isEmpty(entry.data);
                final boolean isSuperPrimary = entryValues.getAsInteger(
                        Data.IS_SUPER_PRIMARY) != 0;

                final Intent itemEditIntent = new Intent(Intent.ACTION_EDIT, entry.uri);
                itemEditIntent.putExtra(ContactFieldEditorActivity.BUNDLE_RAW_CONTACT_URI,
                        rawContactUriString);
                entry.intent = itemEditIntent;
                entry.actionIcon = R.drawable.edit;

                if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    rawContact.getFields().add(entry);
                } else if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    rawContact.getFields().add(entry);
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Remember super-primary phone
                    entry.isPrimary = isSuperPrimary;
                    rawContact.getFields().add(entry);
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build email entries
                    entry.isPrimary = isSuperPrimary;
                    rawContact.getFields().add(entry);
                } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build postal entries
                    rawContact.getFields().add(entry);
                } else if (Im.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    // Build IM entries
                    if (TextUtils.isEmpty(entry.label)) {
                        entry.label = mContext.getString(R.string.chat).toLowerCase();
                    }
                    rawContact.getFields().add(entry);
                } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType) &&
                        (hasData || !TextUtils.isEmpty(entry.label))) {
                    entry.uri = null;

                    if (TextUtils.isEmpty(entry.label)) {
                        entry.label = entry.data;
                        entry.data = "";
                    }

                    rawContact.getFields().add(entry);
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    entry.uri = null;
                    rawContact.getFields().add(entry);
                } else if (Note.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    entry.uri = null;
                    entry.maxLines = 100;
                    rawContact.getFields().add(entry);
                } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                    entry.uri = null;
                    entry.maxLines = 10;
                    rawContact.getFields().add(entry);
                } else {
                    // Use social summary when requested by external source
                    final DataStatus status = mContactData.getStatuses().get(entry.id);
                    final boolean hasSocial = kind.actionBodySocial && status != null;

                    if (hasSocial || hasData) {
                        rawContact.getFields().add(entry);
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

    private abstract static class BaseViewEntry {
        private final RawContact mRawContact;

        public BaseViewEntry(RawContact rawContact) {
            mRawContact = rawContact;
        }

        public RawContact getRawContact() {
            return mRawContact;
        }

        public abstract int getEntryType();
        public abstract View getView(View convertView, ViewGroup parent);
    }

    private class HeaderViewEntry extends BaseViewEntry {
        private boolean mCollapsed;

        public HeaderViewEntry(RawContact rawContact) {
            super(rawContact);
        }

        public boolean isCollapsed() {
            return mCollapsed;
        }

        public void setCollapsed(boolean collapsed) {
            mCollapsed = collapsed;
        }

        @Override
        public int getEntryType() {
            return ItemTypes.RAW_CONTACT_HEADER;
        }

        @Override
        public View getView(View convertView, ViewGroup parent) {
            final View result;
            final HeaderItemViewCache viewCache;
            if (convertView != null) {
                result = convertView;
                viewCache = (HeaderItemViewCache) result.getTag();
            } else {
                result = mInflater.inflate(R.layout.list_edit_item_header, parent, false);
                viewCache = new HeaderItemViewCache();
                result.setTag(viewCache);
                viewCache.logo = (ImageView) result.findViewById(R.id.logo);
                viewCache.caption = (TextView) result.findViewById(R.id.caption);
            }

            CharSequence accountType = getRawContact().getSource().getDisplayLabel(mContext);
            if (TextUtils.isEmpty(accountType)) {
                accountType = mContext.getString(R.string.account_phone);
            }
            final String accountName = getRawContact().getAccountName();

            final String accountTypeDisplay;
            if (TextUtils.isEmpty(accountName)) {
                accountTypeDisplay = mContext.getString(R.string.account_type_format,
                        accountType);
            } else {
                accountTypeDisplay = mContext.getString(R.string.account_type_and_name,
                        accountType, accountName);
            }

            viewCache.caption.setText(accountTypeDisplay);
            viewCache.logo.setImageDrawable(getRawContact().getSource().getDisplayIcon(mContext));

            return result;
        }
    }

    private class FooterViewEntry extends BaseViewEntry {
        public FooterViewEntry(RawContact rawContact) {
            super(rawContact);
        }

        @Override
        public int getEntryType() {
            return ItemTypes.RAW_CONTACT_FOOTER;
        }

        @Override
        public View getView(View convertView, ViewGroup parent) {
            final View result;
            final FooterItemViewCache viewCache;
            if (convertView != null) {
                result = convertView;
                viewCache = (FooterItemViewCache) result.getTag();
            } else {
                result = mInflater.inflate(R.layout.list_edit_item_footer, parent, false);
                viewCache = new FooterItemViewCache();
                result.setTag(viewCache);
                viewCache.addInformationButton =
                    (Button) result.findViewById(R.id.add_information);
                viewCache.separateButton =
                    (Button) result.findViewById(R.id.separate);
                viewCache.deleteButton =
                    (Button) result.findViewById(R.id.deleteButton);
                viewCache.addInformationButton.setOnClickListener(
                        mAddInformationButtonClickListener);
            }

            viewCache.viewEntry = this;
            return result;
        }
    }

    private class DataViewEntry extends BaseViewEntry {
        public String label;
        public String data;
        public Uri uri;
        public long id = 0;
        public int maxLines = 1;
        public String mimetype;

        public int actionIcon = -1;
        public boolean isPrimary = false;
        public Intent intent;
        public Intent secondaryIntent = null;
        public int maxLabelLines = 1;
        public byte[] binaryData = null;

        /**
         * Build new {@link DataViewEntry} and populate from the given values.
         */
        public DataViewEntry(Context context, String mimeType, DataKind kind,
                RawContact rawContact, long dataId, ContentValues values) {
            super(rawContact);
            id = dataId;
            uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);
            mimetype = mimeType;
            label = buildActionString(kind, values, false, context);
            data = buildDataString(kind, values, context);
            binaryData = values.getAsByteArray(Data.DATA15);
        }

        @Override
        public int getEntryType() {
            return Photo.CONTENT_ITEM_TYPE.equals(mimetype) ? ItemTypes.PHOTO : ItemTypes.DATA;
        }

        @Override
        public View getView(View convertView, ViewGroup parent) {
            final View result;
            if (Photo.CONTENT_ITEM_TYPE.equals(mimetype)) {
                final PhotoItemViewCache viewCache;
                if (convertView != null) {
                    result = convertView;
                    viewCache = (PhotoItemViewCache) result.getTag();
                } else {
                    // Create a new view if needed
                    result = mInflater.inflate(R.layout.list_edit_item_photo, parent, false);

                    // Cache the children
                    viewCache = new PhotoItemViewCache();
                    viewCache.photo = (ImageView) result.findViewById(R.id.photo);
                    viewCache.galleryActionButton =
                            (ImageView) result.findViewById(R.id.action_icon);
                    viewCache.takePhotoActionButton =
                            (ImageView) result.findViewById(R.id.secondary_action_button);
                    result.setTag(viewCache);
                }
                final Bitmap bitmap = binaryData != null
                        ? BitmapFactory.decodeByteArray(binaryData, 0, binaryData.length)
                        : null;
                viewCache.photo.setImageBitmap(bitmap);
            } else {
                final DataItemViewCache viewCache;
                if (convertView != null) {
                    result = convertView;
                    viewCache = (DataItemViewCache) result.getTag();
                } else {
                    // Create a new view if needed
                    result = mInflater.inflate(R.layout.list_edit_item_text_icons, parent,
                            false);

                    // Cache the children
                    viewCache = new DataItemViewCache();
                    viewCache.label = (TextView) result.findViewById(android.R.id.text1);
                    viewCache.data = (TextView) result.findViewById(android.R.id.text2);
                    viewCache.actionIcon = (ImageView) result.findViewById(R.id.action_icon);
                    viewCache.primaryIcon = (ImageView) result.findViewById(R.id.primary_icon);
                    viewCache.secondaryActionButton = (ImageView) result.findViewById(
                            R.id.secondary_action_button);
                    viewCache.secondaryActionDivider = result.findViewById(R.id.divider);
                    result.setTag(viewCache);
                }
                final Resources resources = mContext.getResources();

                // Set the label
                setMaxLines(viewCache.label, maxLabelLines);
                viewCache.label.setText(label);

                if (data != null) {
                    if (Phone.CONTENT_ITEM_TYPE.equals(mimetype)
                            || Constants.MIME_SMS_ADDRESS.equals(mimetype)) {
                        viewCache.data.setText(PhoneNumberUtils.formatNumber(data));
                    } else {
                        viewCache.data.setText(data);
                    }
                    setMaxLines(viewCache.data, maxLines);
                }

                // Set the primary icon
                viewCache.primaryIcon.setVisibility(isPrimary ? View.VISIBLE : View.GONE);

                // Set the action icon
                final ImageView action = viewCache.actionIcon;
                if (intent != null) {
                    action.setImageDrawable(resources.getDrawable(actionIcon));
                    action.setVisibility(View.VISIBLE);
                } else {
                    action.setVisibility(View.INVISIBLE);
                }

                // Set the secondary action button
                final ImageView secondaryActionView = viewCache.secondaryActionButton;
                secondaryActionView.setVisibility(View.GONE);
                viewCache.secondaryActionDivider.setVisibility(View.GONE);
            }
            return result;
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
    }

    /** Cache of the header of a raw contact */
    private static class HeaderItemViewCache {
        public ImageView logo;
        public TextView caption;
    }

    /** Cache of the footer of a raw contact */
    private static class FooterItemViewCache {
        public Button addInformationButton;
        public Button separateButton;
        public Button deleteButton;

        public FooterViewEntry viewEntry;
    }

    /** Cache of the children views of a row */
    private static class DataItemViewCache {
        public TextView label;
        public TextView data;
        public ImageView actionIcon;
        public ImageView primaryIcon;
        public ImageView secondaryActionButton;
        public View secondaryActionDivider;
    }

    /** Cache of the children views of a row */
    private static class PhotoItemViewCache {
        public ImageView photo;
        public ImageView takePhotoActionButton;
        public ImageView galleryActionButton;
    }

    /** Possible Item Types */
    private interface ItemTypes {
        public static final int DATA = 0;
        public static final int PHOTO = 1;
        public static final int RAW_CONTACT_HEADER = 2;
        public static final int RAW_CONTACT_FOOTER = 3;
        public static final int _COUNT = 4;
    }

    private final class ViewAdapter extends BaseAdapter {
        public View getView(int position, View convertView, ViewGroup parent) {
            final View result;

            final BaseViewEntry viewEntry = getEntry(position);
            return viewEntry.getView(convertView, parent);
        }

        public Object getItem(int position) {
            return getEntry(position);
        }

        public long getItemId(int position) {
            // TODO Get a unique Id. Use negative numbers for Headers/Footers
            return position;
        }

        private BaseViewEntry getEntry(int position) {
            for (int i = 0; i < mRawContacts.size(); i++) {
                final RawContact rawContact = mRawContacts.get(i);
                if (position == 0) return rawContact.getHeader();

                // Collapsed header? Count one item and continue
                if (rawContact.getHeader().isCollapsed()) {
                    position--;
                    continue;
                }

                final ArrayList<DataViewEntry> fields = rawContact.getFields();
                // +1 for header, +1 for footer
                final int fieldCount = fields.size() + 2;
                if (position == fieldCount - 1) {
                    return rawContact.getFooter();
                }
                if (position < fieldCount) {
                    // -1 for header
                    return fields.get(position - 1);
                }
                position -= fieldCount;
            }
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return ItemTypes._COUNT;
        }

        @Override
        public int getItemViewType(int position) {
            return getEntry(position).getEntryType();
        }

        public int getCount() {
            int result = 0;
            for (int i = 0; i < mRawContacts.size(); i++) {
                final RawContact rawContact = mRawContacts.get(i);
                if (rawContact.getHeader().isCollapsed()) {
                    // just one header item
                    result++;
                } else {
                    // +1 for header, +1 for footer
                    result += rawContact.getFields().size() + 2;
                }
            }
            return result;
        }
    }

    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view, menu);
    }

    public void onPrepareOptionsMenu(Menu menu) {
        // TODO: Prepare options
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit: {
                // TODO: This is temporary code to invoke the old editor. We can get rid of this
                // later
                final Intent intent = new Intent();
                intent.setClass(mContext, EditContactActivity.class);
                final long rawContactId = mRawContacts.get(0).mId;
                final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                        rawContactId);
                intent.setAction(Intent.ACTION_EDIT);
                intent.setData(rawContactUri);
                startActivity(intent);
                return true;
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
        final int dialogId;
        if (mReadOnlySourcesCnt > 0 & mWritableSourcesCnt > 0) {
            dialogId = R.id.detail_dialog_confirm_readonly_delete;
        } else if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt == 0) {
            dialogId = R.id.detail_dialog_confirm_readonly_hide;
        } else if (mReadOnlySourcesCnt == 0 && mWritableSourcesCnt > 1) {
            dialogId = R.id.detail_dialog_confirm_multiple_delete;
        } else {
            dialogId = R.id.detail_dialog_confirm_delete;
        }
        if (mListener != null) mListener.onDialogRequested(dialogId, null);
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

        final BaseViewEntry baseEntry = mAdapter.getEntry(info.position);
        if (baseEntry instanceof DataViewEntry) {
            final DataViewEntry entry = (DataViewEntry) baseEntry;
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
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mListener == null) return;
        final BaseViewEntry baseEntry = mAdapter.getEntry(position);
        if (baseEntry == null) return;

        if (baseEntry instanceof HeaderViewEntry) {
            // Toggle rawcontact visibility
            final HeaderViewEntry entry = (HeaderViewEntry) baseEntry;
            entry.setCollapsed(!entry.isCollapsed());
            mAdapter.notifyDataSetChanged();
        } else if (baseEntry instanceof DataViewEntry) {
            final DataViewEntry entry = (DataViewEntry) baseEntry;
            final Intent intent = entry.intent;
            if (intent == null) return;
            mListener.onItemClicked(intent);
        }
    }

    private final DialogInterface.OnClickListener mDeleteListener =
            new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            mContext.getContentResolver().delete(mContactData.getLookupUri(), null, null);
        }
    };

    public Dialog onCreateDialog(int id, Bundle bundle) {
        // TODO The delete dialogs mirror the functionality from the Contact-Detail-Fragment.
        //      Consider whether we can extract common logic here
        // TODO The actual removal is not in a worker thread currently
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
            case R.id.edit_dialog_add_information: {
                final long rawContactId = bundle.getLong(BUNDLE_RAW_CONTACT_ID);
                final RawContact rawContact = findRawContactById(rawContactId);
                if (rawContact == null) return null;
                final ContactsSource source = rawContact.getSource();

                final ArrayList<DataKind> sortedDataKinds = source.getSortedDataKinds();
                final ArrayList<String> items = new ArrayList<String>(sortedDataKinds.size());
                for (DataKind dataKind : sortedDataKinds) {
                    // TODO: Filter out fields that do not make sense in the current Context
                    //       (Name, Photo, Notes etc)
                    if (dataKind.titleRes == -1) continue;
                    if (!dataKind.editable) continue;
                    final String title = mContext.getString(dataKind.titleRes);
                    items.add(title);
                }
                final DialogInterface.OnClickListener itemClickListener =
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO: Launch Intent to show Dialog
//                        final KindSectionView view = (KindSectionView) mFields.getChildAt(which);
//                        view.addItem();
                    }
                };
                return new AlertDialog.Builder(mContext)
                        .setItems(items.toArray(new String[0]), itemClickListener)
                        .create();
            }
            default:
                return null;
        }
    }

    private RawContact findRawContactById(long rawContactId) {
        for (RawContact rawContact : mRawContacts) {
            if (rawContact.getId() == rawContactId) return rawContact;
        }
        return null;
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
        final BaseViewEntry baseEntry = getViewEntryForMenuItem(item);
        if (baseEntry == null || !(baseEntry instanceof DataViewEntry)) {
            return false;
        }
        final DataViewEntry entry = (DataViewEntry) baseEntry;

        // Update the primary values in the data record.
        ContentValues values = new ContentValues(1);
        values.put(Data.IS_SUPER_PRIMARY, 1);

        mContext.getContentResolver().update(ContentUris.withAppendedId(Data.CONTENT_URI, entry.id),
                values, null, null);
        return true;
    }

    private BaseViewEntry getViewEntryForMenuItem(MenuItem item) {
        final AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return null;
        }

        return mAdapter.getEntry(info.position);
    }

    public OnClickListener mAddInformationButtonClickListener = new OnClickListener() {
        public void onClick(View v) {
            // The parent of the Button allows identifying the section
            final View parentView = (View) v.getParent();
            final FooterItemViewCache viewCache = (FooterItemViewCache) parentView.getTag();
            final FooterViewEntry entry = viewCache.viewEntry;
            final RawContact rawContact = entry.getRawContact();

            // Create a bundle to show the Dialog
            final Bundle bundle = new Bundle();
            bundle.putLong(BUNDLE_RAW_CONTACT_ID, rawContact.getId());
            if (mListener != null) mListener.onDialogRequested(R.id.edit_dialog_add_information,
                    bundle);
        }
    };

    private class RawContact {
        private final ContactsSource mSource;
        private String mAccountName;
        private final long mId;
        private boolean mWritable;
        private final HeaderViewEntry mHeader = new HeaderViewEntry(this);
        private final FooterViewEntry mFooter = new FooterViewEntry(this);
        private final ArrayList<DataViewEntry> mFields = new ArrayList<DataViewEntry>();

        public RawContact(ContactsSource source, String accountName, long id, boolean writable) {
            mSource = source;
            mAccountName = accountName;
            mId = id;
            mWritable = writable;
        }

        public ContactsSource getSource() {
            return mSource;
        }

        public String getAccountName() {
            return mAccountName;
        }

        public long getId() {
            return mId;
        }

        public boolean isWritable() {
            return mWritable;
        }

        public ArrayList<DataViewEntry> getFields() {
            return mFields;
        }

        public HeaderViewEntry getHeader() {
            return mHeader;
        }

        public FooterViewEntry getFooter() {
            return mFooter;
        }
    }

    public static interface Listener {
        /**
         * Contact was not found, so somehow close this fragment.
         */
        public void onContactNotFound();

        /**
         * There was an error loading the contact
         */
        public void onError();

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
