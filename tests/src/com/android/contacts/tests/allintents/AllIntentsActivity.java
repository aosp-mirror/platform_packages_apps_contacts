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
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Intents.UI;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * An activity that provides access to various modes of the contacts application.
 * Useful for manual and scripted tests.
 */
@SuppressWarnings("deprecation")
public class AllIntentsActivity extends ListActivity {

    private static final String ANDROID_CONTACTS_PACKAGE = "com.android.contacts";

    private static final String CONTACTS_LIST_ACTIVITY_CLASS_NAME =
            "com.android.contacts.ContactsListActivity";
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
                startContactsListActivity(
                        new Intent(Intent.ACTION_VIEW, Contacts.CONTENT_URI));
                break;
            }
            case LIST_ALL_CONTACTS_ACTION: {
                startContactsListActivity(
                        new Intent(UI.LIST_ALL_CONTACTS_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_CONTACTS_WITH_PHONES_ACTION: {
                startContactsListActivity(
                        new Intent(UI.LIST_CONTACTS_WITH_PHONES_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_STARRED_ACTION: {
                startContactsListActivity(
                        new Intent(UI.LIST_STARRED_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_STARRED_ACTION_WITH_FILTER: {
                startContactsListActivity(
                        buildFilterIntent(UI.LIST_STARRED_ACTION, null, null));
                break;
            }
            case LIST_FREQUENT_ACTION: {
                startContactsListActivity(
                        new Intent(UI.LIST_FREQUENT_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_FREQUENT_ACTION_WITH_FILTER: {
                startContactsListActivity(
                        buildFilterIntent(UI.LIST_FREQUENT_ACTION, null, null));
                break;
            }
            case LIST_STREQUENT_ACTION: {
                startContactsListActivity(
                        new Intent(UI.LIST_STREQUENT_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_STREQUENT_ACTION_WITH_FILTER: {
                startContactsListActivity(
                        buildFilterIntent(UI.LIST_STREQUENT_ACTION, null, null));
                break;
            }
            case ACTION_PICK_CONTACT: {
                startContactsListActivityForResult(
                        new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI));
                break;
            }
            case ACTION_PICK_CONTACT_LEGACY: {
                startContactsListActivityForResult(
                        new Intent(Intent.ACTION_PICK, People.CONTENT_URI));
                break;
            }
            case ACTION_PICK_PHONE: {
                startContactsListActivityForResult(
                        new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI));
                break;
            }
            case ACTION_PICK_PHONE_LEGACY: {
                startContactsListActivityForResult(
                        new Intent(Intent.ACTION_PICK, Phones.CONTENT_URI));
                break;
            }
            case ACTION_PICK_POSTAL: {
                startContactsListActivityForResult(
                        new Intent(Intent.ACTION_PICK, StructuredPostal.CONTENT_URI));
                break;
            }
            case ACTION_PICK_POSTAL_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(ContactMethods.CONTENT_POSTAL_TYPE);
                startContactsListActivityForResult(intent);
                break;
            }
            case ACTION_CREATE_SHORTCUT_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                startContactsListActivityForResult(intent);
                break;
            }
            case ACTION_CREATE_SHORTCUT_CONTACT_FILTER: {
                startContactsListActivityForResult(
                        buildFilterIntent(Intent.ACTION_CREATE_SHORTCUT,
                                CONTACTS_LIST_ACTIVITY_CLASS_NAME, null));
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
                startContactsListActivityForResult(
                        buildFilterIntent(Intent.ACTION_CREATE_SHORTCUT,
                                "alias.DialShortcut", null));
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
                startContactsListActivityForResult(
                        buildFilterIntent(Intent.ACTION_CREATE_SHORTCUT,
                                "alias.MessageShortcut", null));
                break;
            }
            case ACTION_GET_CONTENT_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                startContactsListActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_CONTACT_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(People.CONTENT_ITEM_TYPE);
                startContactsListActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_CONTACT_FILTER: {
                startContactsListActivityForResult(
                        buildFilterIntent(Intent.ACTION_GET_CONTENT,
                                CONTACTS_LIST_ACTIVITY_CLASS_NAME,
                                Contacts.CONTENT_ITEM_TYPE));
                break;
            }
            case ACTION_GET_CONTENT_CONTACT_FILTER_LEGACY: {
                startContactsListActivityForResult(
                        buildFilterIntent(Intent.ACTION_GET_CONTENT,
                                CONTACTS_LIST_ACTIVITY_CLASS_NAME,
                                People.CONTENT_ITEM_TYPE));
                break;
            }
            case ACTION_GET_CONTENT_PHONE: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Phone.CONTENT_ITEM_TYPE);
                startContactsListActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_PHONE_FILTER: {
                startContactsListActivityForResult(
                        buildFilterIntent(Intent.ACTION_GET_CONTENT,
                                CONTACTS_LIST_ACTIVITY_CLASS_NAME,
                                Phone.CONTENT_ITEM_TYPE));
                break;
            }
            case ACTION_GET_CONTENT_PHONE_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Phones.CONTENT_ITEM_TYPE);
                startContactsListActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_POSTAL: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(StructuredPostal.CONTENT_ITEM_TYPE);
                startContactsListActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_POSTAL_FILTER: {
                startContactsListActivityForResult(
                        buildFilterIntent(Intent.ACTION_GET_CONTENT,
                                CONTACTS_LIST_ACTIVITY_CLASS_NAME,
                                StructuredPostal.CONTENT_ITEM_TYPE));
                break;
            }
            case ACTION_GET_CONTENT_POSTAL_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(ContactMethods.CONTENT_POSTAL_ITEM_TYPE);
                startContactsListActivityForResult(intent);
                break;
            }
            case ACTION_INSERT_OR_EDIT: {
                Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                startContactsListActivity(intent);
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
                    startContactsListActivity(intent);
                }
                break;
            }
            case SEARCH_SUGGESTION_CLICKED_CONTACT: {
                long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    Intent intent = new Intent(Intents.SEARCH_SUGGESTION_CLICKED);
                    intent.setData(contactUri);
                    startContactsListActivity(intent);
                }
                break;
            }
            case SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED: {
                Intent intent = new Intent(Intents.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED);
                intent.setData(Uri.parse("tel:800-4664411"));
                startContactsListActivity(intent);
                break;
            }
            case SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED: {
                Intent intent = new Intent(Intents.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED);
                intent.setData(Uri.parse("tel:800-4664411"));
                startContactsListActivity(intent);
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
        }
    }

    private Intent buildFilterIntent(String action, String component, String type) {
        Intent intent = new Intent(UI.FILTER_CONTACTS_ACTION);
        intent.putExtra(UI.FILTER_TEXT_EXTRA_KEY, "A");
        intent.putExtra(ContactsSearchManager.ORIGINAL_ACTION_EXTRA_KEY, action);
        if (component != null) {
            intent.putExtra(ContactsSearchManager.ORIGINAL_COMPONENT_EXTRA_KEY, component);
        }
        if (type != null) {
            intent.putExtra(ContactsSearchManager.ORIGINAL_TYPE_EXTRA_KEY, type);
        }
        return intent;
    }

    private void startContactsListActivity(Intent intent) {
        intent.setComponent(
                new ComponentName(ANDROID_CONTACTS_PACKAGE, CONTACTS_LIST_ACTIVITY_CLASS_NAME));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startContactsListActivityForResult(Intent intent) {
        intent.setComponent(
                new ComponentName(ANDROID_CONTACTS_PACKAGE, CONTACTS_LIST_ACTIVITY_CLASS_NAME));
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
        Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI,
                new String[]{Contacts._ID},
                Contacts.HAS_PHONE_NUMBER + "!=0", null, Contacts._ID + " LIMIT 1");
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
