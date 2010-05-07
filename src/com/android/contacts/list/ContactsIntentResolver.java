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
 * limitations under the License.
 */

package com.android.contacts.list;

import com.android.contacts.ContactsSearchManager;
import com.android.contacts.JoinContactActivity;
import com.android.contacts.R;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Intents.UI;
import android.text.TextUtils;
import android.util.Log;

/**
 * Maintains contact list configuration, which is a transient object that
 * deals with intents, saved instance configuration etc.
 */
@SuppressWarnings("deprecation")
public class ContactsIntentResolver {

    private static final String TAG = "ContactsListActivity";

    private static final String SHORTCUT_ACTION_KEY = "shortcutAction";

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
    /** Mask for showing a call button in the list */
    static final int MODE_MASK_SHOW_CALL_BUTTON = 0x02000000;
    /** Mask to disable quickcontact (images will show as normal images) */
    static final int MODE_MASK_DISABLE_QUIKCCONTACT = 0x01000000;
    /** Mask to show the total number of contacts at the top */
    static final int MODE_MASK_SHOW_NUMBER_OF_CONTACTS = 0x00800000;

    /** Unknown mode */
    static final int MODE_UNKNOWN = 0;
    /** Default mode */
    static final int MODE_DEFAULT = 4 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;
    /** Custom mode */
    static final int MODE_CUSTOM = 8;
    /** Show all starred contacts */
    static final int MODE_STARRED = 20 | MODE_MASK_SHOW_PHOTOS;
    /** Show frequently contacted contacts */
    static final int MODE_FREQUENT = 30 | MODE_MASK_SHOW_PHOTOS;
    /** Show starred and the frequent */
    static final int MODE_STREQUENT = 35 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_SHOW_CALL_BUTTON;
    /** Show all contacts and pick them when clicking */
    static final int MODE_PICK_CONTACT = 40 | MODE_MASK_PICKER | MODE_MASK_SHOW_PHOTOS
            | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all contacts as well as the option to create a new one */
    static final int MODE_PICK_OR_CREATE_CONTACT = 42 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW
            | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all people through the legacy provider and pick them when clicking */
    static final int MODE_LEGACY_PICK_PERSON = 43 | MODE_MASK_PICKER
            | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all people through the legacy provider as well as the option to create a new one */
    static final int MODE_LEGACY_PICK_OR_CREATE_PERSON = 44 | MODE_MASK_PICKER
            | MODE_MASK_CREATE_NEW | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all contacts and pick them when clicking, and allow creating a new contact */
    static final int MODE_INSERT_OR_EDIT_CONTACT = 45 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW
            | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all phone numbers and pick them when clicking */
    static final int MODE_PICK_PHONE = 50 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE;
    /** Show all phone numbers through the legacy provider and pick them when clicking */
    static final int MODE_LEGACY_PICK_PHONE =
            51 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Show all postal addresses and pick them when clicking */
    static final int MODE_PICK_POSTAL =
            55 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Show all postal addresses and pick them when clicking */
    static final int MODE_LEGACY_PICK_POSTAL =
            56 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    static final int MODE_GROUP = 57 | MODE_MASK_SHOW_PHOTOS;
    /** Run a search query */
    static final int MODE_QUERY = 60 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_NO_FILTER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;
    /** Run a search query in PICK mode, but that still launches to VIEW */
    static final int MODE_QUERY_PICK_TO_VIEW = 65 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_PICKER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Show join suggestions followed by an A-Z list */
    static final int MODE_JOIN_CONTACT = 70 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE
            | MODE_MASK_NO_DATA | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;

    /** Run a search query in a PICK mode */
    static final int MODE_QUERY_PICK = 75 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_NO_FILTER
            | MODE_MASK_PICKER | MODE_MASK_DISABLE_QUIKCCONTACT | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in a PICK_PHONE mode */
    static final int MODE_QUERY_PICK_PHONE = 80 | MODE_MASK_NO_FILTER | MODE_MASK_PICKER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in PICK mode, but that still launches to EDIT */
    static final int MODE_QUERY_PICK_TO_EDIT = 85 | MODE_MASK_NO_FILTER | MODE_MASK_SHOW_PHOTOS
            | MODE_MASK_PICKER | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /**
     * Show all phone numbers and do multiple pick when clicking. This mode has phone filtering
     * feature, but doesn't support 'search for all contacts'.
     */
    static final int MODE_PICK_MULTIPLE_PHONES = 80 | MODE_MASK_PICKER
            | MODE_MASK_NO_PRESENCE | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;

