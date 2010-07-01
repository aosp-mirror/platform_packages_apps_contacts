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

package com.android.contacts.tests.allintents;

import com.android.contacts.ContactsSearchManager;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.tests.R;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Intents.UI;
import android.provider.ContactsContract.RawContacts;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

/**
 * An activity that provides access to various modes of the contacts application.
 * Useful for manual and scripted tests.
 */
@SuppressWarnings("deprecation")
public class AllIntentsActivity extends ListActivity {

    private static final String ANDROID_CONTACTS_PACKAGE = "com.android.contacts";

    private static final String CONTACT_LIST_ACTIVITY_CLASS_NAME =
            "com.android.contacts.activities.ContactListActivity";
    private static final String SEARCH_RESULTS_ACTIVITY_CLASS_NAME =
            "com.android.contacts.SearchResultsActivity";
    private static final String MULTIPLE_PHONE_PICKER_ACTIVITY_CLASS_NAME =
            "com.android.contacts.MultiplePhonePickerActivity";

    private static final int LIST_DEFAULT = 0;
    private static final int LIST_ALL_CONTACTS_ACTION = 1;
    private static final int LIST_CONTACTS_WITH_PHONES_ACTION = 2;
    private static final int LIST_STARRED_ACTION = 3;
    private static final int LIST_STARRED_ACTION_WITH_FILTER = 4;
    private static final int LIST_FREQUENT_ACTION = 5;
    private static final int LIST_FREQUENT_ACTION_WITH_FILTER = 6;
    private static final int LIST_STREQUENT_ACTION = 7;
    private static final int LIST_STREQUENT_ACTION_WITH_FILTER = 8;
    private static final int ACTION_PICK_CONTACT = 9;
    private static final int ACTION_PICK_CONTACT_LEGACY = 10;
    private static final int ACTION_PICK_PHONE = 11;
    private static final int ACTION_PICK_PHONE_LEGACY = 12;
    private static final int ACTION_PICK_POSTAL = 13;
    private static final int ACTION_PICK_POSTAL_LEGACY = 14;
    private static final int ACTION_CREATE_SHORTCUT_CONTACT = 15;
    private static final int ACTION_CREATE_SHORTCUT_CONTACT_FILTER = 16;
    private static final int ACTION_CREATE_SHORTCUT_DIAL = 17;
    private static final int ACTION_CREATE_SHORTCUT_DIAL_FILTER = 18;
    private static final int ACTION_CREATE_SHORTCUT_MESSAGE = 19;
    private static final int ACTION_CREATE_SHORTCUT_MESSAGE_FILTER = 20;
    private static final int ACTION_GET_CONTENT_CONTACT = 21;
    private static final int ACTION_GET_CONTENT_CONTACT_FILTER = 22;
    private static final int ACTION_GET_CONTENT_CONTACT_LEGACY = 23;
    private static final int ACTION_GET_CONTENT_CONTACT_FILTER_LEGACY = 24;
    private static final int ACTION_GET_CONTENT_PHONE = 25;
    private static final int ACTION_GET_CONTENT_PHONE_FILTER = 26;
    private static final int ACTION_GET_CONTENT_PHONE_LEGACY = 27;
    private static final int ACTION_GET_CONTENT_POSTAL = 28;
    private static final int ACTION_GET_CONTENT_POSTAL_FILTER = 29;
    private static final int ACTION_GET_CONTENT_POSTAL_LEGACY = 30;
    private static final int ACTION_INSERT_OR_EDIT = 31;
    private static final int ACTION_SEARCH_CALL = 32;
    private static final int ACTION_SEARCH_CONTACT = 33;
    private static final int ACTION_SEARCH_EMAIL = 34;
    private static final int ACTION_SEARCH_PHONE = 35;
    private static final int SEARCH_SUGGESTION_CLICKED_CALL_BUTTON = 36;
    private static final int SEARCH_SUGGESTION_CLICKED_CONTACT = 37;
    private static final int SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED = 38;
    private static final int SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED = 39;
    private static final int JOIN_CONTACT = 40;
    private static final int ACTION_GET_MULTIPLE_PHONES = 41;

