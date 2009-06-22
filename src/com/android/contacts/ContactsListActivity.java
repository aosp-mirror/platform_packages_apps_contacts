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

import com.android.contacts.DisplayGroupsActivity.Prefs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Aggregates;
import android.provider.ContactsContract.Aggregates.AggregationSuggestions;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Postal;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.UI;
import android.provider.ContactsContract.Presence;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

/*TODO(emillar) I commented most of the code that deals with modes and filtering. It should be
 * brought back in as we add back that functionality.
 */


/**
 * Displays a list of contacts. Usually is embedded into the ContactsActivity.
 */
public final class ContactsListActivity extends ListActivity implements
        View.OnCreateContextMenuListener {
    private static final String TAG = "ContactsListActivity";

    private static final String LIST_STATE_KEY = "liststate";
    private static final String FOCUS_KEY = "focused";

    static final int MENU_ITEM_VIEW_CONTACT = 1;
    static final int MENU_ITEM_CALL = 2;
    static final int MENU_ITEM_EDIT_BEFORE_CALL = 3;
    static final int MENU_ITEM_SEND_SMS = 4;
    static final int MENU_ITEM_SEND_IM = 5;
    static final int MENU_ITEM_EDIT = 6;
    static final int MENU_ITEM_DELETE = 7;
    static final int MENU_ITEM_TOGGLE_STAR = 8;

    public static final int MENU_SEARCH = 1;
    public static final int MENU_DIALER = 9;
    public static final int MENU_NEW_CONTACT = 10;
    public static final int MENU_DISPLAY_GROUP = 11;

    private static final int SUBACTIVITY_NEW_CONTACT = 1;
    private static final int SUBACTIVITY_VIEW_CONTACT = 2;
    private static final int SUBACTIVITY_DISPLAY_GROUP = 3;

    /**
     * The action for the join contact activity.
     * <p>
     * Input: extra field {@link #EXTRA_AGGREGATE_ID} is the aggregate ID.
     *
     * TODO: move to {@link ContactsContract}.
     */
    public static final String JOIN_AGGREGATE =
            "com.android.contacts.action.JOIN_AGGREGATE";

    /**
     * Used with {@link #JOIN_AGGREGATE} to give it the target for aggregation.
     * <p>
     * Type: LONG
     */
    public static final String EXTRA_AGGREGATE_ID =
            "com.android.contacts.action.AGGREGATE_ID";

    /** Mask for picker mode */
    static final int MODE_MASK_PICKER = 0x80000000;
    /** Mask for no presence mode */
    static final int MODE_MASK_NO_PRESENCE = 0x40000000;
    /** Mask for enabling list filtering */
    static final int MODE_MASK_NO_FILTER = 0x20000000;
    /** Mask for having a "create new contact" header in the list */
    static final int MODE_MASK_CREATE_NEW = 0x10000000;
    /** Mask for showing photos in the list */
    static final int MODE_MASK_SHOW_PHOTOS = 0x08000000;
    /** Mask for hiding additional information e.g. primary phone number in the list */
    static final int MODE_MASK_NO_DATA = 0x04000000;

    /** Unknown mode */
    static final int MODE_UNKNOWN = 0;
    /** Default mode */
    static final int MODE_DEFAULT = 4;
    /** Custom mode */
    static final int MODE_CUSTOM = 8;
    /** Show all starred contacts */
    static final int MODE_STARRED = 20;
    /** Show frequently contacted contacts */
    static final int MODE_FREQUENT = 30;
    /** Show starred and the frequent */
    static final int MODE_STREQUENT = 35;
    /** Show all contacts and pick them when clicking */
    static final int MODE_PICK_AGGREGATE = 40 | MODE_MASK_PICKER;
    /** Show all contacts as well as the option to create a new one */
    static final int MODE_PICK_OR_CREATE_AGGREGATE = 42 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW;
    /** Show all contacts and pick them when clicking, and allow creating a new contact */
    static final int MODE_INSERT_OR_EDIT_CONTACT = 45 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW;
    /** Show all phone numbers and pick them when clicking */
    static final int MODE_PICK_PHONE = 50 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE;
    /** Show all postal addresses and pick them when clicking */
    static final int MODE_PICK_POSTAL =
            55 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Run a search query */
    static final int MODE_QUERY = 60 | MODE_MASK_NO_FILTER;
    /** Run a search query in PICK mode, but that still launches to VIEW */
    // TODO Remove this mode if we decided it is really not needed.
    /*static final int MODE_QUERY_PICK_TO_VIEW = 65 | MODE_MASK_NO_FILTER | MODE_MASK_PICKER;*/

    /** Show join suggestions followed by an A-Z list */
    static final int MODE_JOIN_AGGREGATE = 70 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE
            | MODE_MASK_NO_DATA;

    /** Maximum number of suggestions shown for joining aggregates */
    static final int MAX_SUGGESTIONS = 4;

    static final String NAME_COLUMN = Aggregates.DISPLAY_NAME;
    //static final String SORT_STRING = People.SORT_STRING;

    static final String[] AGGREGATES_PROJECTION = new String[] {
        Aggregates._ID, // 0
        Aggregates.DISPLAY_NAME, // 1
        Aggregates.STARRED, //2
        Aggregates.PRIMARY_PHONE_ID, //3
        Aggregates.PRIMARY_EMAIL_ID, //4
    };

    static final String[] AGGREGATES_SUMMARY_PROJECTION = new String[] {
        Aggregates._ID, // 0
        Aggregates.DISPLAY_NAME, // 1
        Aggregates.STARRED, //2
        Aggregates.PRIMARY_PHONE_ID, //3
        Aggregates.TIMES_CONTACTED, //4
        Presence.PRESENCE_STATUS, //5
        CommonDataKinds.Phone.TYPE, //6
        CommonDataKinds.Phone.LABEL, //7
        CommonDataKinds.Phone.NUMBER, //8
    };
    static final int ID_COLUMN_INDEX = 0;
    static final int SUMMARY_NAME_COLUMN_INDEX = 1;
    static final int SUMMARY_STARRED_COLUMN_INDEX = 2;
    static final int PRIMARY_PHONE_ID_COLUMN_INDEX = 3;
    static final int SUMMARY_TIMES_CONTACTED_COLUMN_INDEX = 4;
    static final int SUMMARY_PRESENCE_STATUS_COLUMN_INDEX = 5;
    static final int PRIMARY_PHONE_TYPE_COLUMN_INDEX = 6;
    static final int PRIMARY_PHONE_LABEL_COLUMN_INDEX = 7;
    static final int PRIMARY_PHONE_NUMBER_COLUMN_INDEX = 8;

    static final String[] PHONES_PROJECTION = new String[] {
        Data._ID, //0
        CommonDataKinds.Phone.TYPE, //1
        CommonDataKinds.Phone.LABEL, //2
        CommonDataKinds.Phone.NUMBER, //3
        Aggregates.DISPLAY_NAME, // 4
    };
    static final int PHONE_ID_COLUMN_INDEX = 0;
    static final int PHONE_TYPE_COLUMN_INDEX = 1;
    static final int PHONE_LABEL_COLUMN_INDEX = 2;
    static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 4;

    static final String[] POSTALS_PROJECTION = new String[] {
        Data._ID, //0
        CommonDataKinds.Postal.TYPE, //1
        CommonDataKinds.Postal.LABEL, //2
        CommonDataKinds.Postal.DATA, //3
        Aggregates.DISPLAY_NAME, // 4
    };
    static final int POSTAL_ID_COLUMN_INDEX = 0;
    static final int POSTAL_TYPE_COLUMN_INDEX = 1;
    static final int POSTAL_LABEL_COLUMN_INDEX = 2;
    static final int POSTAL_ADDRESS_COLUMN_INDEX = 3;
    static final int POSTAL_DISPLAY_NAME_COLUMN_INDEX = 4;

    private static final int QUERY_TOKEN = 42;

    /*
    */
    ContactItemListAdapter mAdapter;

    int mMode = MODE_DEFAULT;

    private QueryHandler mQueryHandler;
    private String mQuery;
    private boolean mJustCreated;
    private boolean mSyncEnabled;

    private boolean mDisplayAll;
    private boolean mDisplayOnlyPhones;

    /**
     * Cursor row index that holds reference back to {@link People#_ID}, such as
     * {@link ContactMethods#PERSON_ID}. Used when responding to a
     * {@link Intent#ACTION_SEARCH} in mode {@link #MODE_QUERY_PICK_TO_VIEW}.
     */
    private int mQueryPersonIdIndex;

    private long mQueryAggregateId;

    /**
     * Used to keep track of the scroll state of the list.
     */
    private Parcelable mListState = null;
    private boolean mListHasFocus;

    private boolean mCreateShortcut;

    /**
     * Internal query type when in mode {@link #MODE_QUERY_PICK_TO_VIEW}.
     */
    private int mQueryMode = QUERY_MODE_NONE;

    private static final int QUERY_MODE_NONE = -1;
    private static final int QUERY_MODE_MAILTO = 1;
    private static final int QUERY_MODE_TEL = 2;

    /**
     * Data to use when in mode {@link #MODE_QUERY_PICK_TO_VIEW}. Usually
     * provided by scheme-specific part of incoming {@link Intent#getData()}.
     */
    private String mQueryData;

    private static final String CLAUSE_ONLY_VISIBLE = Aggregates.IN_VISIBLE_GROUP + "=1";
    private static final String CLAUSE_ONLY_PHONES = Aggregates.PRIMARY_PHONE_ID + " IS NOT NULL";

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        private Uri mUri;

        public DeleteClickListener(Uri uri) {
            mUri = uri;
        }

        public void onClick(DialogInterface dialog, int which) {
            getContentResolver().delete(mUri, null, null);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Resolve the intent
        final Intent intent = getIntent();

        // Allow the title to be set to a custom String using an extra on the intent
        String title = intent.getStringExtra(UI.TITLE_EXTRA_KEY);
        if (title != null) {
            setTitle(title);
        }

        final String action = intent.getAction();
        mMode = MODE_UNKNOWN;

        setContentView(R.layout.contacts_list_content);

        Log.i(TAG, "Called with action: " + action);
        if (UI.LIST_DEFAULT.equals(action)) {
            mMode = MODE_DEFAULT;
            // When mDefaultMode is true the mode is set in onResume(), since the preferneces
            // activity may change it whenever this activity isn't running
        } /* else if (UI.LIST_GROUP_ACTION.equals(action)) {
            mMode = MODE_GROUP;
            String groupName = intent.getStringExtra(UI.GROUP_NAME_EXTRA_KEY);
            if (TextUtils.isEmpty(groupName)) {
                finish();
                return;
            }
            buildUserGroupUris(groupName);
        }*/ else if (UI.LIST_ALL_CONTACTS_ACTION.equals(action)) {
            mMode = MODE_CUSTOM;
            mDisplayAll = true;
            mDisplayOnlyPhones = false;
        } else if (UI.LIST_STARRED_ACTION.equals(action)) {
            mMode = MODE_STARRED;
        } else if (UI.LIST_FREQUENT_ACTION.equals(action)) {
            mMode = MODE_FREQUENT;
        } else if (UI.LIST_STREQUENT_ACTION.equals(action)) {
            mMode = MODE_STREQUENT;
        } else if (UI.LIST_CONTACTS_WITH_PHONES_ACTION.equals(action)) {
            mMode = MODE_CUSTOM;
            mDisplayAll = true;
            mDisplayOnlyPhones = true;
        } else if (Intent.ACTION_PICK.equals(action)) {
            // XXX These should be showing the data from the URI given in
            // the Intent.
            final String type = intent.resolveType(this);
            if (Aggregates.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_AGGREGATE;
            } else if (Phone.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_PHONE;
            } else if (Postal.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_POSTAL;
            }
        } else if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            mMode = MODE_PICK_OR_CREATE_AGGREGATE;
            mCreateShortcut = true;
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            final String type = intent.resolveType(this);
            if (Aggregates.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_OR_CREATE_AGGREGATE;
            } else if (Phone.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_PHONE;
            } else if (Postal.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_POSTAL;
            }
        } else if (Intent.ACTION_INSERT_OR_EDIT.equals(action)) {
            mMode = MODE_INSERT_OR_EDIT_CONTACT;
        } else if (Intent.ACTION_SEARCH.equals(action)) {
            // See if the suggestion was clicked with a search action key (call button)
            if ("call".equals(intent.getStringExtra(SearchManager.ACTION_MSG))) {
                String query = intent.getStringExtra(SearchManager.QUERY);
                if (!TextUtils.isEmpty(query)) {
                    Intent newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts("tel", query, null));
                    startActivity(newIntent);
                }
                finish();
                return;
            }

            // See if search request has extras to specify query
            /*if (intent.hasExtra(Insert.EMAIL)) {
                mMode = MODE_QUERY_PICK_TO_VIEW;
                mQueryMode = QUERY_MODE_MAILTO;
                mQueryData = intent.getStringExtra(Insert.EMAIL);
            } else if (intent.hasExtra(Insert.PHONE)) {
                mMode = MODE_QUERY_PICK_TO_VIEW;
                mQueryMode = QUERY_MODE_TEL;
                mQueryData = intent.getStringExtra(Insert.PHONE);
            } else {
                // Otherwise handle the more normal search case
                mMode = MODE_QUERY;
            }
            */
            mMode = MODE_QUERY;

        // Since this is the filter activity it receives all intents
        // dispatched from the SearchManager for security reasons
        // so we need to re-dispatch from here to the intended target.
        } else if (Intents.SEARCH_SUGGESTION_CLICKED.equals(action)) {
            // See if the suggestion was clicked with a search action key (call button)
            Intent newIntent;
            if ("call".equals(intent.getStringExtra(SearchManager.ACTION_MSG))) {
                newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, intent.getData());
            } else {
                newIntent = new Intent(Intent.ACTION_VIEW, intent.getData());
            }
            startActivity(newIntent);
            finish();
            return;
        } else if (Intents.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED.equals(action)) {
            Intent newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, intent.getData());
            startActivity(newIntent);
            finish();
            return;
        } else if (Intents.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED.equals(action)) {
            // TODO actually support this in EditContactActivity.
            String number = intent.getData().getSchemeSpecificPart();
            Intent newIntent = new Intent(Intent.ACTION_INSERT, Aggregates.CONTENT_URI);
            newIntent.putExtra(Intents.Insert.PHONE, number);
            startActivity(newIntent);
            finish();
            return;
        }

        if (JOIN_AGGREGATE.equals(action)) {
            mMode = MODE_JOIN_AGGREGATE;
            mQueryAggregateId = intent.getLongExtra(EXTRA_AGGREGATE_ID, -1);
            if (mQueryAggregateId == -1) {
                Log.e(TAG, "Intent " + action + " is missing required extra: "
                        + EXTRA_AGGREGATE_ID);
                setResult(RESULT_CANCELED);
                finish();
            }

            setTitle(R.string.titleJoinAggregate);
        }

        if (mMode == MODE_UNKNOWN) {
            mMode = MODE_DEFAULT;
        }

        // Setup the UI
        final ListView list = getListView();
        list.setFocusable(true);
        list.setOnCreateContextMenuListener(this);
        if ((mMode & MODE_MASK_NO_FILTER) != MODE_MASK_NO_FILTER) {
            list.setTextFilterEnabled(true);
        }

        if ((mMode & MODE_MASK_CREATE_NEW) != 0) {
            // Add the header for creating a new contact
            final LayoutInflater inflater = getLayoutInflater();
            View header = inflater.inflate(android.R.layout.simple_list_item_1, list, false);
            TextView text = (TextView) header.findViewById(android.R.id.text1);
            text.setText(R.string.pickerNewContactHeader);
            list.addHeaderView(header);
        }

        // Set the proper empty string
        setEmptyText();

        mAdapter = new ContactItemListAdapter(this);
        setListAdapter(mAdapter);

        // We manually save/restore the listview state
        list.setSaveEnabled(false);

        if (mMode == MODE_JOIN_AGGREGATE) {
            mQueryHandler = new SuggestionsQueryHandler(this, mQueryAggregateId);
        } else {
            mQueryHandler = new QueryHandler(this);
        }
        mJustCreated = true;

        // TODO(jham) redesign this
        mSyncEnabled = true;