    /**
     * An action used to do perform search while in a contact picker.  It is initiated
     * by the ContactListActivity itself.
     */
    private static final String ACTION_SEARCH_INTERNAL = "com.android.contacts.INTERNAL_SEARCH";

    // Uri matcher for contact id
    private static final int CONTACTS_ID = 1001;
    private static final UriMatcher sContactsIdMatcher;
    static {
        sContactsIdMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sContactsIdMatcher.addURI(ContactsContract.AUTHORITY, "contacts/#", CONTACTS_ID);
    }

    private final Activity mContext;
    private boolean mValid = true;
    private Intent mRedirectIntent;
    private CharSequence mTitle;

    // TODO: make all these fields private.  They should only remain public while we
    // are refactoring ContactListActivity.
    public int mMode = MODE_DEFAULT;
    public int mQueryMode = QUERY_MODE_NONE;
    public boolean mSearchMode;
    public boolean mShowSearchSnippets;
    public String mInitialFilter;
    public boolean mDisplayOnlyPhones;
    public String mShortcutAction;
    public boolean mSearchResultsMode;
    public boolean mShowNumberOfContacts;
    public String mGroupName;

    /**
     * Internal query type when in mode {@link #MODE_QUERY_PICK_TO_VIEW}.
     */
    private static final int QUERY_MODE_NONE = -1;
    private static final int QUERY_MODE_MAILTO = 1;
    private static final int QUERY_MODE_TEL = 2;

    public ContactsIntentResolver(Activity context) {
        this.mContext = context;
    }

    public void setIntent(Intent intent) {
        String action = intent.getAction();
        String component = intent.getComponent().getClassName();

        // Allow the title to be set to a custom String using an extra on the intent
        String title = intent.getStringExtra(UI.TITLE_EXTRA_KEY);
        if (title != null) {
            mTitle = title;
        }

        String type = intent.getType();

        // When we get a FILTER_CONTACTS_ACTION, it represents search in the context
        // of some other action. Let's retrieve the original action to provide proper
        // context for the search queries.
        if (UI.FILTER_CONTACTS_ACTION.equals(action)) {
            mSearchMode = true;
            mShowSearchSnippets = true;
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mInitialFilter = extras.getString(UI.FILTER_TEXT_EXTRA_KEY);
                String originalAction =
                        extras.getString(ContactsSearchManager.ORIGINAL_ACTION_EXTRA_KEY);
                if (originalAction != null) {
                    action = originalAction;
                }
                String originalComponent =
                        extras.getString(ContactsSearchManager.ORIGINAL_COMPONENT_EXTRA_KEY);
                if (originalComponent != null) {
                    component = originalComponent;
                }
                String originalType =
                    extras.getString(ContactsSearchManager.ORIGINAL_TYPE_EXTRA_KEY);
                if (originalType != null) {
                    type = originalType;
                }
            } else {
                mInitialFilter = null;
            }
        }

