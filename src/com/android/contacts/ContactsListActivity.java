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

import com.android.contacts.list.CallOrSmsInitiator;
import com.android.contacts.list.ContactBrowseListContextMenuAdapter;
import com.android.contacts.list.ContactEntryListAdapter;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactItemListAdapter;
import com.android.contacts.list.ContactPickerFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.DefaultContactListFragment;
import com.android.contacts.list.MultiplePhonePickerFragment;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnContactPickerActionListener;
import com.android.contacts.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.list.OnPostalAddressPickerActionListener;
import com.android.contacts.list.PhoneNumberPickerFragment;
import com.android.contacts.list.PostalAddressPickerFragment;
import com.android.contacts.list.StrequentContactListFragment;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.ui.ContactsPreferences;
import com.android.contacts.ui.ContactsPreferencesActivity;
import com.android.contacts.ui.ContactsPreferencesActivity.Prefs;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.widget.ContextMenuAdapter;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.PeopleColumns;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Intents.Insert;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays a list of contacts. Usually is embedded into the ContactsActivity.
 */
@SuppressWarnings("deprecation")
public class ContactsListActivity extends Activity implements View.OnCreateContextMenuListener,
        View.OnClickListener,
        ContactsApplicationController {

    private static final String TAG = "ContactsListActivity";

    private static final boolean ENABLE_ACTION_ICON_OVERLAYS = true;

    private static final String SHORTCUT_ACTION_KEY = "shortcutAction";

    private static final int SUBACTIVITY_NEW_CONTACT = 1;
    private static final int SUBACTIVITY_VIEW_CONTACT = 2;
    private static final int SUBACTIVITY_DISPLAY_GROUP = 3;
    private static final int SUBACTIVITY_SEARCH = 4;
    protected static final int SUBACTIVITY_FILTER = 5;

    public static final String AUTHORITIES_FILTER_KEY = "authorities";

    private static final Uri CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS =
            buildSectionIndexerUri(Contacts.CONTENT_URI);

    /** Mask for picker mode */
    public static final int MODE_MASK_PICKER = 0x80000000;
    /** Mask for no presence mode */
    public static final int MODE_MASK_NO_PRESENCE = 0x40000000;
    /** Mask for enabling list filtering */
    public static final int MODE_MASK_NO_FILTER = 0x20000000;
    /** Mask for having a "create new contact" header in the list */
    public static final int MODE_MASK_CREATE_NEW = 0x10000000;
    /** Mask for showing photos in the list */
    public static final int MODE_MASK_SHOW_PHOTOS = 0x08000000;
    /** Mask for hiding additional information e.g. primary phone number in the list */
    public static final int MODE_MASK_NO_DATA = 0x04000000;
    /** Mask for showing a call button in the list */
    public static final int MODE_MASK_SHOW_CALL_BUTTON = 0x02000000;
    /** Mask to disable quickcontact (images will show as normal images) */
    public static final int MODE_MASK_DISABLE_QUIKCCONTACT = 0x01000000;
    /** Mask to show the total number of contacts at the top */
    public static final int MODE_MASK_SHOW_NUMBER_OF_CONTACTS = 0x00800000;

    /** Unknown mode */
    public static final int MODE_UNKNOWN = 0;
    /** Default mode */
    public static final int MODE_DEFAULT = 4 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;
    /** Custom mode */
    public static final int MODE_CUSTOM = 8;
    /** Show all starred contacts */
    public static final int MODE_STARRED = 20 | MODE_MASK_SHOW_PHOTOS;
    /** Show frequently contacted contacts */
    public static final int MODE_FREQUENT = 30 | MODE_MASK_SHOW_PHOTOS;
    /** Show starred and the frequent */
    public static final int MODE_STREQUENT = 35 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_SHOW_CALL_BUTTON;
    /** Show all contacts and pick them when clicking */
    public static final int MODE_PICK_CONTACT = 40 | MODE_MASK_PICKER | MODE_MASK_SHOW_PHOTOS
            | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all contacts as well as the option to create a new one */
    public static final int MODE_PICK_OR_CREATE_CONTACT = 42 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW
            | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all people through the legacy provider and pick them when clicking */
    public static final int MODE_LEGACY_PICK_PERSON = 43 | MODE_MASK_PICKER
            | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all people through the legacy provider as well as the option to create a new one */
    public static final int MODE_LEGACY_PICK_OR_CREATE_PERSON = 44 | MODE_MASK_PICKER
            | MODE_MASK_CREATE_NEW | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all contacts and pick them when clicking, and allow creating a new contact */
    public static final int MODE_INSERT_OR_EDIT_CONTACT = 45 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW
            | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all phone numbers and pick them when clicking */
    public static final int MODE_PICK_PHONE = 50 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE;
    /** Show all phone numbers through the legacy provider and pick them when clicking */
    public static final int MODE_LEGACY_PICK_PHONE =
            51 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Show all postal addresses and pick them when clicking */
    public static final int MODE_PICK_POSTAL =
            55 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Show all postal addresses and pick them when clicking */
    public static final int MODE_LEGACY_PICK_POSTAL =
            56 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    public static final int MODE_GROUP = 57 | MODE_MASK_SHOW_PHOTOS;
    /** Run a search query */
    public static final int MODE_QUERY = 60 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_NO_FILTER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;
    /** Run a search query in PICK mode, but that still launches to VIEW */
    public static final int MODE_QUERY_PICK_TO_VIEW = 65 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_PICKER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in a PICK mode */
    public static final int MODE_QUERY_PICK = 75 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_NO_FILTER
            | MODE_MASK_PICKER | MODE_MASK_DISABLE_QUIKCCONTACT | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in a PICK_PHONE mode */
    public static final int MODE_QUERY_PICK_PHONE = 80 | MODE_MASK_NO_FILTER | MODE_MASK_PICKER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in PICK mode, but that still launches to EDIT */
    public static final int MODE_QUERY_PICK_TO_EDIT = 85 | MODE_MASK_NO_FILTER | MODE_MASK_SHOW_PHOTOS
            | MODE_MASK_PICKER | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /**
     * Show all phone numbers and do multiple pick when clicking. This mode has phone filtering
     * feature, but doesn't support 'search for all contacts'.
     */
    public static final int MODE_PICK_MULTIPLE_PHONES = 80 | MODE_MASK_PICKER
            | MODE_MASK_NO_PRESENCE | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;

    /**
     * An action used to do perform search while in a contact picker.  It is initiated
     * by the ContactListActivity itself.
     */
    protected static final String ACTION_SEARCH_INTERNAL = "com.android.contacts.INTERNAL_SEARCH";

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
    };
    static final String[] CONTACTS_SUMMARY_PROJECTION_FROM_EMAIL = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        // email lookup doesn't included HAS_PHONE_NUMBER in projection
    };

    static final String[] CONTACTS_SUMMARY_FILTER_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
        SearchSnippetColumns.SNIPPET_MIMETYPE, // 11
        SearchSnippetColumns.SNIPPET_DATA1,     // 12
        SearchSnippetColumns.SNIPPET_DATA4,     // 13
    };

    static final String[] LEGACY_PEOPLE_PROJECTION = new String[] {
        People._ID,                         // 0
        People.DISPLAY_NAME,                // 1
        People.DISPLAY_NAME,                // 2
        People.DISPLAY_NAME,                // 3
        People.STARRED,                     // 4
        PeopleColumns.TIMES_CONTACTED,      // 5
        People.PRESENCE_STATUS,             // 6
    };
    public static final int SUMMARY_ID_COLUMN_INDEX = 0;
    public static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    public static final int SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    static final int SUMMARY_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    public static final int SUMMARY_STARRED_COLUMN_INDEX = 4;
    static final int SUMMARY_TIMES_CONTACTED_COLUMN_INDEX = 5;
    public static final int SUMMARY_PRESENCE_STATUS_COLUMN_INDEX = 6;
    public static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 7;
    public static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 8;
    public static final int SUMMARY_PHONETIC_NAME_COLUMN_INDEX = 9;
    public static final int SUMMARY_HAS_PHONE_COLUMN_INDEX = 10;
    public static final int SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX = 11;
    public static final int SUMMARY_SNIPPET_DATA1_COLUMN_INDEX = 12;
    public static final int SUMMARY_SNIPPET_DATA4_COLUMN_INDEX = 13;

    static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID, //0
        Phone.TYPE, //1
        Phone.LABEL, //2
        Phone.NUMBER, //3
        Phone.DISPLAY_NAME, // 4
        Phone.CONTACT_ID, // 5
        Contacts.SORT_KEY_PRIMARY, // 6
        Contacts.PHOTO_ID, // 7
    };
    static final String[] LEGACY_PHONES_PROJECTION = new String[] {
        Phones._ID, //0
        Phones.TYPE, //1
        Phones.LABEL, //2
        Phones.NUMBER, //3
        People.DISPLAY_NAME, // 4
    };
    public static final int PHONE_ID_COLUMN_INDEX = 0;
    public static final int PHONE_TYPE_COLUMN_INDEX = 1;
    public static final int PHONE_LABEL_COLUMN_INDEX = 2;
    public static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    public static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 4;
    public static final int PHONE_CONTACT_ID_COLUMN_INDEX = 5;
    static final int PHONE_SORT_KEY_PRIMARY_COLUMN_INDEX = 6;
    public static final int PHONE_PHOTO_ID_COLUMN_INDEX = 7;

    static final String[] POSTALS_PROJECTION = new String[] {
        StructuredPostal._ID, //0
        StructuredPostal.TYPE, //1
        StructuredPostal.LABEL, //2
        StructuredPostal.DATA, //3
        StructuredPostal.DISPLAY_NAME, // 4
    };
    static final String[] LEGACY_POSTALS_PROJECTION = new String[] {
        ContactMethods._ID, //0
        ContactMethods.TYPE, //1
        ContactMethods.LABEL, //2
        ContactMethods.DATA, //3
        People.DISPLAY_NAME, // 4
    };
    static final String[] RAW_CONTACTS_PROJECTION = new String[] {
        RawContacts._ID, //0
        RawContacts.CONTACT_ID, //1
        RawContacts.ACCOUNT_TYPE, //2
    };

    static final int POSTAL_ID_COLUMN_INDEX = 0;
    public static final int POSTAL_TYPE_COLUMN_INDEX = 1;
    public static final int POSTAL_LABEL_COLUMN_INDEX = 2;
    public static final int POSTAL_ADDRESS_COLUMN_INDEX = 3;
    public static final int POSTAL_DISPLAY_NAME_COLUMN_INDEX = 4;

    protected static final int QUERY_TOKEN = 42;

    static final String KEY_PICKER_MODE = "picker_mode";

    public ContactEntryListAdapter mAdapter;
    public ContactListEmptyView mEmptyView;


    public int mMode = MODE_DEFAULT;
    private boolean mRunQueriesSynchronously;
    protected QueryHandler mQueryHandler;
    private boolean mJustCreated;
    private boolean mSyncEnabled;
    Uri mSelectedContactUri;