//        // Check to see if sync is enabled
//        final ContentResolver resolver = getContentResolver();
//        IContentProvider provider = resolver.acquireProvider(Contacts.CONTENT_URI);
//        if (provider == null) {
//            // No contacts provider, bail.
//            finish();
//            return;
//        }
//
//        try {
//            ISyncAdapter sa = provider.getSyncAdapter();
//            mSyncEnabled = sa != null;
//        } catch (RemoteException e) {
//            mSyncEnabled = false;
//        } finally {
//            resolver.releaseProvider(provider);
//        }
    }

    private void setEmptyText() {
        TextView empty = (TextView) findViewById(R.id.emptyText);
        // Center the text by default
        int gravity = Gravity.CENTER;

        if (mDisplayOnlyPhones) {
            empty.setText(getText(R.string.noContactsWithPhoneNumbers));
        } else if (mDisplayAll) {
            empty.setText(getText(R.string.noContacts));
        } else {
            if (mSyncEnabled) {
                empty.setText(getText(R.string.noContactsHelpTextWithSync));
            } else {
                empty.setText(getText(R.string.noContactsHelpText));
            }
            gravity = Gravity.NO_GRAVITY;
        }
        empty.setGravity(gravity);
    }

    /**
     * Sets the mode when the request is for "default"
     */
    private void setDefaultMode() {
        // Load the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mDisplayAll = prefs.getBoolean(Prefs.DISPLAY_ALL, Prefs.DISPLAY_ALL_DEFAULT);
        mDisplayOnlyPhones = prefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                Prefs.DISPLAY_ONLY_PHONES_DEFAULT);

        // Update the empty text view with the proper string, as the group may have changed
        setEmptyText();
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean runQuery = true;
        Activity parent = getParent();

        // Do this before setting the filter. The filter thread relies
        // on some state that is initialized in setDefaultMode
        if (mMode == MODE_DEFAULT) {
            // If we're in default mode we need to possibly reset the mode due to a change
            // in the preferences activity while we weren't running
            setDefaultMode();
        }

        // See if we were invoked with a filter
        if (parent != null && parent instanceof ContactsActivity) {
            String filterText = ((ContactsActivity) parent).getAndClearFilterText();
            if (filterText != null && filterText.length() > 0) {
                getListView().setFilterText(filterText);
                // Don't start a new query since it will conflict with the filter
                runQuery = false;
            } else if (mJustCreated) {
                getListView().clearTextFilter();
            }
        }

        if (mJustCreated && runQuery) {
            // We need to start a query here the first time the activity is launched, as long
            // as we aren't doing a filter.
            startQuery();
        }
        mJustCreated = false;
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        // The cursor was killed off in onStop(), so we need to get a new one here
        // We do not perform the query if a filter is set on the list because the
        // filter will cause the query to happen anyway
        if (TextUtils.isEmpty(getListView().getTextFilter())) {
            startQuery();
        } else {
            // Run the filtered query on the adapter
            ((ContactItemListAdapter) getListAdapter()).onContentChanged();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        // Save list state in the bundle so we can restore it after the QueryHandler has run
        icicle.putParcelable(LIST_STATE_KEY, mList.onSaveInstanceState());
        icicle.putBoolean(FOCUS_KEY, mList.hasFocus());
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        super.onRestoreInstanceState(icicle);
        // Retrieve list state. This will be applied after the QueryHandler has run
        mListState = icicle.getParcelable(LIST_STATE_KEY);
        mListHasFocus = icicle.getBoolean(FOCUS_KEY);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // We don't want the list to display the empty state, since when we resume it will still
        // be there and show up while the new query is happening. After the async query finished
        // in response to onRestart() setLoading(false) will be called.
        mAdapter.setLoading(true);
        mAdapter.changeCursor(null);

        if (mMode == MODE_QUERY) {
            // Make sure the search box is closed
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            searchManager.stopSearch();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // If Contacts was invoked by another Activity simply as a way of
        // picking a contact, don't show the options menu
        if ((mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER) {
            return false;
        }

        // Search
        menu.add(0, MENU_SEARCH, 0, R.string.menu_search)
                .setIcon(android.R.drawable.ic_menu_search);

        // New contact
        //TODO Hook this up to new create contact activity.
        /*
        menu.add(0, MENU_NEW_CONTACT, 0, R.string.menu_newContact)
                .setIcon(android.R.drawable.ic_menu_add)
                .setIntent(new Intent(Intents.Insert.ACTION, People.CONTENT_URI))
                .setAlphabeticShortcut('n');
                */

        // Display group
        if (mMode == MODE_DEFAULT) {
            menu.add(0, MENU_DISPLAY_GROUP, 0, R.string.menu_displayGroup)
                    .setIcon(com.android.internal.R.drawable.ic_menu_allfriends);
        }

        // Sync settings
        if (mSyncEnabled) {
            Intent syncIntent = new Intent(Intent.ACTION_VIEW);
            syncIntent.setClass(this, ContactsGroupSyncSelector.class);
            menu.add(0, 0, 0, R.string.syncGroupPreference)
                    .setIcon(com.android.internal.R.drawable.ic_menu_refresh)
                    .setIntent(syncIntent);
        }

        // SIM import
        Intent importIntent = new Intent(Intent.ACTION_VIEW);
        importIntent.setType("vnd.android.cursor.item/sim-contact");
        importIntent.setClassName("com.android.phone", "com.android.phone.SimContacts");
        menu.add(0, 0, 0, R.string.importFromSim)
                .setIcon(R.drawable.ic_menu_import_contact)
                .setIntent(importIntent);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DISPLAY_GROUP:
                final Intent intent = new Intent(this, DisplayGroupsActivity.class);
                startActivityForResult(intent, SUBACTIVITY_DISPLAY_GROUP);
                return true;

            case MENU_SEARCH:
                startSearch(null, false, null, false);
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        switch (requestCode) {
            case SUBACTIVITY_NEW_CONTACT:
                if (resultCode == RESULT_OK) {
                    // Contact was created, pass it back
                    returnPickerResult(data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME),
                            data.getData());
                }
                break;

            case SUBACTIVITY_VIEW_CONTACT:
                if (resultCode == RESULT_OK) {
                    mAdapter.notifyDataSetChanged();
                }
                break;

            case SUBACTIVITY_DISPLAY_GROUP:
                // Mark as just created so we re-run the view query
                mJustCreated = true;
                break;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        // If Contacts was invoked by another Activity simply as a way of
        // picking a contact, don't show the context menu
        if ((mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER) {
            return;
        }

        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }
        long id = info.id;
        Uri aggUri = ContentUris.withAppendedId(Aggregates.CONTENT_URI, id);

        // Setup the menu header
        menu.setHeaderTitle(cursor.getString(SUMMARY_NAME_COLUMN_INDEX));

        // View contact details
        menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact)
                .setIntent(new Intent(Intent.ACTION_VIEW, aggUri));

        // Calling contact
        long phoneId = cursor.getLong(PRIMARY_PHONE_ID_COLUMN_INDEX);
        if (phoneId > 0) {
            // Get the display label for the number
            CharSequence label = cursor.getString(PRIMARY_PHONE_LABEL_COLUMN_INDEX);
            int type = cursor.getInt(PRIMARY_PHONE_TYPE_COLUMN_INDEX);
            label = ContactsUtils.getDisplayLabel(
                    this, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, type, label);
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                    ContentUris.withAppendedId(Data.CONTENT_URI, id));
            menu.add(0, MENU_ITEM_CALL, 0,
                    String.format(getString(R.string.menu_callNumber), label)).setIntent(intent);

            // Send SMS item
            menu.add(0, MENU_ITEM_SEND_SMS, 0, R.string.menu_sendSMS)
                    .setIntent(new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts("sms",
                                    cursor.getString(PRIMARY_PHONE_NUMBER_COLUMN_INDEX), null)));
        }

        // Star toggling
        int starState = cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX);
        if (starState == 0) {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_addStar);
        } else {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_removeStar);
        }

        // Contact editing
        menu.add(0, MENU_ITEM_EDIT, 0, R.string.menu_editContact)
                .setIntent(new Intent(Intent.ACTION_EDIT, aggUri));
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        switch (item.getItemId()) {
            case MENU_ITEM_TOGGLE_STAR: {
                // Toggle the star
                ContentValues values = new ContentValues(1);
                values.put(Aggregates.STARRED, cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX) == 0 ? 1 : 0);
                Uri aggUri = ContentUris.withAppendedId(Aggregates.CONTENT_URI,
                        cursor.getInt(ID_COLUMN_INDEX));
                getContentResolver().update(aggUri, values, null, null);
                return true;
            }

            /* case MENU_ITEM_DELETE: {
                // Get confirmation
                Uri uri = ContentUris.withAppendedId(People.CONTENT_URI,
                        cursor.getLong(ID_COLUMN_INDEX));
                //TODO make this dialog persist across screen rotations
                new AlertDialog.Builder(ContactsListActivity.this)
                    .setTitle(R.string.deleteConfirmation_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.deleteConfirmation)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DeleteClickListener(uri))
                    .show();
                return true;
            } */
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                if (callSelection()) {
                    return true;
                }
                break;
            }

            case KeyEvent.KEYCODE_DEL: {
                Object o = getListView().getSelectedItem();
                if (o != null) {
                    Cursor cursor = (Cursor) o;
                    Uri uri = ContentUris.withAppendedId(Aggregates.CONTENT_URI,
                            cursor.getLong(ID_COLUMN_INDEX));
                    //TODO make this dialog persist across screen rotations
                    new AlertDialog.Builder(ContactsListActivity.this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener(uri))
                        .setCancelable(false)
                        .show();
                    return true;
                }
                break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mList.getWindowToken(), 0);

        if (mMode == MODE_INSERT_OR_EDIT_CONTACT) {
            Intent intent;
            if (position == 0) {
                // Insert
                intent = new Intent(Intent.ACTION_INSERT, Aggregates.CONTENT_URI);
            } else {
                // Edit
                intent = new Intent(Intent.ACTION_EDIT,
                        ContentUris.withAppendedId(Aggregates.CONTENT_URI, id));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            final Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
            finish();
        } else
        if (id != -1) {
            if ((mMode & MODE_MASK_PICKER) == 0) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(Aggregates.CONTENT_URI, id));
                startActivityForResult(intent, SUBACTIVITY_VIEW_CONTACT);
            } else if (mMode == MODE_JOIN_AGGREGATE) {
                Uri uri = ContentUris.withAppendedId(Aggregates.CONTENT_URI, id);
                returnPickerResult(null, uri);
            }

            /*else if (mMode == MODE_QUERY_PICK_TO_VIEW) {
                // Started with query that should launch to view contact
                Cursor c = (Cursor) mAdapter.getItem(position);
                long personId = c.getLong(mQueryPersonIdIndex);
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(People.CONTENT_URI, personId));
                startActivity(intent);
                finish();
            }*/ else if (mMode == MODE_PICK_AGGREGATE
                    || mMode == MODE_PICK_OR_CREATE_AGGREGATE) {
                Uri uri = ContentUris.withAppendedId(Aggregates.CONTENT_URI, id);
                if (mCreateShortcut) {
                    // Subtract one if we have Create Contact at the top
                    Cursor c = (Cursor) mAdapter.getItem(position
                            - (mMode == MODE_PICK_OR_CREATE_AGGREGATE? 1:0));
                    returnPickerResult(c.getString(SUMMARY_NAME_COLUMN_INDEX), uri);
                } else {
                    returnPickerResult(null, uri);
                }
                setResult(RESULT_OK, new Intent().setData(uri));
                finish();
            } else if (mMode == MODE_PICK_PHONE) {
                setResult(RESULT_OK, new Intent().setData(
                        ContentUris.withAppendedId(Data.CONTENT_URI, id)));
                finish();
            } else if (mMode == MODE_PICK_POSTAL) {
                setResult(RESULT_OK, new Intent().setData(
                        ContentUris.withAppendedId(Data.CONTENT_URI, id)));
                finish();
            }
        } else if ((mMode & MODE_MASK_CREATE_NEW) == MODE_MASK_CREATE_NEW
                && position == 0) {
            // Hook this up to new edit contact activity.
            /*Intent newContact = new Intent(Intents.Insert.ACTION, People.CONTENT_URI);
            startActivityForResult(newContact, SUBACTIVITY_NEW_CONTACT);*/
        } else {
            signalError();
        }
    }

    private void returnPickerResult(String name, Uri uri) {
        final Intent intent = new Intent();

        if (mCreateShortcut) {
            Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, uri);
            shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            final Bitmap icon = loadContactPhoto(ContentUris.parseId(uri), null);
            if (icon != null) {
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
            } else {
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(this,
                                R.drawable.ic_launcher_shortcut_contact));
            }
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_OK, intent.setData(uri));
        }
        finish();
    }

    String[] getProjection() {
        switch (mMode) {
            case MODE_PICK_PHONE:
                return PHONES_PROJECTION;

            case MODE_PICK_POSTAL:
                return POSTALS_PROJECTION;
        }

        // Default to normal aggregate projection
        return AGGREGATES_SUMMARY_PROJECTION;
    }

    private Bitmap loadContactPhoto(long dataId, BitmapFactory.Options options) {
        Cursor cursor = null;
        Bitmap bm;
        try {
            cursor = getContentResolver().query(
                    ContentUris.withAppendedId(Data.CONTENT_URI, dataId),
                    new String[] {Photo.PHOTO}, null, null, null);
            cursor.moveToFirst();
            bm = ContactsUtils.loadContactPhoto(cursor, 0, options);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return bm;
    }

    /**
     * Return the selection arguments for a default query based on
     * {@link #mDisplayAll} and {@link #mDisplayOnlyPhones} flags.
     */
    private String getAggregateSelection() {
        if (!mDisplayAll && mDisplayOnlyPhones) {
            return CLAUSE_ONLY_VISIBLE + " AND " + CLAUSE_ONLY_PHONES;
        } else if (!mDisplayAll) {
            return CLAUSE_ONLY_VISIBLE;
        } else if (mDisplayOnlyPhones) {
            return CLAUSE_ONLY_PHONES;
        }
        return null;
    }

    private Uri getAggregateFilterUri(String filter) {
        if (!TextUtils.isEmpty(filter)) {
            return Uri.withAppendedPath(Aggregates.CONTENT_SUMMARY_FILTER_URI, Uri.encode(filter));
        } else {
            return Aggregates.CONTENT_SUMMARY_URI;
        }
    }

    private static String getSortOrder(String[] projectionType) {
        /* if (Locale.getDefault().equals(Locale.JAPAN) &&
                projectionType == AGGREGATES_PRIMARY_PHONE_PROJECTION) {
            return SORT_STRING + " ASC";
        } else {
            return NAME_COLUMN + " COLLATE LOCALIZED ASC";
        } */

        return NAME_COLUMN + " COLLATE LOCALIZED ASC";
    }

    void startQuery() {
        mAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);

        // Kick off the new query
        switch (mMode) {
            /* case MODE_GROUP:
                mQueryHandler.startQuery(QUERY_TOKEN, null,
                        mGroupUri, CONTACTS_PROJECTION, null, null,
                        getSortOrder(CONTACTS_PROJECTION));
                break; */

            case MODE_DEFAULT:
            case MODE_PICK_AGGREGATE:
            case MODE_PICK_OR_CREATE_AGGREGATE:
            case MODE_INSERT_OR_EDIT_CONTACT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, Aggregates.CONTENT_SUMMARY_URI,
                        AGGREGATES_SUMMARY_PROJECTION, getAggregateSelection(), null,
                        getSortOrder(AGGREGATES_SUMMARY_PROJECTION));
                break;

            case MODE_QUERY: {
                mQuery = getIntent().getStringExtra(SearchManager.QUERY);
                mQueryHandler.startQuery(QUERY_TOKEN, null, getAggregateFilterUri(mQuery),
                        AGGREGATES_SUMMARY_PROJECTION, null, null,
                        getSortOrder(AGGREGATES_SUMMARY_PROJECTION));
                break;
            }

            /*
            case MODE_QUERY_PICK_TO_VIEW: {
                if (mQueryMode == QUERY_MODE_MAILTO) {
                    // Find all contacts with the given search string as E-mail.
                    Uri uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_EMAIL_URI,
                            Uri.encode(mQueryData));
                    mQueryHandler.startQuery(QUERY_TOKEN, null,
                            uri, SIMPLE_CONTACTS_PROJECTION, null, null,
                            getSortOrder(CONTACTS_PROJECTION));

                } else if (mQueryMode == QUERY_MODE_TEL) {
                    mQueryAggIdIndex = PHONES_PERSON_ID_INDEX;
                    mQueryHandler.startQuery(QUERY_TOKEN, null,
                            Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, mQueryData),
                            PHONES_PROJECTION, null, null,
                            getSortOrder(PHONES_PROJECTION));
                }
                break;
            }
            */

            case MODE_STARRED:
                mQueryHandler.startQuery(QUERY_TOKEN, null, Aggregates.CONTENT_SUMMARY_URI,
                        AGGREGATES_SUMMARY_PROJECTION, Aggregates.STARRED + "=1", null,
                        getSortOrder(AGGREGATES_SUMMARY_PROJECTION));
                break;

            case MODE_FREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, Aggregates.CONTENT_SUMMARY_URI,
                        AGGREGATES_SUMMARY_PROJECTION,
                        Aggregates.TIMES_CONTACTED + " > 0", null,
                        Aggregates.TIMES_CONTACTED + " DESC, "
                        + getSortOrder(AGGREGATES_SUMMARY_PROJECTION));
                break;

            case MODE_STREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null,
                        Aggregates.CONTENT_SUMMARY_STREQUENT_URI, AGGREGATES_SUMMARY_PROJECTION,
                        null, null, null);
                break;

            case MODE_PICK_PHONE:
                mQueryHandler.startQuery(QUERY_TOKEN, null, Phone.CONTENT_URI,
                        PHONES_PROJECTION, null, null, getSortOrder(PHONES_PROJECTION));
                break;

            case MODE_PICK_POSTAL:
                mQueryHandler.startQuery(QUERY_TOKEN, null, Postal.CONTENT_URI,
                        POSTALS_PROJECTION, null, null, getSortOrder(POSTALS_PROJECTION));
                break;

            case MODE_JOIN_AGGREGATE:
                Uri suggestionsUri = Aggregates.CONTENT_URI.buildUpon()
                        .appendEncodedPath(String.valueOf(mQueryAggregateId))
                        .appendEncodedPath(AggregationSuggestions.CONTENT_DIRECTORY)
                        .appendQueryParameter(AggregationSuggestions.MAX_SUGGESTIONS,
                                String.valueOf(MAX_SUGGESTIONS))
                        .build();
                mQueryHandler.startQuery(QUERY_TOKEN, null, suggestionsUri, AGGREGATES_PROJECTION,
                        null, null, null);
                break;
        }
    }

    /**
     * Called from a background thread to do the filter and return the resulting cursor.
     *
     * @param filter the text that was entered to filter on
     * @return a cursor with the results of the filter
     */
    Cursor doFilter(String filter) {
        final ContentResolver resolver = getContentResolver();

        switch (mMode) {
            case MODE_DEFAULT:
            case MODE_PICK_AGGREGATE:
            case MODE_PICK_OR_CREATE_AGGREGATE:
            case MODE_INSERT_OR_EDIT_CONTACT: {
                return resolver.query(getAggregateFilterUri(filter), AGGREGATES_SUMMARY_PROJECTION,
                        getAggregateSelection(), null, getSortOrder(AGGREGATES_SUMMARY_PROJECTION));
            }

            case MODE_STARRED: {
                return resolver.query(getAggregateFilterUri(filter), AGGREGATES_SUMMARY_PROJECTION,
                        Aggregates.STARRED + "=1", null,
                        getSortOrder(AGGREGATES_SUMMARY_PROJECTION));
            }

            case MODE_FREQUENT: {
                return resolver.query(getAggregateFilterUri(filter), AGGREGATES_SUMMARY_PROJECTION,
                        Aggregates.TIMES_CONTACTED + " > 0", null,
                        Aggregates.TIMES_CONTACTED + " DESC, "
                        + getSortOrder(AGGREGATES_SUMMARY_PROJECTION));
            }

            case MODE_STREQUENT: {
                Uri uri;
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Aggregates.CONTENT_SUMMARY_STREQUENT_FILTER_URI,
                            Uri.encode(filter));
                } else {
                    uri = Aggregates.CONTENT_SUMMARY_STREQUENT_URI;
                }
                return resolver.query(uri, AGGREGATES_SUMMARY_PROJECTION, null, null, null);
            }

            case MODE_PICK_PHONE: {
                Uri uri;
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(filter));
                } else {
                    uri = Phone.CONTENT_URI;
                }
                return resolver.query(uri, PHONES_PROJECTION, null, null,
                        getSortOrder(PHONES_PROJECTION));
            }
        }
        throw new UnsupportedOperationException("filtering not allowed in mode " + mMode);
    }

    /**
     * Calls the currently selected list item.
     * @return true if the call was initiated, false otherwise
     */
    boolean callSelection() {
        ListView list = getListView();
        if (list.hasFocus()) {
            Cursor cursor = (Cursor) list.getSelectedItem();
            if (cursor != null) {
                long dataId = cursor.getLong(ID_COLUMN_INDEX);
                if (dataId == 0) {
                    // There is no phone number.
                    signalError();
                    return false;
                }
                Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, dataId);
                Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
                startActivity(intent);
                return true;
            }
        }

        return false;
    }

    /**
     * Signal an error to the user.
     */
    void signalError() {
        //TODO play an error beep or something...
    }

    Cursor getItemForView(View view) {
        ListView listView = getListView();
        int index = listView.getPositionForView(view);
        if (index < 0) {
            return null;
        }
        return (Cursor) listView.getAdapter().getItem(index);
    }

    private static class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<ContactsListActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<ContactsListActivity>((ContactsListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final ContactsListActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {
                activity.mAdapter.setLoading(false);
                activity.getListView().clearTextFilter();
                activity.mAdapter.changeCursor(cursor);

                // Now that the cursor is populated again, it's possible to restore the list state
                if (activity.mListState != null) {
                    activity.mList.onRestoreInstanceState(activity.mListState);
                    if (activity.mListHasFocus) {
                        activity.mList.requestFocus();
                    }
                    activity.mListHasFocus = false;
                    activity.mListState = null;
                }
            } else {
                cursor.close();
            }
        }
    }

    /**
     * Query handler for the suggestions query used in the Join Contacts UI.  Once the
     * suggestions query is complete, the handler launches an A-Z query.  The entire search is only
     * done once the second query is complete.
     */
    private static final class SuggestionsQueryHandler extends QueryHandler {
        boolean mSuggestionsQueryComplete;
        private final long mAggregateId;

        public SuggestionsQueryHandler(Context context, long aggregateId) {
            super(context);
            mAggregateId = aggregateId;
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (mSuggestionsQueryComplete) {
                super.onQueryComplete(token, cookie, cursor);
                return;
            }

            mSuggestionsQueryComplete = true;

            final ContactsListActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {
                if (cursor.getCount() > 0) {
                    activity.mAdapter.setSuggestionsCursor(cursor);
                } else {
                    activity.mAdapter.setSuggestionsCursor(null);
                }
                startQuery(QUERY_TOKEN, null, Aggregates.CONTENT_URI, AGGREGATES_PROJECTION,
                        Aggregates._ID + " != " + mAggregateId, null,
                        getSortOrder(AGGREGATES_PROJECTION));

            } else {
                cursor.close();
            }
        }
    }

    final static class ContactListItemCache {
        public TextView header;
        public TextView nameView;
        public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
        public TextView labelView;
        public CharArrayBuffer labelBuffer = new CharArrayBuffer(128);
        public TextView dataView;
        public CharArrayBuffer dataBuffer = new CharArrayBuffer(128);
        public ImageView presenceView;
        public ImageView photoView;
    }

    private final class ContactItemListAdapter extends ResourceCursorAdapter
            implements SectionIndexer {
        private SectionIndexer mIndexer;
        private String mAlphabet;
        private boolean mLoading = true;
        private CharSequence mUnknownNameText;
        private CharSequence[] mLocalizedLabels;
        private boolean mDisplayPhotos = false;
        private boolean mDisplayAdditionalData = true;
        private SparseArray<SoftReference<Bitmap>> mBitmapCache = null;
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;
        private boolean mDisplaySectionHeaders = true;
        private int[] mSectionPositions;
        private Cursor mSuggestionsCursor;
        private int mSuggestionsCursorCount;

        public ContactItemListAdapter(Context context) {
            super(context, R.layout.contacts_list_item, null, false);

            mAlphabet = context.getString(com.android.internal.R.string.fast_scroll_alphabet);

            mUnknownNameText = context.getText(android.R.string.unknownName);
            switch (mMode) {
                case MODE_PICK_POSTAL:
                    mLocalizedLabels = EditContactActivity.getLabelsForMimetype(mContext,
                            CommonDataKinds.Postal.CONTENT_ITEM_TYPE);
                    mDisplaySectionHeaders = false;
                    break;
                case MODE_PICK_PHONE:
                    mLocalizedLabels = EditContactActivity.getLabelsForMimetype(mContext,
                            CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                    mDisplaySectionHeaders = false;
                    break;
                default:
                    mLocalizedLabels = EditContactActivity.getLabelsForMimetype(mContext,
                            CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                    break;
            }

            // Do not display the second line of text if in a specific SEARCH query mode, usually for
            // matching a specific E-mail or phone number. Any contact details
            // shown would be identical, and columns might not even be present
            // in the returned cursor.
            if (mQueryMode != QUERY_MODE_NONE) {
                mDisplayAdditionalData = false;
            }

            if ((mMode & MODE_MASK_NO_DATA) == MODE_MASK_NO_DATA) {
                mDisplayAdditionalData = false;
            }

            if ((mMode & MODE_MASK_SHOW_PHOTOS) == MODE_MASK_SHOW_PHOTOS) {
                mDisplayPhotos = true;
                setViewResource(R.layout.contacts_list_item_photo);
                mBitmapCache = new SparseArray<SoftReference<Bitmap>>();
            }

            if (mMode == MODE_STREQUENT || mMode == MODE_FREQUENT) {
                mDisplaySectionHeaders = false;
            }
        }

        public void setSuggestionsCursor(Cursor cursor) {
            if (mSuggestionsCursor != null) {
                mSuggestionsCursor.close();
            }
            mSuggestionsCursor = cursor;
            mSuggestionsCursorCount = cursor == null ? 0 : cursor.getCount();
        }

        private SectionIndexer getNewIndexer(Cursor cursor) {
            /* if (Locale.getDefault().equals(Locale.JAPAN)) {
                return new JapaneseContactListIndexer(cursor, SORT_STRING_INDEX);
            } else { */
                return new AlphabetIndexer(cursor, SUMMARY_NAME_COLUMN_INDEX, mAlphabet);
            /* } */
        }

        /**
         * Callback on the UI thread when the content observer on the backing cursor fires.
         * Instead of calling requery we need to do an async query so that the requery doesn't
         * block the UI thread for a long time.
         */
        @Override
        protected void onContentChanged() {
            CharSequence constraint = getListView().getTextFilter();
            if (!TextUtils.isEmpty(constraint)) {
                // Reset the filter state then start an async filter operation
                Filter filter = getFilter();
                filter.filter(constraint);
            } else {
                // Start an async query
                startQuery();
            }
        }

        public void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if ((mMode & MODE_MASK_CREATE_NEW) == MODE_MASK_CREATE_NEW) {
                // This mode mask adds a header and we always want it to show up, even
                // if the list is empty, so always claim the list is not empty.
                return false;
            } else {
                if (mLoading) {
                    // We don't want the empty state to show when loading.
                    return false;
                } else {
                    return super.isEmpty();
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (getSeparatorId(position) != 0) {
                // We don't want the separator view to be recycled.
                return IGNORE_ITEM_VIEW_TYPE;
            }
            return super.getItemViewType(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException(
                        "this should only be called when the cursor is valid");
            }

            // Handle the separator specially
            int separatorId = getSeparatorId(position);
            if (separatorId != 0) {
                LayoutInflater inflater =
                        (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                TextView view = (TextView) inflater.inflate(R.layout.list_separator, parent, false);
                view.setText(separatorId);
                return view;
            }

            boolean showingSuggestion;
            Cursor cursor;
            if (mSuggestionsCursorCount != 0 && position < mSuggestionsCursorCount + 2) {
                showingSuggestion = true;
                cursor = mSuggestionsCursor;
            } else {
                showingSuggestion = false;
                cursor = mCursor;
            }

            int realPosition = getRealPosition(position);
            if (!cursor.moveToPosition(realPosition)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            View v;
            if (convertView == null) {
                v = newView(mContext, cursor, parent);
            } else {
                v = convertView;
            }
            bindView(v, mContext, cursor);
            bindSectionHeader(v, realPosition, mDisplaySectionHeaders && !showingSuggestion);
            return v;
        }

        private int getSeparatorId(int position) {
            int separatorId = 0;
            if (position == mFrequentSeparatorPos) {
                separatorId = R.string.favoritesFrquentSeparator;
            }
            if (mSuggestionsCursorCount != 0) {
                if (position == 0) {
                    separatorId = R.string.separatorJoinAggregateSuggestions;
                } else if (position == mSuggestionsCursorCount + 1) {
                    separatorId = R.string.separatorJoinAggregateAll;
                }
            }
            return separatorId;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final View view = super.newView(context, cursor, parent);

            final ContactListItemCache cache = new ContactListItemCache();
            cache.header = (TextView) view.findViewById(R.id.header);
            cache.nameView = (TextView) view.findViewById(R.id.name);
            cache.labelView = (TextView) view.findViewById(R.id.label);
            cache.dataView = (TextView) view.findViewById(R.id.data);
            cache.presenceView = (ImageView) view.findViewById(R.id.presence);
            cache.photoView = (ImageView) view.findViewById(R.id.photo);
            view.setTag(cache);

            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();

            TextView dataView = cache.dataView;
            TextView labelView = cache.labelView;
            int typeColumnIndex;
            int dataColumnIndex;
            int labelColumnIndex;
            int defaultType;
            int nameColumnIndex;
            switch(mMode) {
                case MODE_PICK_PHONE: {
                    nameColumnIndex = PHONE_DISPLAY_NAME_COLUMN_INDEX;
                    dataColumnIndex = PHONE_NUMBER_COLUMN_INDEX;
                    typeColumnIndex = PHONE_TYPE_COLUMN_INDEX;
                    labelColumnIndex = PHONE_LABEL_COLUMN_INDEX;
                    defaultType = Phone.TYPE_HOME;
                    break;
                }
                case MODE_PICK_POSTAL: {
                    nameColumnIndex = POSTAL_DISPLAY_NAME_COLUMN_INDEX;
                    dataColumnIndex = POSTAL_ADDRESS_COLUMN_INDEX;
                    typeColumnIndex = POSTAL_TYPE_COLUMN_INDEX;
                    labelColumnIndex = POSTAL_LABEL_COLUMN_INDEX;
                    defaultType = Postal.TYPE_HOME;
                    break;
                }
                default: {
                    nameColumnIndex = SUMMARY_NAME_COLUMN_INDEX;
                    dataColumnIndex = PRIMARY_PHONE_NUMBER_COLUMN_INDEX;
                    typeColumnIndex = PRIMARY_PHONE_TYPE_COLUMN_INDEX;
                    labelColumnIndex = PRIMARY_PHONE_LABEL_COLUMN_INDEX;
                    defaultType = Phone.TYPE_HOME;
                }
            }

            // Set the name
            cursor.copyStringToBuffer(nameColumnIndex, cache.nameBuffer);
            int size = cache.nameBuffer.sizeCopied;
            if (size != 0) {
                cache.nameView.setText(cache.nameBuffer.data, 0, size);
            } else {
                cache.nameView.setText(mUnknownNameText);
            }

            if (!mDisplayAdditionalData) {
                cache.dataView.setVisibility(View.GONE);
                cache.labelView.setVisibility(View.GONE);
                cache.presenceView.setVisibility(View.GONE);
                return;
            }

            // Set the data.
            cursor.copyStringToBuffer(dataColumnIndex, cache.dataBuffer);

            size = cache.dataBuffer.sizeCopied;
            if (size != 0) {
                dataView.setText(cache.dataBuffer.data, 0, size);
                dataView.setVisibility(View.VISIBLE);
            } else {
                dataView.setVisibility(View.GONE);
            }

            // Set the label.
            if (!cursor.isNull(typeColumnIndex)) {
                labelView.setVisibility(View.VISIBLE);
                int type = cursor.getInt(typeColumnIndex);

                if (type != CommonDataKinds.BaseTypes.TYPE_CUSTOM) {
                    try {
                        labelView.setText(mLocalizedLabels[type - 1]);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        labelView.setText(mLocalizedLabels[defaultType - 1]);
                    }
                } else {
                    cursor.copyStringToBuffer(labelColumnIndex, cache.labelBuffer);
                    // Don't check size, if it's zero just don't show anything
                    labelView.setText(cache.labelBuffer.data, 0, cache.labelBuffer.sizeCopied);
                }
            } else {
                // There is no label, hide the the view
                labelView.setVisibility(View.GONE);
            }

            // Set the proper icon (star or presence or nothing)
            ImageView presenceView = cache.presenceView;
            if ((mMode & MODE_MASK_NO_PRESENCE) == 0) {
                int serverStatus;
                if (!cursor.isNull(SUMMARY_PRESENCE_STATUS_COLUMN_INDEX)) {
                    serverStatus = cursor.getInt(SUMMARY_PRESENCE_STATUS_COLUMN_INDEX);
                    presenceView.setImageResource(
                            Presence.getPresenceIconResourceId(serverStatus));
                    presenceView.setVisibility(View.VISIBLE);
                } else {
                    presenceView.setVisibility(View.GONE);
                }
            } else {
                presenceView.setVisibility(View.GONE);
            }

            // Set the photo, if requested
            // TODO Either remove photos from this class completely or re-implement w/ asynchronous
            // loading.
            /*
            if (mDisplayPhotos) {
                Bitmap photo = null;

                // Look for the cached bitmap
                int pos = cursor.getPosition();
                SoftReference<Bitmap> ref = mBitmapCache.get(pos);
                if (ref != null) {
                    photo = ref.get();
                }

                if (photo == null) {
                    // Bitmap cache miss, decode it from the cursor
                    if (!cursor.isNull(PHOTO_COLUMN_INDEX)) {
                        try {
                            byte[] photoData = cursor.getBlob(PHOTO_COLUMN_INDEX);
                            photo = BitmapFactory.decodeByteArray(photoData, 0,
                                    photoData.length);
                            mBitmapCache.put(pos, new SoftReference<Bitmap>(photo));
                        } catch (OutOfMemoryError e) {
                            // Not enough memory for the photo, use the default one instead
                            photo = null;
                        }
                    }
                }

                // Bind the photo, or use the fallback no photo resource
                if (photo != null) {
                    cache.photoView.setImageBitmap(photo);
                } else {
                    cache.photoView.setImageResource(R.drawable.ic_contact_list_picture);
                }
            } */
        }

        private void bindSectionHeader(View view, int position, boolean displaySectionHeaders) {
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();
            if (!displaySectionHeaders) {
                cache.header.setVisibility(View.GONE);
            } else {
                final int section = getSectionForPosition(position);
                if (getPositionForSection(section) == position) {
                    cache.header.setText(mIndexer.getSections()[section].toString());
                    cache.header.setVisibility(View.VISIBLE);
                } else {
                    cache.header.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            // Get the split between starred and frequent items, if the mode is strequent
            mFrequentSeparatorPos = ListView.INVALID_POSITION;
            if (cursor != null && cursor.getCount() > 0 && mMode == MODE_STREQUENT) {
                cursor.move(-1);
                for (int i = 0; cursor.moveToNext(); i++) {
                    int starred = cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX);
                    if (starred == 0) {
                        if (i > 0) {
                            // Only add the separator when there are starred items present
                            mFrequentSeparatorPos = i;
                        }
                        break;
                    }
                }
            }

            super.changeCursor(cursor);

            // Update the indexer for the fast scroll widget
            updateIndexer(cursor);

            // Clear the photo bitmap cache, if there is one
            if (mBitmapCache != null) {
                mBitmapCache.clear();
            }
        }

        private void updateIndexer(Cursor cursor) {
            if (mIndexer == null) {
                mIndexer = getNewIndexer(cursor);
            } else {
                if (Locale.getDefault().equals(Locale.JAPAN)) {
                    if (mIndexer instanceof JapaneseContactListIndexer) {
                        ((JapaneseContactListIndexer)mIndexer).setCursor(cursor);
                    } else {
                        mIndexer = getNewIndexer(cursor);
                    }
                } else {
                    if (mIndexer instanceof AlphabetIndexer) {
                        ((AlphabetIndexer)mIndexer).setCursor(cursor);
                    } else {
                        mIndexer = getNewIndexer(cursor);
                    }
                }
            }

            int sectionCount = mIndexer.getSections().length;
            if (mSectionPositions == null || mSectionPositions.length != sectionCount) {
                mSectionPositions = new int[sectionCount];
            }
            for (int i = 0; i < sectionCount; i++) {
                mSectionPositions[i] = ListView.INVALID_POSITION;
            }
        }

        /**
         * Run the query on a helper thread. Beware that this code does not run
         * on the main UI thread!
         */
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            return doFilter(constraint.toString());
        }

        public Object [] getSections() {
            if (mMode == MODE_STARRED) {
                return new String[] { " " };
            } else {
                return mIndexer.getSections();
            }
        }

        public int getPositionForSection(int sectionIndex) {
            if (mMode == MODE_STARRED) {
                return -1;
            }

            if (mIndexer == null) {
                Cursor cursor = mAdapter.getCursor();
                if (cursor == null) {
                    // No cursor, the section doesn't exist so just return 0
                    return 0;
                }
                mIndexer = getNewIndexer(cursor);
            }

            int position = mSectionPositions[sectionIndex];
            if (position == ListView.INVALID_POSITION) {
                position = mSectionPositions[sectionIndex] =
                        mIndexer.getPositionForSection(sectionIndex);
            }

            return position;
        }

        public int getSectionForPosition(int position) {
            // The current implementations of SectionIndexers (specifically the Japanese indexer)
            // only work in one direction: given a section they can calculate the position.
            // Here we are using that existing functionality to do the reverse mapping. We are
            // performing binary search in the mSectionPositions array, which itself is populated
            // lazily using the "forward" mapping supported by the indexer.

            int start = 0;
            int end = mSectionPositions.length;
            while (start != end) {

                // We are making the binary search slightly asymmetrical, because the
                // user is more likely to be scrolling the list from the top down.
                int pivot = start + (end - start) / 4;

                int value = getPositionForSection(pivot);
                if (value <= position) {
                    start = pivot + 1;
                } else {
                    end = pivot;
                }
            }

            // The variable "start" cannot be 0, as long as the indexer is implemented properly
            // and actually maps position = 0 to section = 0
            return start - 1;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return mMode != MODE_STARRED;
        }

        @Override
        public boolean isEnabled(int position) {
            if (mSuggestionsCursorCount > 0) {
                return position != 0 && position != mSuggestionsCursorCount + 1;
            }
            return position != mFrequentSeparatorPos;
        }

        @Override
        public int getCount() {
            if (mSuggestionsCursorCount != 0) {
                // When showing suggestions, we have 2 additional list items: the "Suggestions"
                // and "All contacts" headers.
                return mSuggestionsCursorCount + super.getCount() + 2;
            }
            else if (mFrequentSeparatorPos != ListView.INVALID_POSITION) {
                // When showing strequent list, we have an additional list item - the separator.
                return super.getCount() + 1;
            } else {
                return super.getCount();
            }
        }

        private int getRealPosition(int pos) {
            if (mSuggestionsCursorCount != 0) {
                // When showing suggestions, we have 2 additional list items: the "Suggestions"
                // and "All contacts" separators.
                if (pos < mSuggestionsCursorCount + 2) {
                    // We are in the upper partition (Suggestions). Adjusting for the "Suggestions"
                    // separator.
                    return pos - 1;
                } else {
                    // We are in the lower partition (All contacts). Adjusting for the size
                    // of the upper partition plus the two separators.
                    return pos - mSuggestionsCursorCount - 2;
                }
            } else if (mFrequentSeparatorPos == ListView.INVALID_POSITION) {
                // No separator, identity map
                return pos;
            } else if (pos <= mFrequentSeparatorPos) {
                // Before or at the separator, identity map
                return pos;
            } else {
                // After the separator, remove 1 from the pos to get the real underlying pos
                return pos - 1;
            }
        }

        @Override
        public Object getItem(int pos) {
            if (mSuggestionsCursorCount != 0 && pos <= mSuggestionsCursorCount) {
                mSuggestionsCursor.moveToPosition(getRealPosition(pos));
                return mSuggestionsCursor;
            } else {
                return super.getItem(getRealPosition(pos));
            }
        }

        @Override
        public long getItemId(int pos) {
            if (mSuggestionsCursorCount != 0 && pos < mSuggestionsCursorCount + 2) {
                if (mSuggestionsCursor.moveToPosition(pos - 1)) {
                    return mSuggestionsCursor.getLong(mRowIDColumn);
                } else {
                    return 0;
                }
            }
            return super.getItemId(getRealPosition(pos));
        }
    }
}