        Log.i(TAG, "Called with action: " + action);
        mMode = MODE_UNKNOWN;
        if (UI.LIST_DEFAULT.equals(action) || UI.FILTER_CONTACTS_ACTION.equals(action)) {
            mMode = MODE_DEFAULT;
            // When mDefaultMode is true the mode is set in onResume(), since the preferneces
            // activity may change it whenever this activity isn't running
        } else if (UI.LIST_GROUP_ACTION.equals(action)) {
            mMode = MODE_GROUP;
            mGroupName = intent.getStringExtra(UI.GROUP_NAME_EXTRA_KEY);
            if (TextUtils.isEmpty(mGroupName)) {
                mValid = false;
                return;
            }
        } else if (UI.LIST_ALL_CONTACTS_ACTION.equals(action)) {
            mMode = MODE_CUSTOM;
            mDisplayOnlyPhones = false;
        } else if (UI.LIST_STARRED_ACTION.equals(action)) {
            mMode = mSearchMode ? MODE_DEFAULT : MODE_STARRED;
        } else if (UI.LIST_FREQUENT_ACTION.equals(action)) {
            mMode = mSearchMode ? MODE_DEFAULT : MODE_FREQUENT;
        } else if (UI.LIST_STREQUENT_ACTION.equals(action)) {
            mMode = mSearchMode ? MODE_DEFAULT : MODE_STREQUENT;
        } else if (UI.LIST_CONTACTS_WITH_PHONES_ACTION.equals(action)) {
            mMode = MODE_CUSTOM;
            mDisplayOnlyPhones = true;
        } else if (Intent.ACTION_PICK.equals(action)) {
            // XXX These should be showing the data from the URI given in
            // the Intent.
           // TODO : Does it work in mSearchMode?
            final String resolvedType = intent.resolveType(mContext);
            if (Contacts.CONTENT_TYPE.equals(resolvedType)) {
                mMode = MODE_PICK_CONTACT;
            } else if (People.CONTENT_TYPE.equals(resolvedType)) {
                mMode = MODE_LEGACY_PICK_PERSON;
            } else if (Phone.CONTENT_TYPE.equals(resolvedType)) {
                mMode = MODE_PICK_PHONE;
            } else if (Phones.CONTENT_TYPE.equals(resolvedType)) {
                mMode = MODE_LEGACY_PICK_PHONE;
            } else if (StructuredPostal.CONTENT_TYPE.equals(resolvedType)) {
                mMode = MODE_PICK_POSTAL;
            } else if (ContactMethods.CONTENT_POSTAL_TYPE.equals(resolvedType)) {
                mMode = MODE_LEGACY_PICK_POSTAL;
            }
        } else if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            if (component.equals("alias.DialShortcut")) {
                mMode = MODE_PICK_PHONE;
                mShortcutAction = Intent.ACTION_CALL;
                mShowSearchSnippets = false;
                mTitle = mContext.getString(R.string.callShortcutActivityTitle);
            } else if (component.equals("alias.MessageShortcut")) {
                mMode = MODE_PICK_PHONE;
                mShortcutAction = Intent.ACTION_SENDTO;
                mShowSearchSnippets = false;
                mTitle = mContext.getString(R.string.messageShortcutActivityTitle);
            } else if (mSearchMode) {
                mMode = MODE_PICK_CONTACT;
                mShortcutAction = Intent.ACTION_VIEW;
                mTitle = mContext.getString(R.string.shortcutActivityTitle);
            } else {
                mMode = MODE_PICK_OR_CREATE_CONTACT;
                mShortcutAction = Intent.ACTION_VIEW;
                mTitle = mContext.getString(R.string.shortcutActivityTitle);
            }
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            if (Contacts.CONTENT_ITEM_TYPE.equals(type)) {
                if (mSearchMode) {
                    mMode = MODE_PICK_CONTACT;
                } else {
                    mMode = MODE_PICK_OR_CREATE_CONTACT;
                }
            } else if (Phone.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_PHONE;
            } else if (Phones.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_LEGACY_PICK_PHONE;
            } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_POSTAL;
            } else if (ContactMethods.CONTENT_POSTAL_ITEM_TYPE.equals(type)) {
                mMode = MODE_LEGACY_PICK_POSTAL;
            }  else if (People.CONTENT_ITEM_TYPE.equals(type)) {
                if (mSearchMode) {
                    mMode = MODE_LEGACY_PICK_PERSON;
                } else {
                    mMode = MODE_LEGACY_PICK_OR_CREATE_PERSON;
                }
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
                    mRedirectIntent = newIntent;
                }
                return;
            }

            // See if search request has extras to specify query
            if (intent.hasExtra(Insert.EMAIL)) {
                mMode = MODE_QUERY_PICK_TO_VIEW;
                mQueryMode = QUERY_MODE_MAILTO;
                mInitialFilter = intent.getStringExtra(Insert.EMAIL);
            } else if (intent.hasExtra(Insert.PHONE)) {
                mMode = MODE_QUERY_PICK_TO_VIEW;
                mQueryMode = QUERY_MODE_TEL;
                mInitialFilter = intent.getStringExtra(Insert.PHONE);
            } else {
                // Otherwise handle the more normal search case
                mMode = MODE_QUERY;
                mShowSearchSnippets = true;
                mInitialFilter = intent.getStringExtra(SearchManager.QUERY);
            }
            mSearchResultsMode = true;
        } else if (ACTION_SEARCH_INTERNAL.equals(action)) {
            String originalAction = null;
            Bundle extras = intent.getExtras();
            if (extras != null) {
                originalAction = extras.getString(ContactsSearchManager.ORIGINAL_ACTION_EXTRA_KEY);
            }
            mShortcutAction = intent.getStringExtra(SHORTCUT_ACTION_KEY);

            if (Intent.ACTION_INSERT_OR_EDIT.equals(originalAction)) {
                mMode = MODE_QUERY_PICK_TO_EDIT;
                mShowSearchSnippets = true;
                mInitialFilter = intent.getStringExtra(SearchManager.QUERY);
            } else if (mShortcutAction != null && intent.hasExtra(Insert.PHONE)) {
                mMode = MODE_QUERY_PICK_PHONE;
                mQueryMode = QUERY_MODE_TEL;
                mInitialFilter = intent.getStringExtra(Insert.PHONE);
            } else {
                mMode = MODE_QUERY_PICK;
                mQueryMode = QUERY_MODE_NONE;
                mShowSearchSnippets = true;
                mInitialFilter = intent.getStringExtra(SearchManager.QUERY);
            }
            mSearchResultsMode = true;
        // Since this is the filter activity it receives all intents
        // dispatched from the SearchManager for security reasons
        // so we need to re-dispatch from here to the intended target.
        } else if (Intents.SEARCH_SUGGESTION_CLICKED.equals(action)) {
            Uri data = intent.getData();
            Uri telUri = null;
            if (sContactsIdMatcher.match(data) == CONTACTS_ID) {
                long contactId = Long.valueOf(data.getLastPathSegment());
                final Cursor cursor = queryPhoneNumbers(contactId);
                if (cursor != null) {
                    if (cursor.getCount() == 1 && cursor.moveToFirst()) {
                        int phoneNumberIndex = cursor.getColumnIndex(Phone.NUMBER);
                        String phoneNumber = cursor.getString(phoneNumberIndex);
                        telUri = Uri.parse("tel:" + phoneNumber);
                    }
                    cursor.close();
                }
            }
            // See if the suggestion was clicked with a search action key (call button)
            Intent newIntent;
            if ("call".equals(intent.getStringExtra(SearchManager.ACTION_MSG)) && telUri != null) {
                newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, telUri);
            } else {
                newIntent = new Intent(Intent.ACTION_VIEW, data);
            }
            mRedirectIntent = newIntent;
            return;
        } else if (Intents.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED.equals(action)) {
            Intent newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, intent.getData());
            mRedirectIntent = newIntent;
            return;
        } else if (Intents.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED.equals(action)) {
            // TODO actually support this in EditContactActivity.
            String number = intent.getData().getSchemeSpecificPart();
            Intent newIntent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            newIntent.putExtra(Intents.Insert.PHONE, number);
            mRedirectIntent = newIntent;
            return;
        } else if (JoinContactActivity.JOIN_CONTACT.equals(action)) {
            mMode = MODE_PICK_CONTACT;
        } else if (Intents.ACTION_GET_MULTIPLE_PHONES.equals(action)) {
            if (mSearchMode) {
                mShowSearchSnippets = false;
            }
            if (Phone.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_MULTIPLE_PHONES;
            } else {
                // TODO support other content types
                Log.e(TAG, "Intent " + action + " is not supported for type " + type);
                mValid = false;
            }
        }
        if (mMode == MODE_UNKNOWN) {
            mMode = MODE_DEFAULT;
        }

        if (((mMode & MODE_MASK_SHOW_NUMBER_OF_CONTACTS) != 0 || mSearchMode)
                && !mSearchResultsMode) {
            mShowNumberOfContacts = true;
        }
    }

    public boolean isValid() {
        return mValid;
    }

    public Intent getRedirectIntent() {
        return mRedirectIntent;
    }

    public CharSequence getActivityTitle() {
        return mTitle;
    }

    private Cursor queryPhoneNumbers(long contactId) {
        Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        Cursor c = mContext.getContentResolver().query(dataUri,
                new String[] {Phone._ID, Phone.NUMBER, Phone.IS_SUPER_PRIMARY,
                RawContacts.ACCOUNT_TYPE, Phone.TYPE, Phone.LABEL},
                Data.MIMETYPE + "=?", new String[] {Phone.CONTENT_ITEM_TYPE}, null);
        if (c != null && c.moveToFirst()) {
            return c;
        }
        return null;
    }
}
