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

import com.android.contacts.Collapser.Collapsible;
import com.android.contacts.ScrollingTabWidget.OnTabSelectionChangedListener;
import com.android.contacts.SplitAggregateView.OnContactSelectedListener;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.ui.FastTrackWindow;
import com.android.contacts.util.Constants;
import com.android.contacts.util.NotifyingAsyncQueryHandler;
import com.android.internal.telephony.ITelephony;
import com.android.internal.widget.ContactHeaderWidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.Entity.NamedContentValues;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Presence;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Displays the details of a specific contact.
 */
public class ViewContactActivity extends Activity
        implements View.OnCreateContextMenuListener, DialogInterface.OnClickListener,
        AdapterView.OnItemClickListener, NotifyingAsyncQueryHandler.AsyncQueryListener,
        OnTabSelectionChangedListener {
    private static final String TAG = "ViewContact";

    public static final String RAW_CONTACT_ID_EXTRA = "rawContactIdExtra";

    private static final boolean SHOW_SEPARATORS = false;

    private static final int DIALOG_CONFIRM_DELETE = 1;

    private static final int REQUEST_JOIN_CONTACT = 1;
    private static final int REQUEST_EDIT_CONTACT = 2;

    public static final int MENU_ITEM_MAKE_DEFAULT = 3;

    protected Uri mLookupUri;
    private Uri mUri;
    private ContentResolver mResolver;
    private ViewAdapter mAdapter;
    private int mNumPhoneNumbers = 0;

    private static final long ALL_CONTACTS_ID = -100;

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

    private SparseArray<Long> mTabRawContactIdMap;
    protected ScrollingTabWidget mTabWidget;
    protected ContactHeaderWidget mContactHeaderWidget;
    private NotifyingAsyncQueryHandler mHandler;

    protected LayoutInflater mInflater;

    //Projection used for the query that determines which tabs to add.
    protected static final String[] TAB_PROJECTION = new String[] {
        RawContacts._ID,
        RawContacts.ACCOUNT_NAME,
        RawContacts.ACCOUNT_TYPE
    };
    protected static final int TAB_CONTACT_ID_COLUMN_INDEX = 0;
    protected static final int TAB_ACCOUNT_NAME_COLUMN_INDEX = 1;
    protected static final int TAB_ACCOUNT_TYPE_COLUMN_INDEX = 2;

    protected static final String SELECTED_RAW_CONTACT_ID_KEY = "selectedRawContact";
    protected Long mSelectedRawContactId = null;

    private static final int TOKEN_QUERY = 0;

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mCursor != null && !mCursor.isClosed()) {
                startEntityQuery();
            }
        }
    };

    public void onClick(DialogInterface dialog, int which) {
        closeCursor();
        getContentResolver().delete(mUri, null, null);
        finish();
    }

    private FrameLayout mTabContentLayout;
    private ListView mListView;
    private boolean mShowSmsLinksForAllPhones;
    private ArrayList<Entity> mEntities = null;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();
        mLookupUri = intent.getData();

        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.contact_card_layout);

        mContactHeaderWidget = (ContactHeaderWidget) findViewById(R.id.contact_header_widget);
        mContactHeaderWidget.showStar(true);
        mContactHeaderWidget.setExcludeMimes(new String[] {
            Contacts.CONTENT_ITEM_TYPE
        });

        mTabWidget = (ScrollingTabWidget) findViewById(R.id.tab_widget);
        mTabWidget.setTabSelectionListener(this);
        mTabWidget.setVisibility(View.INVISIBLE);

        mTabRawContactIdMap = new SparseArray<Long>();

        mHandler = new NotifyingAsyncQueryHandler(this, this);

        mListView = new ListView(this);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        mListView.setOnItemClickListener(this);

        mTabContentLayout = (FrameLayout) findViewById(android.R.id.tabcontent);
        mTabContentLayout.addView(mListView);

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

        startEntityQuery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startEntityQuery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCursor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCursor();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mSelectedRawContactId = savedInstanceState.getLong(SELECTED_RAW_CONTACT_ID_KEY);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(SELECTED_RAW_CONTACT_ID_KEY, mSelectedRawContactId);
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


    // TAB CODE //
    /**
     * Adds a tab for each {@link RawContacts} associated with this contact.
     * Override this method if you want to additional tabs and/or different
     * tabs for your activity.
     */
    protected void bindTabs() {
        if (mEntities.size() > 1) {
            addAllTab();
        }

        final Sources sources = Sources.getInstance(this);

        for (Entity entity : mEntities) {
            final String accountType = entity.getEntityValues().
                    getAsString(RawContacts.ACCOUNT_TYPE);
            final Long rawContactId = entity.getEntityValues().
                    getAsLong(RawContacts._ID);

            // TODO: ensure inflation on background task so we don't block UI thread here
            final ContactsSource source = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_SUMMARY);
            addTab(rawContactId, ContactsUtils.createTabIndicatorView(mTabWidget.getTabParent(), source));
        }

        selectInitialTab();
        mTabWidget.setVisibility(View.VISIBLE);
        mTabWidget.postInvalidate();
    }

    /**
     * Add a tab to be displayed in the {@link ScrollingTabWidget}.
     *
     * @param rawContactId The contact id associated with the tab.
     * @param view A view to use as the tab indicator.
     */
    protected void addTab(long rawContactId, View view) {
        mTabRawContactIdMap.put(mTabWidget.getTabCount(), rawContactId);
        mTabWidget.addTab(view);
    }


    protected void clearCurrentTabs() {
        mTabRawContactIdMap.clear();
        mTabWidget.removeAllTabs();
    }

    protected void selectInitialTab() {
        int selectedTabIndex = 0;

        if (mSelectedRawContactId != null) {
            selectedTabIndex = getTabIndexForRawContactId(mSelectedRawContactId);
            if (selectedTabIndex == -1) {
                // If there was no matching tab, just select the first;
                selectedTabIndex = 0;
            }
        }

        mTabWidget.setCurrentTab(selectedTabIndex);
        onTabSelectionChanged(selectedTabIndex, false);
    }

    private void addAllTab() {
        View allTabIndicator = mInflater.inflate(R.layout.all_tab_indicator,
                mTabWidget.getTabParent(), false);
        allTabIndicator.getBackground().setDither(true);
        addTab(ALL_CONTACTS_ID, allTabIndicator);
    }

    public void onTabSelectionChanged(int tabIndex, boolean clicked) {
        long rawContactId = getTabRawContactId(tabIndex);
        mSelectedRawContactId = rawContactId;
        bindData();
    }

    /**
     * Return the RawContact id associated with the tab at an index.
     *
     * @param index The index of the tab in question.
     * @return The contactId associated with the tab at the specified index.
     */
    protected long getTabRawContactId(int index) {
        return mTabRawContactIdMap.get(index);
    }

    /**
     * Return the tab index associated with the RawContact id.
     *
     * @param index The index of the tab in question.
     * @return The contactId associated with the tab at the specified index.
     */
    protected int getTabIndexForRawContactId(long rawContactId) {
        int numTabs = mTabRawContactIdMap.size();
        for (int i=0; i < numTabs; i++) {
            if (mTabRawContactIdMap.get(i) == rawContactId) {
                return i;
            }
        }
        return -1;
    }


    // QUERY CODE //
    /** {@inheritDoc} */
    public void onQueryEntitiesComplete(int token, Object cookie, EntityIterator iterator) {
        try{
            if (token == TOKEN_QUERY) {
                clearCurrentTabs();
                mEntities = readEntities(iterator);
                bindTabs();
                bindData();
            }
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        // Empty
    }

    private ArrayList<Entity> readEntities(EntityIterator iterator) {
        ArrayList<Entity> entities = new ArrayList<Entity>();
        try {
            while (iterator.hasNext()) {
                entities.add(iterator.next());
            }
        } catch (RemoteException e) {
        }

        return entities;
    }

    private void startEntityQuery() {
        closeCursor();

        mUri = null;
        if (mLookupUri != null) {
            mLookupUri = Contacts.getLookupUri(getContentResolver(), mLookupUri);
            if (mLookupUri != null) {
                mUri = Contacts.lookupContact(getContentResolver(), mLookupUri);
            }
        }

        if (mUri == null) {

            // TODO either figure out a way to prevent a flash of black background or
            // use some other UI than a toast
            Toast.makeText(this, R.string.invalidContactMessage, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "invalid contact uri: " + mLookupUri);
            finish();
            return;
        }

        mCursor = mResolver.query(Uri.withAppendedPath(mUri, Contacts.Data.CONTENT_DIRECTORY),
                new String[] {Contacts.DISPLAY_NAME}, null, null, null);
        mCursor.registerContentObserver(mObserver);

        long contactId = ContentUris.parseId(mUri);
        mHandler.startQueryEntities(TOKEN_QUERY, null,
                RawContacts.CONTENT_URI, RawContacts.CONTACT_ID + "=" + contactId, null, null);

        mContactHeaderWidget.bindFromContactId(ContentUris.parseId(mUri));
    }

    private void closeCursor() {
        if (mCursor != null) {
            mCursor.unregisterContentObserver(mObserver);
            mCursor.close();
            mCursor = null;
        }
    }

    private void bindData() {

        // Build up the contact entries
        buildEntries();

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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.view, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // Only allow edit if we have a selected tab
        final boolean contactSelected = (mSelectedRawContactId != null);
        menu.findItem(R.id.menu_edit).setEnabled(contactSelected);

        // Only allow split when more than one contact
        final boolean isAggregate = (mRawContactIds.size() > 1);
        menu.findItem(R.id.menu_split).setEnabled(isAggregate);

        return true;
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
            case R.id.menu_edit: {
                Long rawContactIdToEdit = mSelectedRawContactId;
                if (rawContactIdToEdit == null) {
                    // This shouldn't be possible. We only show the edit option if
                    // this value is non-null.
                    break;
                }
                if (rawContactIdToEdit == ALL_CONTACTS_ID) {
                    // If the "all" tab is selected, edit the next tab.
                    rawContactIdToEdit = getTabRawContactId(mTabWidget.getCurrentTab() + 1);
                }
                Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                        rawContactIdToEdit);
                startActivityForResult(new Intent(Intent.ACTION_EDIT, rawContactUri),
                        REQUEST_EDIT_CONTACT);
                break;
            }
            case R.id.menu_delete: {
                // Get confirmation
                showDialog(DIALOG_CONFIRM_DELETE);
                return true;
            }
            case R.id.menu_split: {
                if (mRawContactIds.size() == 2) {
                    splitContact(mRawContactIds.get(1));
                } else {
                    showSplitAggregateDialog();
                }
                return true;
            }
            case R.id.menu_join: {
                showJoinAggregateActivity();
                return true;
            }
            case R.id.menu_options: {
                showOptionsActivity();
                return true;
            }
            case R.id.menu_share: {
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, mLookupUri);

                // Launch chooser to share contact via
                final CharSequence chooseTitle = getText(R.string.share_via);
                final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);

                try {
                    startActivity(chooseIntent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
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
        startEntityQuery();
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
            public void onContactSelected(long rawContactId) {
                dialog.dismiss();
                splitContact(rawContactId);
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
        startActivityForResult(intent, REQUEST_JOIN_CONTACT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case REQUEST_JOIN_CONTACT: {
                if (resultCode == RESULT_OK && intent != null) {
                    final long contactId = ContentUris.parseId(intent.getData());
                    joinAggregate(contactId);
                }
                break;
            }
            case REQUEST_EDIT_CONTACT: {
                if (resultCode == RESULT_OK && intent != null) {
                    long newInitialSelectedRawContactId = intent.getLongExtra(
                            RAW_CONTACT_ID_EXTRA, ALL_CONTACTS_ID);
                    if (newInitialSelectedRawContactId != mSelectedRawContactId) {
                        mSelectedRawContactId = newInitialSelectedRawContactId;
                        selectInitialTab();
                    }
                }
            }
        }
    }

    private void splitContact(long rawContactId) {
        setAggregationException(rawContactId, AggregationExceptions.TYPE_KEEP_SEPARATE);

        // The split operation may have removed the original aggregate contact, so we need
        // to requery everything
        Toast.makeText(this, R.string.contactsSplitMessage, Toast.LENGTH_LONG).show();
        startEntityQuery();
    }

    private void joinAggregate(final long contactId) {
        Cursor c = mResolver.query(RawContacts.CONTENT_URI, new String[] {RawContacts._ID},
                RawContacts.CONTACT_ID + "=" + contactId, null, null);

        try {
            while(c.moveToNext()) {
                long rawContactId = c.getLong(0);
                setAggregationException(rawContactId, AggregationExceptions.TYPE_KEEP_TOGETHER);
            }
        } finally {
            c.close();
        }

        Toast.makeText(this, R.string.contactsJoinedMessage, Toast.LENGTH_LONG).show();
        startEntityQuery();
    }

    /**
     * Given a contact ID sets an aggregation exception to either join the contact with the
     * current aggregate or split off.
     */
    protected void setAggregationException(long rawContactId, int exceptionType) {
        ContentValues values = new ContentValues(3);
        for (long aRawContactId : mRawContactIds) {
            if (aRawContactId != rawContactId) {
                values.put(AggregationExceptions.RAW_CONTACT_ID1, aRawContactId);
                values.put(AggregationExceptions.RAW_CONTACT_ID2, rawContactId);
                values.put(AggregationExceptions.TYPE, exceptionType);
                mResolver.update(AggregationExceptions.CONTENT_URI, values, null, null);
            }
        }
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
    private final void buildEntries() {
        // Clear out the old entries
        final int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        mRawContactIds.clear();

        Sources sources = Sources.getInstance(this);

        // Build up method entries
        if (mUri != null) {
            for (Entity entity: mEntities) {
                final ContentValues entValues = entity.getEntityValues();
                final String accountType = entValues.getAsString(RawContacts.ACCOUNT_TYPE);
                // TODO: entry.contactId should be renamed to entry.rawContactId
                long contactId = entValues.getAsLong(RawContacts._ID);

                for (NamedContentValues subValue : entity.getSubValues()) {
                    ViewEntry entry = new ViewEntry();

                    ContentValues entryValues = subValue.values;
                    final String mimetype = entryValues.getAsString(Data.MIMETYPE);
                    if (mimetype == null || accountType == null) {
                        continue;
                    }

                    ContactsSource contactsSource = sources.getInflatedSource(accountType,
                            ContactsSource.LEVEL_MIMETYPES);
                    if (contactsSource == null) {
                        continue;
                    }

                    DataKind kind = contactsSource.getKindForMimetype(mimetype);
                    if (kind == null) {
                        continue;
                    }

                    final long id = entryValues.getAsLong(Data._ID);
                    final Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);
                    entry.contactId = contactId;
                    entry.id = id;
                    entry.uri = uri;
                    entry.mimetype = mimetype;
                    entry.label = buildActionString(kind, entryValues, true);
                    entry.data = buildDataString(kind, entryValues);
                    if (kind.typeColumn != null && entryValues.containsKey(kind.typeColumn)) {
                        entry.type = entryValues.getAsInteger(kind.typeColumn);
                    }
                    if (kind.iconRes > 0) {
                        entry.actionIcon = kind.iconRes;
                    }

                    // Don't crash if the data is bogus
                    if (TextUtils.isEmpty(entry.data)) {
                        Log.w(TAG, "empty data for contact method " + id);
                        continue;
                    }

                    if (!mRawContactIds.contains(entry.contactId)) {
                        mRawContactIds.add(entry.contactId);
                    }

                    // This performs the tab filtering
                    if (mSelectedRawContactId != null
                            && mSelectedRawContactId != entry.contactId
                            && mSelectedRawContactId != ALL_CONTACTS_ID) {
                        continue;
                    }

                    if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimetype)
                            || CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mimetype)
                            || CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mimetype)
                            || CommonDataKinds.Im.CONTENT_ITEM_TYPE.equals(mimetype)) {
                        final boolean isSuperPrimary = entryValues.getAsInteger(
                                Data.IS_SUPER_PRIMARY) != 0;

                        if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimetype)) {
                            // Build phone entries
                            mNumPhoneNumbers++;

                            entry.intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                    Uri.fromParts("tel", entry.data, null));
                            entry.secondaryIntent = new Intent(Intent.ACTION_SENDTO,
                                    Uri.fromParts("sms", entry.data, null));
                            entry.data = PhoneNumberUtils.stripSeparators(entry.data);

                            // If data is empty, don't show it.
                            if (TextUtils.isEmpty(entry.data)) {
                                Log.w(TAG, "empty data for contact method " + id);
                                continue;
                            }

                            entry.isPrimary = isSuperPrimary;
                            mPhoneEntries.add(entry);

                            if (entry.type == CommonDataKinds.Phone.TYPE_MOBILE
                                    || mShowSmsLinksForAllPhones) {
                                // Add an SMS entry
                                if (kind.iconAltRes > 0) {
                                    entry.secondaryActionIcon = kind.iconAltRes;
                                }
                            }
                        } else if (CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mimetype)) {
                            // Build email entries
                            entry.intent = new Intent(Intent.ACTION_SENDTO,
                                    Uri.fromParts("mailto", entry.data, null));
                            // Temporary hack until we get real label resources for exchange.
                            if (TextUtils.isEmpty(entry.label)) {
                                entry.label = getString(R.string.email).toLowerCase();
                            }
                            entry.isPrimary = isSuperPrimary;
                            mEmailEntries.add(entry);
                        } else if (CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.
                                equals(mimetype)) {
                            // Build postal entries
                            entry.maxLines = 4;
                            entry.intent = new Intent(Intent.ACTION_VIEW, uri);
                            mPostalEntries.add(entry);
                        } else if (CommonDataKinds.Im.CONTENT_ITEM_TYPE.equals(mimetype)) {
                            // Build im entries
                            Object protocolObj = entryValues.getAsInteger(Data.DATA5);
                            String host = null;

                            if (TextUtils.isEmpty(entry.label)) {
                                entry.label = getString(R.string.chat_other).toLowerCase();
                            }

                            if (protocolObj instanceof Number) {
                                int protocol = ((Number) protocolObj).intValue();
                                host = ContactsUtils.lookupProviderNameFromId(
                                        protocol).toLowerCase();
                                if (protocol == CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK
                                        || protocol == CommonDataKinds.Im.PROTOCOL_MSN) {
                                    entry.maxLabelLines = 2;
                                }
                            } else if (protocolObj != null) {
                                String providerName = (String) protocolObj;
                                host = providerName.toLowerCase();
                            }

                            // Only add the intent if there is a valid host
                            if (!TextUtils.isEmpty(host)) {
                                entry.intent = new Intent(Intent.ACTION_SENDTO,
                                        constructImToUrl(host, entry.data));
                            }
                            //TODO(emillar) Add in presence info
                            /*if (!aggCursor.isNull(METHODS_STATUS_COLUMN)) {
                            entry.presenceIcon = Presence.getPresenceIconResourceId(
                                    aggCursor.getInt(METHODS_STATUS_COLUMN));
                            entry.status = ...
                        }*/
                            mImEntries.add(entry);
                        }
                    } else if (CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mimetype)) {
                        // Build organization entries
                        mOrganizationEntries.add(entry);
                    } else if (CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(mimetype)) {
                        // Build note entries
                        entry.id = 0;
                        entry.uri = null;
                        entry.intent = null;
                        entry.maxLines = 10;
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
    }

    String buildActionString(DataKind kind, ContentValues values, boolean lowerCase) {
        if (kind.actionHeader == null) {
            return null;
        }
        CharSequence actionHeader = kind.actionHeader.inflateUsing(this, values);
        if (actionHeader == null) {
            return null;
        }
        return lowerCase ? actionHeader.toString().toLowerCase() : actionHeader.toString();
    }

    String buildDataString(DataKind kind, ContentValues values) {
        if (kind.actionBody == null) {
            return null;
        }
        CharSequence actionBody = kind.actionBody.inflateUsing(this, values);
        return actionBody == null ? null : actionBody.toString();
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
                        || entry.mimetype.equals(Constants.MIME_SMS_ADDRESS)) {
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