//    private boolean mDisplayAll;
    public boolean mDisplayOnlyPhones;

    private String mGroupName;

    private ArrayList<Long> mWritableRawContactIds = new ArrayList<Long>();
    private int  mWritableSourcesCnt;
    private int  mReadOnlySourcesCnt;

    public String mShortcutAction;

    /**
     * Internal query type when in mode {@link #MODE_QUERY_PICK_TO_VIEW}.
     */
    public int mQueryMode = QUERY_MODE_NONE;

    public static final int QUERY_MODE_NONE = -1;
    private static final int QUERY_MODE_MAILTO = 1;
    private static final int QUERY_MODE_TEL = 2;

    public int mProviderStatus = ProviderStatus.STATUS_NORMAL;

    public boolean mSearchMode;
    public boolean mSearchResultsMode;
    public boolean mShowNumberOfContacts;

    public boolean mShowSearchSnippets;
    private boolean mSearchInitiated;

    private String mInitialFilter;

    protected static final String CLAUSE_ONLY_VISIBLE = Contacts.IN_VISIBLE_GROUP + "=1";
    private static final String CLAUSE_ONLY_PHONES = Contacts.HAS_PHONE_NUMBER + "=1";


    // Uri matcher for contact id
    private static final int CONTACTS_ID = 1001;
    private static final UriMatcher sContactsIdMatcher;

    final String[] sLookupProjection = new String[] {
            Contacts.LOOKUP_KEY
    };

    static {
        sContactsIdMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sContactsIdMatcher.addURI(ContactsContract.AUTHORITY, "contacts/#", CONTACTS_ID);
    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            if (mSelectedContactUri != null) {
                getContentResolver().delete(mSelectedContactUri, null, null);
            }
        }
    }

    // The size of a home screen shortcut icon.
    private int mIconSize;
    private ContactsPreferences mContactsPrefs;
    public int mDisplayOrder;
    private int mSortOrder;

    private ContentObserver mProviderStatusObserver = new ContentObserver(new Handler()) {

        @Override
        public void onChange(boolean selfChange) {
            checkProviderState(true);
        }
    };

    private ContactsIntentResolver mIntentResolver;
    protected ContactEntryListFragment mListFragment;

    private ListView mListView;

    protected CallOrSmsInitiator mCallOrSmsInitiator;

    public ContactsListActivity() {
        mIntentResolver = new ContactsIntentResolver(this, this);
    }

    /**
     * Visible for testing: makes queries run on the UI thread.
     */
    /* package */ void runQueriesSynchronously() {
        mRunQueriesSynchronously = true;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mIconSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
        mContactsPrefs = new ContactsPreferences(this);

        mQueryHandler = new QueryHandler(this);
        mJustCreated = true;
        mSyncEnabled = true;

        // Resolve the intent
        final Intent intent = getIntent();

        if (!resolveIntent(intent)) {
            return;
        }

        FragmentTransaction transaction = openFragmentTransaction();
        transaction.add(mListFragment, android.R.id.content);
        transaction.commit();
    }

    protected boolean resolveIntent(final Intent intent) {
        mIntentResolver.setIntent(intent);

        if (!mIntentResolver.isValid()) {           // Invalid intent
            setResult(RESULT_CANCELED);
            finish();
            return false;
        }

        Intent redirect = mIntentResolver.getRedirectIntent();
        if (redirect != null) {             // Need to start a different activity
            startActivity(redirect);
            finish();
            return false;
        }

        setTitle(mIntentResolver.getActivityTitle());


        // This is strictly temporary. Its purpose is to allow us to refactor this class in
        // small increments.  We should expect all of these modes to go away.
        mMode = mIntentResolver.mMode;
        mGroupName = mIntentResolver.mGroupName;
        mQueryMode = mIntentResolver.mQueryMode;
        mSearchMode = mIntentResolver.mSearchMode;
        mShowSearchSnippets = mIntentResolver.mShowSearchSnippets;
        mInitialFilter = mIntentResolver.mInitialFilter;
        mDisplayOnlyPhones = mIntentResolver.mDisplayOnlyPhones;
        mShortcutAction = mIntentResolver.mShortcutAction;
        mSearchResultsMode = mIntentResolver.mSearchResultsMode;
        mShowNumberOfContacts = mIntentResolver.mShowNumberOfContacts;
        mGroupName = mIntentResolver.mGroupName;

        switch (mMode) {
            case MODE_DEFAULT:
            case MODE_INSERT_OR_EDIT_CONTACT:
            case MODE_QUERY_PICK_TO_EDIT:
            case MODE_FREQUENT:
            case MODE_QUERY: {
                DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();
                if (!mSearchMode) {
                    fragment.setSectionHeaderDisplayEnabled(true);
                }

                if (mMode == MODE_INSERT_OR_EDIT_CONTACT ||
                        mMode == MODE_QUERY_PICK_TO_EDIT) {
                    fragment.setEditMode(true);
                }

                if (mMode == MODE_INSERT_OR_EDIT_CONTACT) {
                    fragment.setCreateContactEnabled(true);
                }

                if (mMode == MODE_QUERY) {
                    fragment.setSearchResultsMode(true);
                }

                fragment.setOnContactListActionListener(new OnContactBrowserActionListener() {
                    public void onSearchAllContactsAction(String string) {
                        doSearch();
                    }

                    public void onViewContactAction(Uri contactLookupUri) {
                        startActivity(new Intent(Intent.ACTION_VIEW, contactLookupUri));
                    }

                    public void onCreateNewContactAction() {
                        Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                        Bundle extras = getIntent().getExtras();
                        if (extras != null) {
                            intent.putExtras(extras);
                        }
                        startActivity(intent);
                    }

                    public void onEditContactAction(Uri contactLookupUri) {
                        Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
                        Bundle extras = getIntent().getExtras();
                        if (extras != null) {
                            intent.putExtras(extras);
                        }
                        startActivity(intent);
                    }

                    public void onAddToFavoritesAction(Uri contactUri) {
                        ContentValues values = new ContentValues(1);
                        values.put(Contacts.STARRED, 1);
                        getContentResolver().update(contactUri, values, null, null);
                    }

                    public void onRemoveFromFavoritesAction(Uri contactUri) {
                        ContentValues values = new ContentValues(1);
                        values.put(Contacts.STARRED, 0);
                        getContentResolver().update(contactUri, values, null, null);
                    }

                    public void onCallContactAction(Uri contactUri) {
                        getCallOrSmsInitiator().initiateCall(contactUri);
                    }

                    public void onSmsContactAction(Uri contactUri) {
                        getCallOrSmsInitiator().initiateSms(contactUri);
                    }

                    public void onDeleteContactAction(Uri contactUri) {
                        doContactDelete(contactUri);
                    }

                    public void onFinishAction() {
                        onBackPressed();
                    }
                });
                fragment.setContextMenuAdapter(new ContactBrowseListContextMenuAdapter(fragment));
                mListFragment = fragment;
                break;
            }
            case MODE_STREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setSectionHeaderDisplayEnabled(false);
                fragment.setOnContactListActionListener(new OnContactBrowserActionListener() {
                    public void onSearchAllContactsAction(String string) {
                        doSearch();
                    }

                    public void onViewContactAction(Uri contactLookupUri) {
                        startActivity(new Intent(Intent.ACTION_VIEW, contactLookupUri));
                    }

                    public void onCreateNewContactAction() {
                        Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                        Bundle extras = getIntent().getExtras();
                        if (extras != null) {
                            intent.putExtras(extras);
                        }
                        startActivity(intent);
                    }

                    public void onEditContactAction(Uri contactLookupUri) {
                        Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
                        Bundle extras = getIntent().getExtras();
                        if (extras != null) {
                            intent.putExtras(extras);
                        }
                        startActivity(intent);
                    }

                    public void onAddToFavoritesAction(Uri contactUri) {
                        ContentValues values = new ContentValues(1);
                        values.put(Contacts.STARRED, 1);
                        getContentResolver().update(contactUri, values, null, null);
                    }

                    public void onRemoveFromFavoritesAction(Uri contactUri) {
                        ContentValues values = new ContentValues(1);
                        values.put(Contacts.STARRED, 0);
                        getContentResolver().update(contactUri, values, null, null);
                    }

                    public void onCallContactAction(Uri contactUri) {
                        getCallOrSmsInitiator().initiateCall(contactUri);
                    }

                    public void onSmsContactAction(Uri contactUri) {
                        getCallOrSmsInitiator().initiateSms(contactUri);
                    }

                    public void onDeleteContactAction(Uri contactUri) {
                        doContactDelete(contactUri);
                    }

                    public void onFinishAction() {
                        onBackPressed();
                    }
                });
                fragment.setContextMenuAdapter(new ContactBrowseListContextMenuAdapter(fragment));
                mListFragment = fragment;
                break;
            }
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                if (!mSearchMode) {
                    fragment.setSectionHeaderDisplayEnabled(true);
                }

                if (mMode == MODE_PICK_OR_CREATE_CONTACT) {
                    fragment.setCreateContactEnabled(true);
                }

                fragment.setShortcutRequested(mShortcutAction != null);

                fragment.setOnContactPickerActionListener(new OnContactPickerActionListener() {
                    public void onSearchAllContactsAction(String string) {
                        doSearch();
                    }

                    public void onCreateNewContactAction() {
                        Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                        startActivityAndForwardResult(intent);
                    }

                    public void onPickContactAction(Uri contactUri) {
                        Intent intent = new Intent();
                        setResult(RESULT_OK, intent.setData(contactUri));
                        finish();
                    }

                    public void onShortcutIntentCreated(Intent intent) {
                        setResult(RESULT_OK, intent);
                        finish();
                    }

                    // TODO: finish action to support search in the picker
                });

                mListFragment = fragment;
                break;
            }
            case MODE_LEGACY_PICK_PHONE:
            case MODE_PICK_PHONE: {
                mListFragment = new DefaultContactListFragment();
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                if (mMode == MODE_LEGACY_PICK_PHONE) {
                    fragment.setLegacyCompatibility(true);
                }
                fragment.setSectionHeaderDisplayEnabled(false);
                fragment.setShortcutAction(mShortcutAction);
                fragment.setOnPhoneNumberPickerActionListener(
                        new OnPhoneNumberPickerActionListener() {

                    public void onPickPhoneNumberAction(Uri dataUri) {
                        Intent intent = new Intent();
                        setResult(RESULT_OK, intent.setData(dataUri));
                        finish();
                    }

                    public void onSearchAllContactsAction(String string) {
                        doSearch();
                    }

                    public void onShortcutIntentCreated(Intent intent) {
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                });
                mListFragment = fragment;
                break;
            }
            case MODE_LEGACY_PICK_POSTAL:
            case MODE_PICK_POSTAL: {
                PostalAddressPickerFragment fragment = new PostalAddressPickerFragment();
                if (mMode == MODE_LEGACY_PICK_PHONE) {
                    fragment.setLegacyCompatibility(true);
                }
                fragment.setSectionHeaderDisplayEnabled(false);
                fragment.setOnPostalAddressPickerActionListener(
                        new OnPostalAddressPickerActionListener() {

                    public void onPickPostalAddressAction(Uri dataUri) {
                        Intent intent = new Intent();
                        setResult(RESULT_OK, intent.setData(dataUri));
                        finish();
                    }

                    public void onSearchAllContactsAction(String string) {
                        doSearch();
                    }
                });
                mListFragment = fragment;
                break;
            }
            case MODE_PICK_MULTIPLE_PHONES: {
                mListFragment = new MultiplePhonePickerFragment();
                break;
            }
            default: {
                mListFragment = new DefaultContactListFragment();
                if (!mSearchMode) {
                    mListFragment.setSectionHeaderDisplayEnabled(true);
                }
            }
        }

        mListFragment.setSearchMode(mSearchMode);
        mListFragment.setSearchResultsMode(mSearchResultsMode);
        mListFragment.setQueryString(mInitialFilter);
        mListFragment.setContactNameDisplayOrder(mContactsPrefs.getDisplayOrder());
        mListFragment.setSortOrder(mContactsPrefs.getSortOrder());

        if ((mMode & MODE_MASK_SHOW_PHOTOS) == MODE_MASK_SHOW_PHOTOS) {
            mListFragment.setPhotoLoaderEnabled(true);
        }

        mListFragment.setContactsApplicationController(this);

        return true;
    }

    public void startActivityAndForwardResult(final Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Forward extras to the new activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
        finish();
    }

    @Deprecated
    public void setupListView(ListAdapter adapter, ListView list) {
        mAdapter = (ContactEntryListAdapter)adapter;
    }

    /**
     * Register an observer for provider status changes - we will need to
     * reflect them in the UI.
     */
    private void registerProviderStatusObserver() {
        getContentResolver().registerContentObserver(ProviderStatus.CONTENT_URI,
                false, mProviderStatusObserver);
    }

    /**
     * Register an observer for provider status changes - we will need to
     * reflect them in the UI.
     */
    private void unregisterProviderStatusObserver() {
        getContentResolver().unregisterContentObserver(mProviderStatusObserver);
    }

    public int getSummaryDisplayNameColumnIndex() {
        if (mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            return SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
        } else {
            return SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
        }
    }

    /** {@inheritDoc} */
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            // TODO a better way of identifying the button
            case android.R.id.button1: {
                final int position = (Integer)v.getTag();
                Cursor c = mAdapter.getCursor();
                if (c != null) {
                    c.moveToPosition(position);
                    callContact(c);
                }
                break;
            }
        }
    }

    /**
     * Sets the mode when the request is for "default"
     */
    private void setDefaultMode() {
        // Load the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mDisplayOnlyPhones = prefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                Prefs.DISPLAY_ONLY_PHONES_DEFAULT);
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterProviderStatusObserver();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Move to the fragment
        if (mListFragment != null) {
            mListFragment.setContactNameDisplayOrder(mContactsPrefs.getDisplayOrder());
            mListFragment.setSortOrder(mContactsPrefs.getSortOrder());
        }

        // TODO move this to onAttach of the corresponding fragment
        mListView = (ListView) findViewById(android.R.id.list);

        View emptyView = mListView.getEmptyView();
        if (emptyView instanceof ContactListEmptyView) {
            mEmptyView = (ContactListEmptyView)emptyView;
        }

        registerProviderStatusObserver();

        Activity parent = getParent();

        // Do this before setting the filter. The filter thread relies
        // on some state that is initialized in setDefaultMode
        if (mMode == MODE_DEFAULT) {
            // If we're in default mode we need to possibly reset the mode due to a change
            // in the preferences activity while we weren't running
            setDefaultMode();
        }

        if (!mSearchMode && !checkProviderState(mJustCreated)) {
            return;
        }

        if (mJustCreated) {
            // We need to start a query here the first time the activity is launched, as long
            // as we aren't doing a filter.
            startQuery();
        }
        mJustCreated = false;
        mSearchInitiated = false;
    }

    /**
     * Obtains the contacts provider status and configures the UI accordingly.
     *
     * @param loadData true if the method needs to start a query when the
     *            provider is in the normal state
     * @return true if the provider status is normal
     */
    private boolean checkProviderState(boolean loadData) {
        View importFailureView = findViewById(R.id.import_failure);
        if (importFailureView == null) {
            return true;
        }

        TextView messageView = (TextView) findViewById(R.id.emptyText);

        // This query can be performed on the UI thread because
        // the API explicitly allows such use.
        Cursor cursor = getContentResolver().query(ProviderStatus.CONTENT_URI,
                new String[] { ProviderStatus.STATUS, ProviderStatus.DATA1 }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(0);
                    if (status != mProviderStatus) {
                        mProviderStatus = status;
                        switch (status) {
                            case ProviderStatus.STATUS_NORMAL:
                                mAdapter.notifyDataSetInvalidated();
                                if (loadData) {
                                    startQuery();
                                }
                                break;

                            case ProviderStatus.STATUS_CHANGING_LOCALE:
                                messageView.setText(R.string.locale_change_in_progress);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;

                            case ProviderStatus.STATUS_UPGRADING:
                                messageView.setText(R.string.upgrade_in_progress);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;

                            case ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY:
                                long size = cursor.getLong(1);
                                String message = getResources().getString(
                                        R.string.upgrade_out_of_memory, new Object[] {size});
                                messageView.setText(message);
                                configureImportFailureView(importFailureView);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }

        importFailureView.setVisibility(
                mProviderStatus == ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY
                        ? View.VISIBLE
                        : View.GONE);
        return mProviderStatus == ProviderStatus.STATUS_NORMAL;
    }

    private void configureImportFailureView(View importFailureView) {

        OnClickListener listener = new OnClickListener(){

            public void onClick(View v) {
                switch(v.getId()) {
                    case R.id.import_failure_uninstall_apps: {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                        break;
                    }
                    case R.id.import_failure_retry_upgrade: {
                        // Send a provider status update, which will trigger a retry
                        ContentValues values = new ContentValues();
                        values.put(ProviderStatus.STATUS, ProviderStatus.STATUS_UPGRADING);
                        getContentResolver().update(ProviderStatus.CONTENT_URI, values, null, null);
                        break;
                    }
                }
            }};

        Button uninstallApps = (Button) findViewById(R.id.import_failure_uninstall_apps);
        uninstallApps.setOnClickListener(listener);

        Button retryUpgrade = (Button) findViewById(R.id.import_failure_retry_upgrade);
        retryUpgrade.setOnClickListener(listener);
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (!checkProviderState(false)) {
            return;
        }

        // The cursor was killed off in onStop(), so we need to get a new one here
        // We do not perform the query if a filter is set on the list because the
        // filter will cause the query to happen anyway
        if (TextUtils.isEmpty(mListFragment.getQueryString())) {
            startQuery();
        } else {
            // Run the filtered query on the adapter
            mAdapter.onContentChanged();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        mAdapter.changeCursor(null);

        if (mMode == MODE_QUERY) {
            // Make sure the search box is closed
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            searchManager.stopSearch();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // If Contacts was invoked by another Activity simply as a way of
        // picking a contact, don't show the options menu
        if ((mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER) {
            return false;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final boolean defaultMode = (mMode == MODE_DEFAULT);
        menu.findItem(R.id.menu_display_groups).setVisible(defaultMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_display_groups: {
                final Intent intent = new Intent(this, ContactsPreferencesActivity.class);
                startActivityForResult(intent, SUBACTIVITY_DISPLAY_GROUP);
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_add: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivity(intent);
                return true;
            }
            case R.id.menu_import_export: {
                displayImportExportDialog();
                return true;
            }
            case R.id.menu_accounts: {
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    ContactsContract.AUTHORITY
                });
                startActivity(intent);
                return true;
            }
        }
        return false;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
            return;
        }

        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0) {
                if ((mMode & MODE_MASK_PICKER) != 0) {
                    ContactsSearchManager.startSearchForResult(this, initialQuery,
                            SUBACTIVITY_FILTER, null);
                } else {
                    ContactsSearchManager.startSearch(this, initialQuery);
                }
            }
        }
    }

    /**
     * Performs filtering of the list based on the search query entered in the
     * search text edit.
     */
    protected void onSearchTextChanged() {
    }

    /**
     * Starts a new activity that will run a search query and display search results.
     */
    protected void doSearch() {
        String query = mListFragment.getQueryString();
        if (TextUtils.isEmpty(query)) {
            return;
        }

        Intent intent = new Intent(this, SearchResultsActivity.class);
        Intent originalIntent = getIntent();
        Bundle originalExtras = originalIntent.getExtras();
        if (originalExtras != null) {
            intent.putExtras(originalExtras);
        }

        intent.putExtra(SearchManager.QUERY, query);
        if ((mMode & MODE_MASK_PICKER) != 0) {
            intent.setAction(ACTION_SEARCH_INTERNAL);
            intent.putExtra(SHORTCUT_ACTION_KEY, mShortcutAction);
            if (mShortcutAction != null) {
                if (Intent.ACTION_CALL.equals(mShortcutAction)
                        || Intent.ACTION_SENDTO.equals(mShortcutAction)) {
                    intent.putExtra(Insert.PHONE, query);
                }
            } else {
                switch (mQueryMode) {
                    case QUERY_MODE_MAILTO:
                        intent.putExtra(Insert.EMAIL, query);
                        break;
                    case QUERY_MODE_TEL:
                        intent.putExtra(Insert.PHONE, query);
                        break;
                }
            }
            startActivityForResult(intent, SUBACTIVITY_SEARCH);
        } else {
            intent.setAction(Intent.ACTION_SEARCH);
            startActivity(intent);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.string.import_from_sim:
            case R.string.import_from_sdcard: {
                return AccountSelectionUtil.getSelectAccountDialog(this, id);
            }
            case R.id.dialog_sdcard_not_found: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.no_sdcard_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.no_sdcard_message)
                        .setPositiveButton(android.R.string.ok, null).create();
            }
            case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_hide_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_delete_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_multiple_contact_delete_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
        }
        return super.onCreateDialog(id, bundle);
    }

    /**
     * Create a {@link Dialog} that allows the user to pick from a bulk import
     * or bulk export task across all contacts.
     */
    private void displayImportExportDialog() {
        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(this, android.R.style.Theme_Light);
        final Resources res = dialogContext.getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this,
                android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(android.R.layout.simple_list_item_1,
                            parent, false);
                }

                final int resId = this.getItem(position);
                ((TextView)convertView).setText(resId);
                return convertView;
            }
        };

        if (TelephonyManager.getDefault().hasIccCard()) {
            adapter.add(R.string.import_from_sim);
        }
        if (res.getBoolean(R.bool.config_allow_import_from_sdcard)) {
            adapter.add(R.string.import_from_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_export_to_sdcard)) {
            adapter.add(R.string.export_to_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_share_visible_contacts)) {
            adapter.add(R.string.share_visible_contacts);
        }

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                final int resId = adapter.getItem(which);
                switch (resId) {
                    case R.string.import_from_sim:
                    case R.string.import_from_sdcard: {
                        handleImportRequest(resId);
                        break;
                    }
                    case R.string.export_to_sdcard: {
                        Context context = ContactsListActivity.this;
                        Intent exportIntent = new Intent(context, ExportVCardActivity.class);
                        context.startActivity(exportIntent);
                        break;
                    }
                    case R.string.share_visible_contacts: {
                        doShareVisibleContacts();
                        break;
                    }
                    default: {
                        Log.e(TAG, "Unexpected resource: " +
                                getResources().getResourceEntryName(resId));
                    }
                }
            }
        };

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_import_export)
            .setNegativeButton(android.R.string.cancel, null)
            .setSingleChoiceItems(adapter, -1, clickListener)
            .show();
    }

    private void doShareVisibleContacts() {
        final Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI,
                sLookupProjection, getContactSelection(), null, null);
        try {
            if (!cursor.moveToFirst()) {
                Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder uriListBuilder = new StringBuilder();
            int index = 0;
            for (;!cursor.isAfterLast(); cursor.moveToNext()) {
                if (index != 0)
                    uriListBuilder.append(':');
                uriListBuilder.append(cursor.getString(0));
                index++;
            }
            Uri uri = Uri.withAppendedPath(
                    Contacts.CONTENT_MULTI_VCARD_URI,
                    Uri.encode(uriListBuilder.toString()));

            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(Contacts.CONTENT_VCARD_TYPE);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(intent);
        } finally {
            cursor.close();
        }
    }

    private void handleImportRequest(int resId) {
        // There's three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final Sources sources = Sources.getInstance(this);
        final List<Account> accountList = sources.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            showDialog(resId);
            return;
        }

        AccountSelectionUtil.doImport(this, resId, (size == 1 ? accountList.get(0) : null));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SUBACTIVITY_NEW_CONTACT:
                if (resultCode == RESULT_OK) {
                    returnPickerResult(null, data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME),
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

            case SUBACTIVITY_FILTER:
            case SUBACTIVITY_SEARCH:
                // Pass through results of filter or search UI
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK, data);
                    finish();
                } else if (resultCode == RESULT_CANCELED && mMode == MODE_PICK_MULTIPLE_PHONES) {
                    // Finish the activity if the sub activity was canceled as back key is used
                    // to confirm user selection in MODE_PICK_MULTIPLE_PHONES.
                    finish();
                }
                break;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuAdapter menuAdapter = mListFragment.getContextMenuAdapter();
        if (menuAdapter != null) {
            return menuAdapter.onContextItemSelected(item);
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Event handler for the use case where the user starts typing without
     * bringing up the search UI first.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0 && !mSearchInitiated) {
            int unicodeChar = event.getUnicodeChar();
            if (unicodeChar != 0) {
                mSearchInitiated = true;
                startSearch(new String(new int[]{unicodeChar}, 0, 1), false, null, false);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
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
                if (deleteSelection()) {
                    return true;
                }
                break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean deleteSelection() {
        if ((mMode & MODE_MASK_PICKER) != 0) {
            return false;
        }

        final int position = mListView.getSelectedItemPosition();
        if (position != ListView.INVALID_POSITION) {
            Uri contactUri = getContactUri(position);
            if (contactUri != null) {
                doContactDelete(contactUri);
                return true;
            }
        }
        return false;
    }

    /**
     * Prompt the user before deleting the given {@link Contacts} entry.
     */
    protected void doContactDelete(Uri contactUri) {
        mReadOnlySourcesCnt = 0;
        mWritableSourcesCnt = 0;
        mWritableRawContactIds.clear();

        Sources sources = Sources.getInstance(ContactsListActivity.this);
        Cursor c = getContentResolver().query(RawContacts.CONTENT_URI, RAW_CONTACTS_PROJECTION,
                RawContacts.CONTACT_ID + "=" + ContentUris.parseId(contactUri), null,
                null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    final String accountType = c.getString(2);
                    final long rawContactId = c.getLong(0);
                    ContactsSource contactsSource = sources.getInflatedSource(accountType,
                            ContactsSource.LEVEL_SUMMARY);
                    if (contactsSource != null && contactsSource.readOnly) {
                        mReadOnlySourcesCnt += 1;
                    } else {
                        mWritableSourcesCnt += 1;
                        mWritableRawContactIds.add(rawContactId);
                    }
                }
            } finally {
                c.close();
            }
        }

        mSelectedContactUri = contactUri;
        if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt > 0) {
            showDialog(R.id.dialog_readonly_contact_delete_confirmation);
        } else if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt == 0) {
            showDialog(R.id.dialog_readonly_contact_hide_confirmation);
        } else if (mReadOnlySourcesCnt == 0 && mWritableSourcesCnt > 1) {
            showDialog(R.id.dialog_multiple_contact_delete_confirmation);
        } else {
            showDialog(R.id.dialog_delete_contact_confirmation);
        }
    }

    public void onListItemClick(int position, long id) {
        if (mSearchMode &&
                ((ContactItemListAdapter)(mAdapter)).isSearchAllContactsItemPosition(position)) {
            doSearch();
        } else if (mMode == MODE_INSERT_OR_EDIT_CONTACT || mMode == MODE_QUERY_PICK_TO_EDIT) {
            Intent intent;
            if (position == 0 && !mSearchMode && mMode != MODE_QUERY_PICK_TO_EDIT) {
                intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            } else {
                intent = new Intent(Intent.ACTION_EDIT, getSelectedUri(position));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            intent.putExtra(KEY_PICKER_MODE, (mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER);

            startActivity(intent);
            finish();
        } else if ((mMode & MODE_MASK_CREATE_NEW) == MODE_MASK_CREATE_NEW
                && position == 0) {
            Intent newContact = new Intent(Intents.Insert.ACTION, Contacts.CONTENT_URI);
            startActivityForResult(newContact, SUBACTIVITY_NEW_CONTACT);
        } else if (id > 0) {
            final Uri uri = getSelectedUri(position);
            if ((mMode & MODE_MASK_PICKER) == 0) {
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivityForResult(intent, SUBACTIVITY_VIEW_CONTACT);
            } else if (mMode == MODE_QUERY_PICK_TO_VIEW) {
                // Started with query that should launch to view contact
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                finish();
            } else if (mMode == MODE_PICK_PHONE || mMode == MODE_QUERY_PICK_PHONE) {
                Cursor c = (Cursor) mAdapter.getItem(position);
                returnPickerResult(c, c.getString(PHONE_DISPLAY_NAME_COLUMN_INDEX), uri);
            } else if ((mMode & MODE_MASK_PICKER) != 0) {
                Cursor c = (Cursor) mAdapter.getItem(position);
                returnPickerResult(c, c.getString(getSummaryDisplayNameColumnIndex()), uri);
            } else if (mMode == MODE_PICK_POSTAL
                    || mMode == MODE_LEGACY_PICK_POSTAL
                    || mMode == MODE_LEGACY_PICK_PHONE) {
                returnPickerResult(null, null, uri);
            }
        } else {
            signalError();
        }
    }

    @Deprecated
    private void returnPickerResult(Cursor c, String string, Uri uri) {
    }

    protected Uri getUriToQuery() {
        switch(mMode) {
            case MODE_FREQUENT:
            case MODE_STARRED:
                return Contacts.CONTENT_URI;

            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_INSERT_OR_EDIT_CONTACT:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:{
                return CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS;
            }
            case MODE_STREQUENT: {
                return Contacts.CONTENT_STREQUENT_URI;
            }
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return People.CONTENT_URI;
            }
            case MODE_PICK_MULTIPLE_PHONES:
            case MODE_PICK_PHONE: {
                return buildSectionIndexerUri(Phone.CONTENT_URI);
            }
            case MODE_LEGACY_PICK_PHONE: {
                return Phones.CONTENT_URI;
            }
            case MODE_PICK_POSTAL: {
                return buildSectionIndexerUri(StructuredPostal.CONTENT_URI);
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return ContactMethods.CONTENT_URI;
            }
            case MODE_QUERY_PICK_TO_VIEW: {
                if (mQueryMode == QUERY_MODE_MAILTO) {
                    return Uri.withAppendedPath(Email.CONTENT_FILTER_URI,
                            Uri.encode(mInitialFilter));
                } else if (mQueryMode == QUERY_MODE_TEL) {
                    return Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,
                            Uri.encode(mInitialFilter));
                }
                return CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS;
            }
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_TO_EDIT: {
                return getContactFilterUri(mInitialFilter);
            }
            case MODE_QUERY_PICK_PHONE: {
                return Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,
                        Uri.encode(mInitialFilter));
            }
            case MODE_GROUP: {
                return Uri.withAppendedPath(Contacts.CONTENT_GROUP_URI, mGroupName);
            }
            default: {
                throw new IllegalStateException("Can't generate URI: Unsupported Mode.");
            }
        }
    }

    /**
     * Build the {@link Contacts#CONTENT_LOOKUP_URI} for the given
     * {@link ListView} position, using {@link #mAdapter}.
     */
    private Uri getContactUri(int position) {
        if (position == ListView.INVALID_POSITION) {
            throw new IllegalArgumentException("Position not in list bounds");
        }

        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        if (cursor == null) {
            return null;
        }

        switch(mMode) {
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                final long personId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                return ContentUris.withAppendedId(People.CONTENT_URI, personId);
            }

            default: {
                // Build and return soft, lookup reference
                final long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                final String lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                return Contacts.getLookupUri(contactId, lookupKey);
            }
        }
    }

    /**
     * Build the {@link Uri} for the given {@link ListView} position, which can
     * be used as result when in {@link #MODE_MASK_PICKER} mode.
     */
    protected Uri getSelectedUri(int position) {
        if (position == ListView.INVALID_POSITION) {
            throw new IllegalArgumentException("Position not in list bounds");
        }

        final long id = mAdapter.getItemId(position);
        switch(mMode) {
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return ContentUris.withAppendedId(People.CONTENT_URI, id);
            }
            case MODE_PICK_PHONE:
            case MODE_QUERY_PICK_PHONE: {
                return ContentUris.withAppendedId(Data.CONTENT_URI, id);
            }
            case MODE_LEGACY_PICK_PHONE: {
                return ContentUris.withAppendedId(Phones.CONTENT_URI, id);
            }
            case MODE_PICK_POSTAL: {
                return ContentUris.withAppendedId(Data.CONTENT_URI, id);
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return ContentUris.withAppendedId(ContactMethods.CONTENT_URI, id);
            }
            default: {
                return getContactUri(position);
            }
        }
    }

    String[] getProjectionForQuery() {
        switch(mMode) {
            case MODE_STREQUENT:
            case MODE_FREQUENT:
            case MODE_STARRED:
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_INSERT_OR_EDIT_CONTACT:
            case MODE_GROUP:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT: {
                return mSearchMode
                        ? CONTACTS_SUMMARY_FILTER_PROJECTION
                        : CONTACTS_SUMMARY_PROJECTION;
            }
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_TO_EDIT: {
                return CONTACTS_SUMMARY_FILTER_PROJECTION;
            }
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return LEGACY_PEOPLE_PROJECTION ;
            }
            case MODE_QUERY_PICK_PHONE:
            case MODE_PICK_MULTIPLE_PHONES:
            case MODE_PICK_PHONE: {
                return PHONES_PROJECTION;
            }
            case MODE_LEGACY_PICK_PHONE: {
                return LEGACY_PHONES_PROJECTION;
            }
            case MODE_PICK_POSTAL: {
                return POSTALS_PROJECTION;
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return LEGACY_POSTALS_PROJECTION;
            }
            case MODE_QUERY_PICK_TO_VIEW: {
                if (mQueryMode == QUERY_MODE_MAILTO) {
                    return CONTACTS_SUMMARY_PROJECTION_FROM_EMAIL;
                } else if (mQueryMode == QUERY_MODE_TEL) {
                    return PHONES_PROJECTION;
                }
                break;
            }
        }

        // Default to normal aggregate projection
        return CONTACTS_SUMMARY_PROJECTION;
    }

    /**
     * Return the selection arguments for a default query based on the
     * {@link #mDisplayOnlyPhones} flag.
     */
    private String getContactSelection() {
        if (mDisplayOnlyPhones) {
            return CLAUSE_ONLY_VISIBLE + " AND " + CLAUSE_ONLY_PHONES;
        } else {
            return CLAUSE_ONLY_VISIBLE;
        }
    }

    protected Uri getContactFilterUri(String filter) {
        Uri baseUri;
        if (!TextUtils.isEmpty(filter)) {
            baseUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(filter));
        } else {
            baseUri = Contacts.CONTENT_URI;
        }

        if (mListFragment.isSectionHeaderDisplayEnabled()) {
            return buildSectionIndexerUri(baseUri);
        } else {
            return baseUri;
        }
    }

    private Uri getPeopleFilterUri(String filter) {
        if (!TextUtils.isEmpty(filter)) {
            return Uri.withAppendedPath(People.CONTENT_FILTER_URI, Uri.encode(filter));
        } else {
            return People.CONTENT_URI;
        }
    }

    private static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }


    protected String getSortOrder(String[] projectionType) {
        String sortKey;
        if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            sortKey = Contacts.SORT_KEY_PRIMARY;
        } else {
            sortKey = Contacts.SORT_KEY_ALTERNATIVE;
        }
        switch (mMode) {
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON:
                sortKey = Contacts.DISPLAY_NAME;
                break;
            case MODE_LEGACY_PICK_PHONE:
                sortKey = People.DISPLAY_NAME;
                break;
        }
        return sortKey;
    }

    public void startQuery() {

        // Disabled
        if (true) {
            return;
        }

        if (mSearchResultsMode) {
            TextView foundContactsText = (TextView)findViewById(R.id.search_results_found);
            foundContactsText.setText(R.string.search_results_searching);
        }

        if (mEmptyView != null) {
            mEmptyView.hide();
        }

        // TODO reintroduce the loading state handling
//        mAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);

        mSortOrder = mContactsPrefs.getSortOrder();
        mDisplayOrder = mContactsPrefs.getDisplayOrder();

        if (mListFragment != null) {
            mListFragment.setContactNameDisplayOrder(mDisplayOrder);
            mListFragment.setSortOrder(mSortOrder);
        }

        if (mListView instanceof ContactEntryListView) {
            ContactEntryListView listView = (ContactEntryListView)mListView;

            // When sort order and display order contradict each other, we want to
            // highlight the part of the name used for sorting.
            if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY &&
                    mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE) {
                listView.setHighlightNamesWhenScrolling(true);
                mAdapter.setNameHighlightingEnabled(true);
            } else if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE &&
                    mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                listView.setHighlightNamesWhenScrolling(true);
                mAdapter.setNameHighlightingEnabled(true);
            } else {
                listView.setHighlightNamesWhenScrolling(false);
                mAdapter.setNameHighlightingEnabled(false);
            }
        }

        String[] projection = getProjectionForQuery();
        if (mSearchMode && TextUtils.isEmpty(mListFragment.getQueryString())) {
            mAdapter.changeCursor(new MatrixCursor(projection));
            return;
        }

        String callingPackage = getCallingPackage();
        Uri uri = getUriToQuery();
        if (!TextUtils.isEmpty(callingPackage)) {
            uri = uri.buildUpon()
                    .appendQueryParameter(ContactsContract.REQUESTING_PACKAGE_PARAM_KEY,
                            callingPackage)
                    .build();
        }

        startQuery(uri, projection);
    }

    protected void startQuery(Uri uri, String[] projection) {
        // Kick off the new query
        switch (mMode) {
            case MODE_GROUP:
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_INSERT_OR_EDIT_CONTACT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, getContactSelection(),
                        null, getSortOrder(projection));
                break;

            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null,
                        People.DISPLAY_NAME);
                break;
            }
            case MODE_PICK_POSTAL:
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_PHONE:
            case MODE_QUERY_PICK_TO_VIEW:
            case MODE_QUERY_PICK_TO_EDIT: {
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null,
                        getSortOrder(projection));
                break;
            }

            case MODE_STARRED:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, Contacts.STARRED + "=1", null,
                        getSortOrder(projection));
                break;

            case MODE_FREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection,
                        Contacts.TIMES_CONTACTED + " > 0", null,
                        Contacts.TIMES_CONTACTED + " DESC, "
                        + getSortOrder(projection));
                break;

            case MODE_STREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null, null);
                break;

            case MODE_PICK_PHONE:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, CLAUSE_ONLY_VISIBLE, null, getSortOrder(projection));
                break;

            case MODE_LEGACY_PICK_PHONE:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, null, null, Phones.DISPLAY_NAME);
                break;

            case MODE_LEGACY_PICK_POSTAL:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection,
                        ContactMethods.KIND + "=" + android.provider.Contacts.KIND_POSTAL, null,
                        ContactMethods.DISPLAY_NAME);
                break;
        }
    }

    protected void startQuery(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection, selectionArgs,
                sortOrder);
    }

    /**
     * Called from a background thread to do the filter and return the resulting cursor.
     *
     * @param filter the text that was entered to filter on
     * @return a cursor with the results of the filter
     */
    public Cursor doFilter(String filter) {
        String[] projection = getProjectionForQuery();
        if (mSearchMode && TextUtils.isEmpty(mListFragment.getQueryString())) {
            return new MatrixCursor(projection);
        }

        final ContentResolver resolver = getContentResolver();
        switch (mMode) {
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_INSERT_OR_EDIT_CONTACT: {
                return resolver.query(getContactFilterUri(filter), projection,
                        getContactSelection(), null, getSortOrder(projection));
            }

            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return resolver.query(getPeopleFilterUri(filter), projection, null, null,
                        People.DISPLAY_NAME);
            }

            case MODE_STARRED: {
                return resolver.query(getContactFilterUri(filter), projection,
                        Contacts.STARRED + "=1", null,
                        getSortOrder(projection));
            }

            case MODE_FREQUENT: {
                return resolver.query(getContactFilterUri(filter), projection,
                        Contacts.TIMES_CONTACTED + " > 0", null,
                        Contacts.TIMES_CONTACTED + " DESC, "
                        + getSortOrder(projection));
            }

            case MODE_STREQUENT: {
                Uri uri;
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Contacts.CONTENT_STREQUENT_FILTER_URI,
                            Uri.encode(filter));
                } else {
                    uri = Contacts.CONTENT_STREQUENT_URI;
                }
                return resolver.query(uri, projection, null, null, null);
            }

            case MODE_PICK_PHONE: {
                Uri uri = getUriToQuery();
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(filter));
                }
                return resolver.query(uri, projection, CLAUSE_ONLY_VISIBLE, null,
                        getSortOrder(projection));
            }

            case MODE_LEGACY_PICK_PHONE: {
                //TODO: Support filtering here (bug 2092503)
                break;
            }
        }
        throw new UnsupportedOperationException("filtering not allowed in mode " + mMode);
    }


    /**
     * Calls the currently selected list item.
     * @return true if the call was initiated, false otherwise
     */
    boolean callSelection() {
        ListView list = mListView;
        if (list.hasFocus()) {
            Cursor cursor = (Cursor) list.getSelectedItem();
            return callContact(cursor);
        }
        return false;
    }

    boolean callContact(Cursor cursor) {
        return callOrSmsContact(cursor, false /*call*/);
    }

    boolean smsContact(Cursor cursor) {
        return callOrSmsContact(cursor, true /*sms*/);
    }

    /**
     * Calls the contact which the cursor is point to.
     * @return true if the call was initiated, false otherwise
     */
    boolean callOrSmsContact(Cursor cursor, boolean sendSms) {
        if (cursor == null) {
            return false;
        }

        switch (mMode) {
            case MODE_PICK_PHONE:
            case MODE_LEGACY_PICK_PHONE:
            case MODE_QUERY_PICK_PHONE: {
                String phone = cursor.getString(PHONE_NUMBER_COLUMN_INDEX);
                if (sendSms) {
                    ContactsUtils.initiateSms(this, phone);
                } else {
                    ContactsUtils.initiateCall(this, phone);
                }
                return true;
            }

            case MODE_PICK_POSTAL:
            case MODE_LEGACY_PICK_POSTAL: {
                return false;
            }

            default: {

                boolean hasPhone = cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;
                if (!hasPhone) {
                    // There is no phone number.
                    signalError();
                    return false;
                }

                String phone = null;
                Cursor phonesCursor = null;
                phonesCursor = queryPhoneNumbers(cursor.getLong(SUMMARY_ID_COLUMN_INDEX));
                if (phonesCursor == null || phonesCursor.getCount() == 0) {
                    // No valid number
                    signalError();
                    return false;
                } else if (phonesCursor.getCount() == 1) {
                    // only one number, call it.
                    phone = phonesCursor.getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                } else {
                    phonesCursor.moveToPosition(-1);
                    while (phonesCursor.moveToNext()) {
                        if (phonesCursor.getInt(phonesCursor.
                                getColumnIndex(Phone.IS_SUPER_PRIMARY)) != 0) {
                            // Found super primary, call it.
                            phone = phonesCursor.
                            getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                            break;
                        }
                    }
                }

                if (phone == null) {
                    // Display dialog to choose a number to call.
                    PhoneDisambigDialog phoneDialog = new PhoneDisambigDialog(
                            this, phonesCursor, sendSms);
                    phoneDialog.show();
                } else {
                    if (sendSms) {
                        ContactsUtils.initiateSms(this, phone);
                    } else {
                        ContactsUtils.initiateCall(this, phone);
                    }
                }
            }
        }
        return true;
    }

    // TODO: eliminate
    @Deprecated
    private Cursor queryPhoneNumbers(long contactId) {
        Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        Cursor c = getContentResolver().query(dataUri,
                new String[] {Phone._ID, Phone.NUMBER, Phone.IS_SUPER_PRIMARY,
                        RawContacts.ACCOUNT_TYPE, Phone.TYPE, Phone.LABEL},
                Data.MIMETYPE + "=?", new String[] {Phone.CONTENT_ITEM_TYPE}, null);
        if (c != null && c.moveToFirst()) {
            return c;
        }
        return null;
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getString(zeroResourceId);
        } else {
            String format = getResources().getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }

    /**
     * Signal an error to the user.
     */
    void signalError() {
        //TODO play an error beep or something...
    }

    Cursor getItemForView(View view) {
        int index = mListView.getPositionForView(view);
        if (index < 0) {
            return null;
        }
        return (Cursor) mListView.getAdapter().getItem(index);
    }

    protected class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<ContactsListActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<ContactsListActivity>((ContactsListActivity) context);
        }

        @Override
        public void startQuery(int token, Object cookie, Uri uri, String[] projection,
                String selection, String[] selectionArgs, String orderBy) {
            final ContactsListActivity activity = mActivity.get();
            if (activity != null && activity.mRunQueriesSynchronously) {
                Cursor cursor = getContentResolver().query(uri, projection, selection,
                        selectionArgs, orderBy);
                onQueryComplete(token, cookie, cursor);
            } else {
                super.startQuery(token, cookie, uri, projection, selection, selectionArgs, orderBy);
            }
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final ContactsListActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {
                activity.onQueryComplete(cursor);
            } else {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    protected void onQueryComplete(Cursor cursor) {
        mAdapter.changeCursor(cursor);

        // TODO make this triggered by the Loader
        mListFragment.completeRestoreInstanceState();
    }

    private CallOrSmsInitiator getCallOrSmsInitiator() {
        if (mCallOrSmsInitiator == null) {
            mCallOrSmsInitiator = new CallOrSmsInitiator(this);
        }
        return mCallOrSmsInitiator;
    }
}
