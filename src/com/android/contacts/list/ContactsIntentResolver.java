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

    // Uri matcher for contact id
    private static final int CONTACTS_ID = 1001;
    private static final UriMatcher sContactsIdMatcher;
    static {
        sContactsIdMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sContactsIdMatcher.addURI(ContactsContract.AUTHORITY, "contacts/#", CONTACTS_ID);
    }

    private final Activity mContext;

    public ContactsIntentResolver(Activity context) {
        this.mContext = context;
    }

    public ContactsRequest resolveIntent(Intent intent) {
        ContactsRequest request = new ContactsRequest();
        request.setDisplayOnlyVisible(true);

        String action = intent.getAction();
        String component = intent.getComponent().getClassName();
        String type = intent.getType();

        Log.i(TAG, "Called with action: " + action);

        if (UI.LIST_DEFAULT.equals(action) ) {
            request.setActionCode(ContactsRequest.ACTION_DEFAULT);
            request.setDisplayWithPhonesOnlyOption(
                    ContactsRequest.DISPLAY_ONLY_WITH_PHONES_PREFERENCE);
        } else if (UI.LIST_ALL_CONTACTS_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_DEFAULT);
            request.setDisplayWithPhonesOnlyOption(
                    ContactsRequest.DISPLAY_ONLY_WITH_PHONES_DISABLED);
            request.setDisplayOnlyVisible(false);
        } else if (UI.LIST_CONTACTS_WITH_PHONES_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_DEFAULT);
            request.setDisplayWithPhonesOnlyOption(
                    ContactsRequest.DISPLAY_ONLY_WITH_PHONES_ENABLED);
        } else if (UI.LIST_STARRED_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_STARRED);
        } else if (UI.LIST_FREQUENT_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_FREQUENT);
        } else if (UI.LIST_STREQUENT_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_STREQUENT);
        } else if (UI.LIST_GROUP_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_GROUP);
            String groupName = intent.getStringExtra(UI.GROUP_NAME_EXTRA_KEY);
            if (TextUtils.isEmpty(groupName)) {
                Log.e(TAG, "Intent missing a required extra: " + UI.GROUP_NAME_EXTRA_KEY);
                request.setValid(false);
            } else {
                request.setGroupName(groupName);
            }
        } else if (Intent.ACTION_PICK.equals(action)) {
            final String resolvedType = intent.resolveType(mContext);
            if (Contacts.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_CONTACT);
            } else if (People.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_CONTACT);
                request.setLegacyCompatibilityMode(true);
            } else if (Phone.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
            } else if (Phones.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
                request.setLegacyCompatibilityMode(true);
            } else if (StructuredPostal.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
            } else if (ContactMethods.CONTENT_POSTAL_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
                request.setLegacyCompatibilityMode(true);
            }
        } else if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            if (component.equals("alias.DialShortcut")) {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_CALL);
                request.setActivityTitle(mContext.getString(R.string.callShortcutActivityTitle));
            } else if (component.equals("alias.MessageShortcut")) {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_SMS);
                request.setActivityTitle(mContext.getString(R.string.messageShortcutActivityTitle));
            } else {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT);
                request.setActivityTitle(mContext.getString(R.string.shortcutActivityTitle));
            }
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            if (Contacts.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT);
            } else if (Phone.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
            } else if (Phones.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
                request.setLegacyCompatibilityMode(true);
            } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
            } else if (ContactMethods.CONTENT_POSTAL_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
                request.setLegacyCompatibilityMode(true);
            }  else if (People.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT);
                request.setLegacyCompatibilityMode(true);
            }
        } else if (Intent.ACTION_INSERT_OR_EDIT.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT);
        } else if (Intent.ACTION_SEARCH.equals(action)) {
            // See if the suggestion was clicked with a search action key (call button)
            if ("call".equals(intent.getStringExtra(SearchManager.ACTION_MSG))) {
                String query = intent.getStringExtra(SearchManager.QUERY);
                if (!TextUtils.isEmpty(query)) {
                    Intent newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts("tel", query, null));
                    request.setRedirectIntent(newIntent);
                }
            } else {
                request.setQueryString(intent.getStringExtra(SearchManager.QUERY));
                request.setSearchResultsMode(true);
            }
        } else if (UI.FILTER_CONTACTS_ACTION.equals(action)) {
            // When we get a FILTER_CONTACTS_ACTION, it represents search in the context
            // of some other action. Let's retrieve the original action to provide proper
            // context for the search queries.
            request.setActionCode(ContactsRequest.ACTION_DEFAULT);
            Bundle extras = intent.getExtras();
            if (extras != null) {
                request.setQueryString(extras.getString(UI.FILTER_TEXT_EXTRA_KEY));

                ContactsRequest originalRequest =
                        (ContactsRequest)extras.get(ContactsSearchManager.ORIGINAL_REQUEST_KEY);
                if (originalRequest != null) {
                    request.copyFrom(originalRequest);
                }
            }

            if (request == null) {
                request = new ContactsRequest();
            }

            request.setSearchMode(true);
//        } else if (ACTION_SEARCH_INTERNAL.equals(action)) {
//            String originalAction = null;
//            Bundle extras = intent.getExtras();
//            if (extras != null) {
//                originalAction = extras.getString(ContactsSearchManager.ORIGINAL_ACTION_EXTRA_KEY);
//            }
//            mShortcutAction = intent.getStringExtra(SHORTCUT_ACTION_KEY);
//
//            if (Intent.ACTION_INSERT_OR_EDIT.equals(originalAction)) {
//                request.setActionCode(ContactsRequest.MODE_QUERY_PICK_TO_EDIT;
//                mShowSearchSnippets = true;
//                mQueryString = intent.getStringExtra(SearchManager.QUERY);
//            } else if (mShortcutAction != null && intent.hasExtra(Insert.PHONE)) {
//                request.setActionCode(ContactsRequest.MODE_QUERY_PICK_PHONE;
//                mQueryMode = QUERY_MODE_TEL;
//                mQueryString = intent.getStringExtra(Insert.PHONE);
//            } else {
//                request.setActionCode(ContactsRequest.MODE_QUERY_PICK;
//                mQueryMode = QUERY_MODE_NONE;
//                mShowSearchSnippets = true;
//                mQueryString = intent.getStringExtra(SearchManager.QUERY);
//            }
//            mSearchResultsMode = true;
        // Since this is the filter activity it receives all intents
        // dispatched from the SearchManager for security reasons
        // so we need to re-dispatch from here to the intended target.
        } else if (Intents.SEARCH_SUGGESTION_CLICKED.equals(action)) {
            // TODO show the disambig dialog instead of guessing the number
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
            request.setRedirectIntent(newIntent);
        } else if (Intents.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED.equals(action)) {
            Intent newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, intent.getData());
            request.setRedirectIntent(newIntent);
        } else if (Intents.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED.equals(action)) {
            // TODO actually support this in EditContactActivity.
            String number = intent.getData().getSchemeSpecificPart();
            Intent newIntent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            newIntent.putExtra(Intents.Insert.PHONE, number);
            request.setRedirectIntent(newIntent);
        }

        // Allow the title to be set to a custom String using an extra on the intent
        String title = intent.getStringExtra(UI.TITLE_EXTRA_KEY);
        if (title != null) {
            request.setActivityTitle(title);
        }

        return request;
    }

    // TODO replace with a disabmig dialog
    @Deprecated
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
