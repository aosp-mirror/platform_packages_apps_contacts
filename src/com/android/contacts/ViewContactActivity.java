/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;

import static com.android.contacts.ContactEntryAdapter.CONTACT_PHOTO_ID;
import static com.android.contacts.ContactEntryAdapter.CONTACT_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.CONTACT_STARRED_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_1_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_2_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_3_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_4_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_5_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_9_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_CONTACT_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_IS_SUPER_PRIMARY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.DATA_MIMETYPE_COLUMN;

import com.android.contacts.Collapser.Collapsible;
import com.android.contacts.SplitAggregateView.OnContactSelectedListener;
import com.android.contacts.ui.FastTrackWindow;
import com.android.internal.telephony.ITelephony;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Presence;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Displays the details of a specific contact.
 */
public class ViewContactActivity extends BaseContactCardActivity
        implements View.OnCreateContextMenuListener, DialogInterface.OnClickListener,
        AdapterView.OnItemClickListener {
    private static final String TAG = "ViewContact";
    private static final String SHOW_BARCODE_INTENT = "com.google.zxing.client.android.ENCODE";

    private static final boolean SHOW_SEPARATORS = false;

    private static final int DIALOG_CONFIRM_DELETE = 1;

    private static final int REQUEST_JOIN_AGGREGATE = 1;

    public static final int MENU_ITEM_DELETE = 1;
    public static final int MENU_ITEM_MAKE_DEFAULT = 2;
    public static final int MENU_ITEM_SHOW_BARCODE = 3;
    public static final int MENU_ITEM_SPLIT_AGGREGATE = 4;
    public static final int MENU_ITEM_JOIN_AGGREGATE = 5;
    public static final int MENU_ITEM_OPTIONS = 6;

    private Uri mUri;
    private Uri mAggDataUri;
    private ContentResolver mResolver;
    private ViewAdapter mAdapter;
    private int mNumPhoneNumbers = 0;

    private static final long ALL_CONTACTS_ID = -1;
    private long mRawContactId = ALL_CONTACTS_ID;

    /**
     * A list of distinct contact IDs included in the current contact.
     */
    private ArrayList<Long> mRawContactIds = new ArrayList<Long>();

    /* package */ ArrayList<ViewEntry> mPhoneEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mSmsEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mEmailEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mPostalEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mImEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mOrganizationEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mGroupEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mOtherEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ArrayList<ViewEntry>> mSections = new ArrayList<ArrayList<ViewEntry>>();

    private Cursor mCursor;
    private boolean mObserverRegistered;

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mCursor != null && !mCursor.isClosed()){
                dataChanged();
            }
        }
    };

    public void onClick(DialogInterface dialog, int which) {
        if (mCursor != null) {
            if (mObserverRegistered) {
                mCursor.unregisterContentObserver(mObserver);
                mObserverRegistered = false;
            }
            mCursor.close();
            mCursor = null;
        }
        getContentResolver().delete(mUri, null, null);
        finish();
    }

    private FrameLayout mTabContentLayout;
    private ListView mListView;
    private boolean mShowSmsLinksForAllPhones;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mListView = new ListView(this);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        mListView.setOnItemClickListener(this);

        mTabContentLayout = (FrameLayout) findViewById(android.R.id.tabcontent);
        mTabContentLayout.addView(mListView);

        mUri = getIntent().getData();
        mAggDataUri = Uri.withAppendedPath(mUri, "data");
        mResolver = getContentResolver();

        // Build the list of sections. The order they're added to mSections dictates the
        // order they are displayed in the list.
        mSections.add(mPhoneEntries);
        mSections.add(mSmsEntries);
        mSections.add(mEmailEntries);
        mSections.add(mImEntries);
        mSections.add(mPostalEntries);
        mSections.add(mOrganizationEntries);
        mSections.add(mGroupEntries);
        mSections.add(mOtherEntries);

        //TODO Read this value from a preference
        mShowSmsLinksForAllPhones = true;

        mCursor = mResolver.query(mAggDataUri,
                CONTACT_PROJECTION, null, null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mObserverRegistered = true;
        mCursor.registerContentObserver(mObserver);
        dataChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCursor != null) {
            if (mObserverRegistered) {
                mObserverRegistered = false;
                mCursor.unregisterContentObserver(mObserver);
            }
            mCursor.deactivate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mCursor != null) {
            if (mObserverRegistered) {
                mCursor.unregisterContentObserver(mObserver);
                mObserverRegistered = false;
            }
            mCursor.close();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CONFIRM_DELETE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, this)
                        .setCancelable(false)
                        .create();
        }
        return null;
    }

    @Override
    protected void bindTabs(Cursor tabsCursor) {
        if (tabsCursor.getCount() > 1) {
            addAllTab();
        }
        super.bindTabs(tabsCursor);
    }

    private void addAllTab() {
        View allTabIndicator = mInflater.inflate(R.layout.all_tab_indicator, mTabWidget, false);
        addTab(ALL_CONTACTS_ID, allTabIndicator);
    }

    public void onTabSelectionChanged(int tabIndex, boolean clicked) {
        long rawContactId = getTabRawContactId(tabIndex);
        mRawContactId = rawContactId;
        dataChanged();
    }

    private void dataChanged() {
        mCursor.requery();
        if (mCursor.moveToFirst()) {

            // Build up the contact entries
            buildEntries(mCursor);

            // Collapse similar data items in select sections.
            Collapser.collapseList(mPhoneEntries);
            Collapser.collapseList(mSmsEntries);
            Collapser.collapseList(mEmailEntries);
            Collapser.collapseList(mPostalEntries);

            if (mAdapter == null) {
                mAdapter = new ViewAdapter(this, mSections);
                mListView.setAdapter(mAdapter);
            } else {
                mAdapter.setSections(mSections, SHOW_SEPARATORS);
            }
        } else {
            Toast.makeText(this, R.string.invalidContactMessage, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "invalid contact uri: " + mUri);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, R.string.menu_editContact)
                .setIcon(android.R.drawable.ic_menu_edit)
                .setIntent(new Intent(Intent.ACTION_EDIT, mUri))
                .setAlphabeticShortcut('e');
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact)
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(0, MENU_ITEM_SPLIT_AGGREGATE, 0, R.string.menu_splitAggregate)
                .setIcon(android.R.drawable.ic_menu_share);
        menu.add(0, MENU_ITEM_JOIN_AGGREGATE, 0, R.string.menu_joinAggregate)
                .setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, MENU_ITEM_OPTIONS, 0, R.string.menu_contactOptions)
                .setIcon(R.drawable.ic_menu_mark);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Perform this check each time the menu is about to be shown, because the Barcode Scanner
        // could be installed or uninstalled at any time.
        if (isBarcodeScannerInstalled()) {
            if (menu.findItem(MENU_ITEM_SHOW_BARCODE) == null) {
                menu.add(0, MENU_ITEM_SHOW_BARCODE, 0, R.string.menu_showBarcode)
                        .setIcon(R.drawable.ic_menu_show_barcode);
            }
        } else {
            menu.removeItem(MENU_ITEM_SHOW_BARCODE);
        }

        boolean isAggregate = mRawContactIds.size() > 1;
        menu.findItem(MENU_ITEM_SPLIT_AGGREGATE).setEnabled(isAggregate);
        return true;
    }

    private boolean isBarcodeScannerInstalled() {
        final Intent intent = new Intent(SHOW_BARCODE_INTENT);
        ResolveInfo ri = getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
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

        ViewEntry entry = ContactEntryAdapter.getEntry(mSections, info.position, SHOW_SEPARATORS);
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
        // TODO(emillar): add back with group support.
        /* else if (entry.mimetype.equals()) {
            menu.add(0, 0, 0, R.string.menu_viewGroup).setIntent(entry.intent);
            } */
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_DELETE: {
                // Get confirmation
                showDialog(DIALOG_CONFIRM_DELETE);
                return true;
            }

            case MENU_ITEM_SPLIT_AGGREGATE: {
                showSplitAggregateDialog();
                return true;
            }

            case MENU_ITEM_JOIN_AGGREGATE: {
                showJoinAggregateActivity();
                return true;
            }

            case MENU_ITEM_OPTIONS: {
                showOptionsActivity();
                return true;
            }

            // TODO(emillar) Bring this back.
            /*case MENU_ITEM_SHOW_BARCODE:
                if (mCursor.moveToFirst()) {
                    Intent intent = new Intent(SHOW_BARCODE_INTENT);
                    intent.putExtra("ENCODE_TYPE", "CONTACT_TYPE");
                    Bundle bundle = new Bundle();
                    String name = mCursor.getString(AGGREGATE_DISPLAY_NAME_COLUMN);
                    if (!TextUtils.isEmpty(name)) {
                        // Correctly handle when section headers are hidden
                        int sepAdjust = SHOW_SEPARATORS ? 1 : 0;

                        bundle.putString(Contacts.Intents.Insert.NAME, name);
                        // The 0th ViewEntry in each ArrayList below is a separator item
                        int entriesToAdd = Math.min(mPhoneEntries.size() - sepAdjust, PHONE_KEYS.length);
                        for (int x = 0; x < entriesToAdd; x++) {
                            ViewEntry entry = mPhoneEntries.get(x + sepAdjust);
                            bundle.putString(PHONE_KEYS[x], entry.data);
                        }
                        entriesToAdd = Math.min(mEmailEntries.size() - sepAdjust, EMAIL_KEYS.length);
                        for (int x = 0; x < entriesToAdd; x++) {
                            ViewEntry entry = mEmailEntries.get(x + sepAdjust);
                            bundle.putString(EMAIL_KEYS[x], entry.data);
                        }
                        if (mPostalEntries.size() >= 1 + sepAdjust) {
                            ViewEntry entry = mPostalEntries.get(sepAdjust);
                            bundle.putString(Contacts.Intents.Insert.POSTAL, entry.data);
                        }
                        intent.putExtra("ENCODE_DATA", bundle);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            // The check in onPrepareOptionsMenu() should make this impossible, but
                            // for safety I'm catching the exception rather than crashing. Ideally
                            // I'd call Menu.removeItem() here too, but I don't see a way to get
                            // the options menu.
                            Log.e(TAG, "Show barcode menu item was clicked but Barcode Scanner " +
                                    "was not installed.");
                        }
                        return true;
                    }
                }
                break; */
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_MAKE_DEFAULT: {
                if (makeItemDefault(item)) {
                    return true;
                }
                break;
            }
        }

        return super.onContextItemSelected(item);
    }

    private boolean makeItemDefault(MenuItem item) {
        ViewEntry entry = getViewEntryForMenuItem(item);
        if (entry == null) {
            return false;
        }

        // Update the primary values in the data record.
        ContentValues values = new ContentValues(2);
        values.put(Data.IS_PRIMARY, 1);

        if (entry.ids.size() > 0) {
            for (int i = 0; i < entry.ids.size(); i++) {
                getContentResolver().update(ContentUris.withAppendedId(Data.CONTENT_URI,
                        entry.ids.get(i)),
                        values, null, null);
            }
        }

        values.put(Data.IS_SUPER_PRIMARY, 1);
        getContentResolver().update(ContentUris.withAppendedId(Data.CONTENT_URI, entry.id),
                values, null, null);
        dataChanged();
        return true;
    }

    /**
     * Shows a dialog that contains a list of all constituent contacts in this aggregate.
     * The user picks a contact to be split into its own aggregate or clicks Cancel.
     */
    private void showSplitAggregateDialog() {

        // Wrap this dialog in a specific theme so that list items have correct text color.
        final ContextThemeWrapper dialogContext =
                new ContextThemeWrapper(this, android.R.style.Theme_Light);
        AlertDialog.Builder builder =
                new AlertDialog.Builder(dialogContext);
        builder.setTitle(getString(R.string.splitAggregate_title));

        final SplitAggregateView view = new SplitAggregateView(dialogContext, mUri);
        builder.setView(view);

        builder.setInverseBackgroundForced(true);
        builder.setCancelable(true);
        builder.setNegativeButton(android.R.string.cancel,
                new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        final AlertDialog dialog = builder.create();

        view.setOnContactSelectedListener(new OnContactSelectedListener() {
            public void onContactSelected(long contactId) {
                dialog.dismiss();
                splitContact(contactId);
            }
        });

        dialog.show();
    }

    /**
     * Shows a list of aggregates that can be joined into the currently viewed aggregate.
     */
    public void showJoinAggregateActivity() {
        Intent intent = new Intent(ContactsListActivity.JOIN_AGGREGATE);
        intent.putExtra(ContactsListActivity.EXTRA_AGGREGATE_ID, ContentUris.parseId(mUri));
        startActivityForResult(intent, REQUEST_JOIN_AGGREGATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_JOIN_AGGREGATE && resultCode == RESULT_OK && intent != null) {
            final long aggregateId = ContentUris.parseId(intent.getData());
            joinAggregate(aggregateId);
        }
    }

    private void splitContact(long contactId) {
        setAggregationException(contactId, AggregationExceptions.TYPE_KEEP_OUT);
        Toast.makeText(this, R.string.contactsSplitMessage, Toast.LENGTH_SHORT).show();
        mAdapter.notifyDataSetChanged();
    }

    private void joinAggregate(final long aggregateId) {
        Cursor c = mResolver.query(RawContacts.CONTENT_URI, new String[] {RawContacts._ID},
                RawContacts.CONTACT_ID + "=" + aggregateId, null, null);

        try {
            while(c.moveToNext()) {
                long contactId = c.getLong(0);
                setAggregationException(contactId, AggregationExceptions.TYPE_KEEP_IN);
            }
        } finally {
            c.close();
        }

        Toast.makeText(this, R.string.contactsJoinedMessage, Toast.LENGTH_SHORT).show();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Given a contact ID sets an aggregation exception to either join the contact with the
     * current aggregate or split off.
     */
    protected void setAggregationException(long contactId, int exceptionType) {
        ContentValues values = new ContentValues(3);
        values.put(AggregationExceptions.CONTACT_ID, ContentUris.parseId(mUri));
        values.put(AggregationExceptions.CONTACT_ID, contactId);
        values.put(AggregationExceptions.TYPE, exceptionType);
        mResolver.update(AggregationExceptions.CONTENT_URI, values, null, null);
    }

    private void showOptionsActivity() {
        final Intent intent = new Intent(this, ContactOptionsActivity.class);
        intent.setData(mUri);
        startActivity(intent);
    }

    private ViewEntry getViewEntryForMenuItem(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return null;
        }

        return ContactEntryAdapter.getEntry(mSections, info.position, SHOW_SEPARATORS);
    }

    @Override
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
                    ViewEntry entry = ViewAdapter.getEntry(mSections, index, SHOW_SEPARATORS);
                    if (entry.intent.getAction() == Intent.ACTION_CALL_PRIVILEGED) {
                        startActivity(entry.intent);
                    }
                } else if (mNumPhoneNumbers != 0) {
                    // There isn't anything selected, call the default number
                    Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, mUri);
                    startActivity(intent);
                }
                return true;
            }

            case KeyEvent.KEYCODE_DEL: {
                showDialog(DIALOG_CONFIRM_DELETE);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    public void onItemClick(AdapterView parent, View v, int position, long id) {
        ViewEntry entry = ViewAdapter.getEntry(mSections, position, SHOW_SEPARATORS);
        if (entry != null) {
            Intent intent = entry.intent;
            if (intent != null) {
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "No activity found for intent: " + intent);
                    signalError();
                }
            } else {
                signalError();
            }
        } else {
            signalError();
        }
    }

    /**
     * Signal an error to the user via a beep, or some other method.
     */
    private void signalError() {
        //TODO: implement this when we have the sonification APIs
    }

    private Uri constructImToUrl(String host, String data) {
        // don't encode the url, because the Activity Manager can't find using the encoded url
        StringBuilder buf = new StringBuilder("imto://");
        buf.append(host);
        buf.append('/');
        buf.append(data);
        return Uri.parse(buf.toString());
    }

    /**
     * Build up the entries to display on the screen.
     *
     * @param personCursor the URI for the contact being displayed
     */
    private final void buildEntries(Cursor aggCursor) {
        // Clear out the old entries
        final int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        mRawContactIds.clear();

        // Build up method entries
        if (mUri != null) {
            aggCursor.moveToPosition(-1);
            while (aggCursor.moveToNext()) {
                final String mimetype = aggCursor.getString(DATA_MIMETYPE_COLUMN);

                ViewEntry entry = new ViewEntry();

                final long id = aggCursor.getLong(DATA_ID_COLUMN);
                final Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);
                entry.id = id;
                entry.uri = uri;
                entry.mimetype = mimetype;
                // TODO: entry.contactId should be renamed to entry.rawContactId
                entry.contactId = aggCursor.getLong(DATA_CONTACT_ID_COLUMN);
                if (!mRawContactIds.contains(entry.contactId)) {
                    mRawContactIds.add(entry.contactId);
                }

                // This performs the tab filtering
                if (mRawContactId != entry.contactId && mRawContactId != ALL_CONTACTS_ID) {
                    continue;
                }

                if (mimetype.equals(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        || mimetype.equals(CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        || mimetype.equals(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                        || mimetype.equals(CommonDataKinds.Im.CONTENT_ITEM_TYPE)) {
                    final int type = aggCursor.getInt(DATA_1_COLUMN);
                    final String label = aggCursor.getString(DATA_3_COLUMN);
                    final String data = aggCursor.getString(DATA_2_COLUMN);
                    final boolean isSuperPrimary = "1".equals(
                            aggCursor.getString(DATA_IS_SUPER_PRIMARY_COLUMN));

                    entry.type = type;

                    // Don't crash if the data is bogus
                    if (TextUtils.isEmpty(data)) {
                        Log.w(TAG, "empty data for contact method " + id);
                        continue;
                    }

                    // Build phone entries
                    if (mimetype.equals(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                        mNumPhoneNumbers++;

                        final CharSequence displayLabel = ContactsUtils.getDisplayLabel(
                                this, mimetype, type, label);
                        entry.label = buildActionString(R.string.actionCall, displayLabel, true);
                        entry.data = PhoneNumberUtils.stripSeparators(data);
                        entry.intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                Uri.fromParts("tel", data, null));
                        entry.secondaryIntent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts("sms", data, null));
                        entry.isPrimary = isSuperPrimary;
                        entry.actionIcon = android.R.drawable.sym_action_call;
                        mPhoneEntries.add(entry);

                        if (type == CommonDataKinds.Phone.TYPE_MOBILE
                                || mShowSmsLinksForAllPhones) {
                            // Add an SMS entry
                            entry.secondaryActionIcon = R.drawable.sym_action_sms;
                        }
                    // Build email entries
                    } else if (mimetype.equals(CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
                        entry.label = buildActionString(R.string.actionEmail,
                                ContactsUtils.getDisplayLabel(this, mimetype, type, label), true);
                        entry.data = data;
                        entry.intent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts("mailto", data, null));
                        entry.actionIcon = android.R.drawable.sym_action_email;
                        entry.isPrimary = isSuperPrimary;
                        mEmailEntries.add(entry);
                    // Build postal entries
                    } else if (mimetype.equals(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)) {
                        entry.label = buildActionString(R.string.actionMap,
                                ContactsUtils.getDisplayLabel(this, mimetype, type, label), true);
                        entry.data = data;
                        entry.maxLines = 4;
                        entry.intent = new Intent(Intent.ACTION_VIEW, uri);
                        entry.actionIcon = R.drawable.sym_action_map;
                        mPostalEntries.add(entry);
                    // Build im entries
                    } else if (mimetype.equals(CommonDataKinds.Im.CONTENT_ITEM_TYPE)) {
                        String[] protocolStrings = getResources().getStringArray(
                                android.R.array.imProtocols);
                        Object protocolObj = aggCursor.getString(DATA_5_COLUMN);
                        String host = null;
                        // TODO: fix by moving to contactssource-based rendering rules
//                      Object protocolObj = ContactsUtils.decodeImProtocol(
//                      aggCursor.getString(DATA_5_COLUMN));
//                        if (protocolObj instanceof Number) {
//                            int protocol = ((Number) protocolObj).intValue();
//                            entry.label = buildActionString(R.string.actionChat,
//                                    protocolStrings[protocol], false);
//                            host = ContactsUtils.lookupProviderNameFromId(
//                                    protocol).toLowerCase();
//                            if (protocol == CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK
//                                    || protocol == CommonDataKinds.Im.PROTOCOL_MSN) {
//                                entry.maxLabelLines = 2;
//                            }
//                        } else if (protocolObj != null) {
                            String providerName = (String) protocolObj;
                            entry.label = buildActionString(R.string.actionChat,
                                    providerName, false);
                            host = providerName.toLowerCase();
//                        }

                        // Only add the intent if there is a valid host
                        if (!TextUtils.isEmpty(host)) {
                            entry.intent = new Intent(Intent.ACTION_SENDTO,
                                    constructImToUrl(host, data));
                        }
                        entry.data = data;
                        //TODO(emillar) Add in presence info
                        /*if (!aggCursor.isNull(METHODS_STATUS_COLUMN)) {
                            entry.presenceIcon = Presence.getPresenceIconResourceId(
                                    aggCursor.getInt(METHODS_STATUS_COLUMN));
                            entry.status = ...
                        }*/
                        entry.actionIcon = android.R.drawable.sym_action_chat;
                        mImEntries.add(entry);
                    }
                // Build organization entries
                } else if (mimetype.equals(CommonDataKinds.Organization.CONTENT_ITEM_TYPE)) {
                    final String company = aggCursor.getString(DATA_3_COLUMN);
                    final String title = aggCursor.getString(DATA_4_COLUMN);

                    // Don't crash if the data is bogus
                    if (TextUtils.isEmpty(company) && TextUtils.isEmpty(title)) {
                        Log.w(TAG, "empty data for contact method " + id);
                        continue;
                    }

                    entry.data = title;
                    entry.actionIcon = R.drawable.sym_action_organization;
                    entry.label = company;

                    mOrganizationEntries.add(entry);
                // Build note entries
                } else if (mimetype.equals(CommonDataKinds.Note.CONTENT_ITEM_TYPE)) {
                    entry.label = getString(R.string.label_notes);
                    entry.data = aggCursor.getString(DATA_1_COLUMN);
                    entry.id = 0;
                    entry.uri = null;
                    entry.intent = null;
                    entry.maxLines = 10;
                    entry.actionIcon = R.drawable.sym_note;

                    if (TextUtils.isEmpty(entry.data)) {
                        Log.w(TAG, "empty data for contact method " + id);
                        continue;
                    }

                    mOtherEntries.add(entry);
                }


                // TODO(emillar) Add group entries
//              // Build the group entries
//              final Uri groupsUri = Uri.withAppendedPath(mUri, GroupMembership.CONTENT_DIRECTORY);
//              Cursor groupCursor = mResolver.query(groupsUri, ContactsListActivity.GROUPS_PROJECTION,
//                      null, null, Groups.DEFAULT_SORT_ORDER);
//              if (groupCursor != null) {
//                  try {
//                      StringBuilder sb = new StringBuilder();
//
//                      while (groupCursor.moveToNext()) {
//                          String systemId = groupCursor.getString(
//                                  ContactsListActivity.GROUPS_COLUMN_INDEX_SYSTEM_ID);
//
//                          if (systemId != null || Groups.GROUP_MY_CONTACTS.equals(systemId)) {
//                              continue;
//                          }
//
//                          String name = groupCursor.getString(ContactsListActivity.GROUPS_COLUMN_INDEX_NAME);
//                          if (!TextUtils.isEmpty(name)) {
//                              if (sb.length() == 0) {
//                                  sb.append(name);
//                              } else {
//                                  sb.append(getString(R.string.group_list, name));
//                              }
//                          }
//                      }
//
//                      if (sb.length() > 0) {
//                          ViewEntry entry = new ViewEntry();
//                          entry.kind = ContactEntryAdapter.Entry.KIND_GROUP;
//                          entry.label = getString(R.string.label_groups);
//                          entry.data = sb.toString();
//                          entry.intent = new Intent(Intent.ACTION_EDIT, mUri);
//
//                          // TODO: Add an icon for the groups item.
//
//                          mGroupEntries.add(entry);
//                      }
//                  } finally {
//                      groupCursor.close();
//                  }
//              }

            }
        }
    }

    String buildActionString(int actionResId, CharSequence type, boolean lowerCase) {
        // If there is no type just display an empty string
        if (type == null) {
            type = "";
        }

        if (lowerCase) {
            return getString(actionResId, type.toString().toLowerCase());
        } else {
            return getString(actionResId, type.toString());
        }
    }

    /**
     * A basic structure with the data for a contact entry in the list.
     */
    static class ViewEntry extends ContactEntryAdapter.Entry implements Collapsible<ViewEntry> {
        public int actionIcon = -1;
        public boolean isPrimary = false;
        public int presenceIcon = -1;
        public int secondaryActionIcon = -1;
        public Intent intent;
        public Intent secondaryIntent = null;
        public int status = -1;
        public int maxLabelLines = 1;
        public ArrayList<Long> ids = new ArrayList<Long>();
        public int collapseCount = 0;

        public boolean collapseWith(ViewEntry entry) {
            // assert equal collapse keys
            if (!getCollapseKey().equals(entry.getCollapseKey())) {
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
            if (Presence.getPresencePrecedence(status)
                    < Presence.getPresencePrecedence(entry.status)) {
                status = entry.status;
            }

            // If any of the collapsed entries are primary make the whole thing primary.
            isPrimary = entry.isPrimary ? true : isPrimary;

            // uri, and contactdId, shouldn't make a difference. Just keep the original.

            // Keep track of all the ids that have been collapsed with this one.
            ids.add(entry.id);
            collapseCount++;
            return true;
        }

        public String getCollapseKey() {
            StringBuilder hashSb = new StringBuilder();
            hashSb.append(data);
            hashSb.append(mimetype);
            hashSb.append((intent != null && intent.getAction() != null)
                    ? intent.getAction() : "");
            hashSb.append((secondaryIntent != null && secondaryIntent.getAction() != null)
                    ? secondaryIntent.getAction() : "");
            hashSb.append(actionIcon);
            return hashSb.toString();
        }
    }

    /** Cache of the children views of a row */
    static class ViewCache {
        public TextView label;
        public TextView data;
        public ImageView actionIcon;
        public ImageView presenceIcon;
        public ImageView primaryIcon;
        public ImageView secondaryActionButton;
        public View secondaryActionDivider;

        // Need to keep track of this too
        ViewEntry entry;
    }

    private final class ViewAdapter extends ContactEntryAdapter<ViewEntry>
            implements View.OnClickListener {


        ViewAdapter(Context context, ArrayList<ArrayList<ViewEntry>> sections) {
            super(context, sections, SHOW_SEPARATORS);
        }

        public void onClick(View v) {
            Intent intent = (Intent) v.getTag();
            startActivity(intent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewEntry entry = getEntry(mSections, position, false);
            View v;

            ViewCache views;

            // Check to see if we can reuse convertView
            if (convertView != null) {
                v = convertView;
                views = (ViewCache) v.getTag();
            } else {
                // Create a new view if needed
                v = mInflater.inflate(R.layout.list_item_text_icons, parent, false);

                // Cache the children
                views = new ViewCache();
                views.label = (TextView) v.findViewById(android.R.id.text1);
                views.data = (TextView) v.findViewById(android.R.id.text2);
                views.actionIcon = (ImageView) v.findViewById(R.id.action_icon);
                views.primaryIcon = (ImageView) v.findViewById(R.id.primary_icon);
                views.presenceIcon = (ImageView) v.findViewById(R.id.presence_icon);
                views.secondaryActionButton = (ImageView) v.findViewById(
                        R.id.secondary_action_button);
                views.secondaryActionButton.setOnClickListener(this);
                views.secondaryActionDivider = v.findViewById(R.id.divider);
                v.setTag(views);
            }

            // Update the entry in the view cache
            views.entry = entry;

            // Bind the data to the view
            bindView(v, entry);
            return v;
        }

        @Override
        protected View newView(int position, ViewGroup parent) {
            // getView() handles this
            throw new UnsupportedOperationException();
        }

        @Override
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
                        || entry.mimetype.equals(FastTrackWindow.MIME_SMS_ADDRESS)) {
                    data.setText(PhoneNumberUtils.formatNumber(entry.data));
                } else {
                    data.setText(entry.data);
                }
                setMaxLines(data, entry.maxLines);
            }

            // Set the primary icon
            views.primaryIcon.setVisibility(entry.isPrimary ? View.VISIBLE : View.GONE);

            // Set the action icon
            ImageView action = views.actionIcon;
            if (entry.actionIcon != -1) {
                action.setImageDrawable(resources.getDrawable(entry.actionIcon));
                action.setVisibility(View.VISIBLE);
            } else {
                // Things should still line up as if there was an icon, so make it invisible
                action.setVisibility(View.INVISIBLE);
            }

            // Set the presence icon
            Drawable presenceIcon = null;
            if (entry.presenceIcon != -1) {
                presenceIcon = resources.getDrawable(entry.presenceIcon);
            } else if (entry.status != -1) {
                presenceIcon = resources.getDrawable(
                        Presence.getPresenceIconResourceId(entry.status));
            }
            ImageView presenceIconView = views.presenceIcon;
            if (presenceIcon != null) {
                presenceIconView.setImageDrawable(presenceIcon);
                presenceIconView.setVisibility(View.VISIBLE);
            } else {
                presenceIconView.setVisibility(View.GONE);
            }

            // Set the secondary action button
            ImageView secondaryActionView = views.secondaryActionButton;
            Drawable secondaryActionIcon = null;
            if (entry.secondaryActionIcon != -1) {
                secondaryActionIcon = resources.getDrawable(entry.secondaryActionIcon);
            }
            if (entry.secondaryIntent != null && secondaryActionIcon != null) {
                secondaryActionView.setImageDrawable(secondaryActionIcon);
                secondaryActionView.setTag(entry.secondaryIntent);
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
    }
}