    private static final int EDIT_CONTACT = 42;
    private static final int EDIT_CONTACT_LOOKUP = 43;
    private static final int EDIT_CONTACT_LOOKUP_ID = 44;
    private static final int EDIT_RAW_CONTACT = 45;
    private static final int EDIT_LEGACY = 46;
    private static final int EDIT_NEW_CONTACT = 47;
    private static final int EDIT_NEW_RAW_CONTACT = 48;
    private static final int EDIT_NEW_LEGACY = 49;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setListAdapter(new ArrayAdapter<String>(this, R.layout.intent_list_item,
                getResources().getStringArray(R.array.allIntents)));
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        switch (position) {
            case LIST_DEFAULT: {
                startContactListActivity(
                        new Intent(Intent.ACTION_VIEW, Contacts.CONTENT_URI));
                break;
            }
            case LIST_ALL_CONTACTS_ACTION: {
                startContactListActivity(
                        new Intent(UI.LIST_ALL_CONTACTS_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_CONTACTS_WITH_PHONES_ACTION: {
                startContactListActivity(
                        new Intent(UI.LIST_CONTACTS_WITH_PHONES_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_STARRED_ACTION: {
                startContactListActivity(
                        new Intent(UI.LIST_STARRED_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_STARRED_ACTION_WITH_FILTER: {
                startContactListActivity(buildFilterIntent(ContactsRequest.ACTION_STARRED, false));
                break;
            }
            case LIST_FREQUENT_ACTION: {
                startContactListActivity(
                        new Intent(UI.LIST_FREQUENT_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_FREQUENT_ACTION_WITH_FILTER: {
                startContactListActivity(
                        buildFilterIntent(ContactsRequest.ACTION_FREQUENT, false));
                break;
            }
            case LIST_STREQUENT_ACTION: {
                startContactListActivity(
                        new Intent(UI.LIST_STREQUENT_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_STREQUENT_ACTION_WITH_FILTER: {
                startContactListActivity(
                        buildFilterIntent(ContactsRequest.ACTION_STREQUENT, false));
                break;
            }
            case ACTION_PICK_CONTACT: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI));
                break;
            }
            case ACTION_PICK_CONTACT_LEGACY: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, People.CONTENT_URI));
                break;
            }
            case ACTION_PICK_PHONE: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI));
                break;
            }
            case ACTION_PICK_PHONE_LEGACY: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, Phones.CONTENT_URI));
                break;
            }
            case ACTION_PICK_POSTAL: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, StructuredPostal.CONTENT_URI));
                break;
            }
            case ACTION_PICK_POSTAL_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(ContactMethods.CONTENT_POSTAL_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_CREATE_SHORTCUT_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_CREATE_SHORTCUT_CONTACT_FILTER: {
                startContactSelectionActivityForResult(
                        buildFilterIntent(ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT,
                                false));
                break;
            }
            case ACTION_CREATE_SHORTCUT_DIAL: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                intent.setComponent(
                        new ComponentName(ANDROID_CONTACTS_PACKAGE, "alias.DialShortcut"));
                startActivityForResult(intent, 0);
                break;
            }
            case ACTION_CREATE_SHORTCUT_DIAL_FILTER: {
                startContactSelectionActivityForResult(
                        buildFilterIntent(ContactsRequest.ACTION_CREATE_SHORTCUT_CALL,
                                false));
                break;
            }
            case ACTION_CREATE_SHORTCUT_MESSAGE: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                intent.setComponent(
                        new ComponentName(ANDROID_CONTACTS_PACKAGE, "alias.MessageShortcut"));
                startActivityForResult(intent, 0);
                break;
            }
            case ACTION_CREATE_SHORTCUT_MESSAGE_FILTER: {
                startContactSelectionActivityForResult(
                        buildFilterIntent(ContactsRequest.ACTION_CREATE_SHORTCUT_CALL, false));
                break;
            }
            case ACTION_GET_CONTENT_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_CONTACT_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(People.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_CONTACT_FILTER: {
                startContactSelectionActivityForResult(
                        buildFilterIntent(ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT, false));
                break;
            }
            case ACTION_GET_CONTENT_CONTACT_FILTER_LEGACY: {
                startContactSelectionActivityForResult(
                        buildFilterIntent(ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT,
                                true));
                break;
            }
            case ACTION_GET_CONTENT_PHONE: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Phone.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_PHONE_FILTER: {
                startContactSelectionActivityForResult(
                        buildFilterIntent(ContactsRequest.ACTION_PICK_PHONE, true));
                break;
            }
            case ACTION_GET_CONTENT_PHONE_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Phones.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_POSTAL: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(StructuredPostal.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_POSTAL_FILTER: {
                startContactSelectionActivityForResult(
                        buildFilterIntent(ContactsRequest.ACTION_PICK_POSTAL, false));
                break;
            }
            case ACTION_GET_CONTENT_POSTAL_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(ContactMethods.CONTENT_POSTAL_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_INSERT_OR_EDIT: {
                Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                startContactListActivity(intent);
                break;
            }
            case ACTION_SEARCH_CALL: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(SearchManager.ACTION_MSG, "call");
                intent.putExtra(SearchManager.QUERY, "800-4664-411");
                startSearchResultActivity(intent);
                break;
            }
            case ACTION_SEARCH_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(SearchManager.QUERY, "a");
                startSearchResultActivity(intent);
                break;
            }
            case ACTION_SEARCH_EMAIL: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(Insert.EMAIL, "a");
                startSearchResultActivity(intent);
                break;
            }
            case ACTION_SEARCH_PHONE: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(Insert.PHONE, "800");
                startSearchResultActivity(intent);
                break;
            }
            case SEARCH_SUGGESTION_CLICKED_CALL_BUTTON: {
                long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    Intent intent = new Intent(Intents.SEARCH_SUGGESTION_CLICKED);
                    intent.setData(contactUri);
                    intent.putExtra(SearchManager.ACTION_MSG, "call");
                    startContactListActivity(intent);
                }
                break;
            }
            case SEARCH_SUGGESTION_CLICKED_CONTACT: {
                long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    Intent intent = new Intent(Intents.SEARCH_SUGGESTION_CLICKED);
                    intent.setData(contactUri);
                    startContactListActivity(intent);
                }
                break;
            }
            case SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED: {
                Intent intent = new Intent(Intents.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED);
                intent.setData(Uri.parse("tel:800-4664411"));
                startContactListActivity(intent);
                break;
            }
            case SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED: {
                Intent intent = new Intent(Intents.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED);
                intent.setData(Uri.parse("tel:800-4664411"));
                startContactListActivity(intent);
                break;
            }
            case JOIN_CONTACT: {
                // TODO
                break;
            }
            case ACTION_GET_MULTIPLE_PHONES: {
                Intent intent = new Intent(Intents.ACTION_GET_MULTIPLE_PHONES);
                intent.setType(Phone.CONTENT_TYPE);
                intent.putExtra(Intents.EXTRA_PHONE_URIS, new Uri[] {
                        Uri.parse("tel:555-1212"), Uri.parse("tel:555-2121")
                });
                startMultiplePhoneSelectionActivityForResult(intent);
                break;
            }
            case EDIT_CONTACT: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                final Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                startActivity(intent);
                break;
            }
            case EDIT_CONTACT_LOOKUP: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), uri);
                final String lookupKey = lookupUri.getPathSegments().get(2);
                final Uri lookupWithoutIdUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI,
                        lookupKey);
                final Intent intent = new Intent(Intent.ACTION_EDIT, lookupWithoutIdUri);
                startActivity(intent);
                break;
            }
            case EDIT_CONTACT_LOOKUP_ID: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), uri);
                final Intent intent = new Intent(Intent.ACTION_EDIT, lookupUri);
                startActivity(intent);
                break;
            }
            case EDIT_RAW_CONTACT: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                final long rawContactId = findArbitraryRawContactOfContact(contactId);
                final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
                final Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                startActivity(intent);
                break;
            }
            case EDIT_LEGACY: {
                final Uri legacyContentUri = Uri.parse("content://contacts/people");
                final long contactId = findArbitraryContactWithPhoneNumber();
                final long rawContactId = findArbitraryRawContactOfContact(contactId);
                final Uri uri = ContentUris.withAppendedId(legacyContentUri, rawContactId);
                final Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                startActivity(intent);
                break;
            }
            case EDIT_NEW_CONTACT: {
                startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
                break;
            }
            case EDIT_NEW_RAW_CONTACT: {
                startActivity(new Intent(Intent.ACTION_INSERT, RawContacts.CONTENT_URI));
                break;
            }
            case EDIT_NEW_LEGACY: {
                final Uri legacyContentUri = Uri.parse("content://contacts/people");
                startActivity(new Intent(Intent.ACTION_INSERT, legacyContentUri));
                break;
            }
            default: {
                Toast.makeText(this, "Sorry, we forgot to write this...", Toast.LENGTH_LONG).show();
            }
        }
    }

    private Intent buildFilterIntent(int actionCode, boolean legacy) {
        Intent intent = new Intent(UI.FILTER_CONTACTS_ACTION);
        intent.putExtra(UI.FILTER_TEXT_EXTRA_KEY, "A");
        ContactsRequest request = new ContactsRequest();
        request.setActionCode(actionCode);
        intent.putExtra(ContactsSearchManager.ORIGINAL_REQUEST_KEY, request);
        return intent;
    }

    private void startContactListActivity(Intent intent) {
        intent.setComponent(
                new ComponentName(ANDROID_CONTACTS_PACKAGE, CONTACT_LIST_ACTIVITY_CLASS_NAME));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startContactSelectionActivityForResult(Intent intent) {
        startActivityForResult(intent, 12);
    }

    private void startSearchResultActivity(Intent intent) {
        intent.setComponent(
                new ComponentName(ANDROID_CONTACTS_PACKAGE, SEARCH_RESULTS_ACTIVITY_CLASS_NAME));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startMultiplePhoneSelectionActivityForResult(Intent intent) {
        intent.setComponent(
                new ComponentName(ANDROID_CONTACTS_PACKAGE,
                        MULTIPLE_PHONE_PICKER_ACTIVITY_CLASS_NAME));
        startActivityForResult(intent, 13);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("resultCode", resultCode);
        intent.putExtra("data", data);
        startActivity(intent);
    }

    private long findArbitraryContactWithPhoneNumber() {
        final Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI,
                new String[] { Contacts._ID },
                Contacts.HAS_PHONE_NUMBER + "!=0 AND " + Contacts.STARRED + "!=0" ,
                null, "RANDOM() LIMIT 1");
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }

        return -1;
    }

    private long findArbitraryRawContactOfContact(long contactId) {
        final Cursor cursor = getContentResolver().query(RawContacts.CONTENT_URI,
                new String[] { RawContacts._ID },
                RawContacts.CONTACT_ID + "=?",
                new String[] { String.valueOf(contactId) },
                RawContacts._ID + " LIMIT 1");
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }

        return -1;
    }
}
