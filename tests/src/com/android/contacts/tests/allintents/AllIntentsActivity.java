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

import android.accounts.Account;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Intents.UI;
import android.provider.ContactsContract.RawContacts;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.contacts.tests.R;
import com.google.common.collect.Lists;

/**
 * An activity that provides access to various modes of the contacts application.
 * Useful for manual and scripted tests.
 * <p>
 * Note: this class cannot depend (directly on indirectly) on anything outside the test package.
 */
@SuppressWarnings("deprecation")
public class AllIntentsActivity extends ListActivity
        implements SelectAccountDialogFragment.Listener {

    /** The name of the package of the contacts application. */
    private static final String ANDROID_CONTACTS_PACKAGE = "com.android.contacts";

    private static final String CONTACT_LIST_ACTIVITY_CLASS_NAME =
            "com.android.contacts.activities.PeopleActivity";

    public enum ContactsIntent {
        LIST_DEFAULT,
        LIST_ALL_CONTACTS_ACTION,
        LIST_CONTACTS_WITH_PHONES_ACTION,
        LIST_STARRED_ACTION,
        LIST_FREQUENT_ACTION,
        LIST_STREQUENT_ACTION,
        LIST_GROUP_ACTION,
        VIEW_CONTACT_WITHOUT_ID,
        ACTION_PICK_CONTACT,
        ACTION_PICK_CONTACT_LEGACY,
        ACTION_PICK_PHONE,
        ACTION_PICK_PHONE_LEGACY,
        ACTION_PICK_POSTAL,
        ACTION_PICK_POSTAL_LEGACY,
        ACTION_PICK_EMAIL,
        ACTION_CREATE_SHORTCUT_CONTACT,
        ACTION_CREATE_SHORTCUT_DIAL,
        ACTION_CREATE_SHORTCUT_MESSAGE,
        ACTION_GET_CONTENT_CONTACT,
        ACTION_GET_CONTENT_CONTACT_LEGACY,
        ACTION_GET_CONTENT_PHONE,
        ACTION_GET_CONTENT_PHONE_LEGACY,
        ACTION_GET_CONTENT_POSTAL,
        ACTION_GET_CONTENT_POSTAL_LEGACY,
        ACTION_INSERT_OR_EDIT,
        ACTION_INSERT_OR_EDIT_PHONE_NUMBER,
        ACTION_INSERT_OR_EDIT_EMAIL_ADDRESS,
        ACTION_SEARCH_CALL,
        ACTION_SEARCH_CONTACT,
        ACTION_SEARCH_EMAIL,
        ACTION_SEARCH_PHONE,
        SEARCH_SUGGESTION_CLICKED_CONTACT,
        JOIN_CONTACT,
        EDIT_CONTACT,
        EDIT_CONTACT_LOOKUP,
        EDIT_CONTACT_LOOKUP_ID,
        EDIT_RAW_CONTACT,
        EDIT_LEGACY,
        EDIT_NEW_CONTACT,
        EDIT_NEW_CONTACT_WITH_DATA,
        EDIT_NEW_CONTACT_FOR_ACCOUNT,
        EDIT_NEW_CONTACT_FOR_ACCOUNT_WITH_DATA,
        EDIT_NEW_RAW_CONTACT,
        EDIT_NEW_LEGACY,
        VIEW_CONTACT,
        VIEW_CONTACT_LOOKUP,
        VIEW_CONTACT_LOOKUP_ID,
        VIEW_RAW_CONTACT,
        VIEW_LEGACY,
        DIAL,
        DIAL_phone,
        DIAL_person,
        DIAL_voicemail,
        CALL_BUTTON,
        DIAL_tel,
        VIEW_tel,
        VIEW_calllog,
        VIEW_calllog_entry,
        LEGACY_CALL_DETAILS_ACTIVITY,
        LEGACY_CALL_LOG_ACTIVITY;

        public static ContactsIntent get(int ordinal) {
            return values()[ordinal];
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setListAdapter(new ArrayAdapter<String>(this, R.layout.intent_list_item,
                getResources().getStringArray(R.array.allIntents)));
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        switch (ContactsIntent.get(position)) {
            case LIST_DEFAULT: {
                startContactListActivity(
                        new Intent(UI.LIST_DEFAULT, Contacts.CONTENT_URI));
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
            case LIST_FREQUENT_ACTION: {
                startContactListActivity(
                        new Intent(UI.LIST_FREQUENT_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_STREQUENT_ACTION: {
                startContactListActivity(
                        new Intent(UI.LIST_STREQUENT_ACTION, Contacts.CONTENT_URI));
                break;
            }
            case LIST_GROUP_ACTION: {
                startContactListActivity(
                        new Intent(UI.LIST_GROUP_ACTION, Contacts.CONTENT_URI));
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
            case ACTION_PICK_EMAIL: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, Email.CONTENT_URI));
                break;
            }
            case ACTION_CREATE_SHORTCUT_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_CREATE_SHORTCUT_DIAL: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                bindIntentToClass(intent, "alias.DialShortcut");
                startActivityForResult(intent, 0);
                break;
            }
            case ACTION_CREATE_SHORTCUT_MESSAGE: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                bindIntentToClass(intent, "alias.MessageShortcut");
                startActivityForResult(intent, 0);
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
            case ACTION_GET_CONTENT_PHONE: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Phone.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
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
            case ACTION_GET_CONTENT_POSTAL_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(ContactMethods.CONTENT_POSTAL_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_INSERT_OR_EDIT: {
                Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                putDataExtra(intent);
                startActivity(intent);
                break;
            }
            case ACTION_INSERT_OR_EDIT_PHONE_NUMBER: {
                Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                intent.putExtra(Insert.PHONE, "5123456789");
                startActivity(intent);
                break;
            }
            case ACTION_INSERT_OR_EDIT_EMAIL_ADDRESS: {
                Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                intent.putExtra(Insert.EMAIL, "android@android.com");
                startActivity(intent);
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
                intent.setType(Contacts.CONTENT_TYPE);
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
            case JOIN_CONTACT: {
                // TODO
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
            case EDIT_NEW_CONTACT_WITH_DATA: {
                Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);

                putDataExtra(intent);

                startActivity(intent);
                break;
            }
            case EDIT_NEW_CONTACT_FOR_ACCOUNT:
            case EDIT_NEW_CONTACT_FOR_ACCOUNT_WITH_DATA: {
                final SelectAccountDialogFragment dialog = new SelectAccountDialogFragment();
                dialog.setArguments(SelectAccountDialogFragment.createBundle(position));
                dialog.show(getFragmentManager(), SelectAccountDialogFragment.TAG);
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
            case VIEW_CONTACT: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                break;
            }
            case VIEW_CONTACT_WITHOUT_ID: {
                startActivity(new Intent(Intent.ACTION_VIEW, Contacts.CONTENT_URI));
                break;
            }
            case VIEW_CONTACT_LOOKUP: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), uri);
                final String lookupKey = lookupUri.getPathSegments().get(2);
                final Uri lookupWithoutIdUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI,
                        lookupKey);
                final Intent intent = new Intent(Intent.ACTION_VIEW, lookupWithoutIdUri);
                startActivity(intent);
                break;
            }
            case VIEW_CONTACT_LOOKUP_ID: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), uri);
                final Intent intent = new Intent(Intent.ACTION_VIEW, lookupUri);
                startActivity(intent);
                break;
            }
            case VIEW_RAW_CONTACT: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                final long rawContactId = findArbitraryRawContactOfContact(contactId);
                final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                break;
            }
            case VIEW_LEGACY: {
                final Uri legacyContentUri = Uri.parse("content://contacts/people");
                final long contactId = findArbitraryContactWithPhoneNumber();
                final long rawContactId = findArbitraryRawContactOfContact(contactId);
                final Uri uri = ContentUris.withAppendedId(legacyContentUri, rawContactId);
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                break;
            }
            case DIAL: {
                startActivity(new Intent(Intent.ACTION_DIAL));
                break;
            }
            case DIAL_phone: {
                // This is the legacy URI (there is no >2.0 way to call a phone data item)
                final long dataId = findArbitraryPhoneDataId();
                if (dataId != -1) {
                    final Uri legacyContentUri = Uri.parse("content://contacts/phones");
                    final Uri uri = ContentUris.withAppendedId(legacyContentUri, dataId);
                    startActivity(new Intent(Intent.ACTION_DIAL, uri));
                }
                break;
            }
            case DIAL_person: {
                // This is the legacy URI (there is no >2.0 way to call a person)
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final Uri legacyContentUri = Uri.parse("content://contacts/people");
                    final long rawContactId = findArbitraryRawContactOfContact(contactId);
                    final Uri uri = ContentUris.withAppendedId(legacyContentUri, rawContactId);
                    startActivity(new Intent(Intent.ACTION_DIAL, uri));
                }
                break;
            }
            case DIAL_voicemail: {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("voicemail:")));
                break;
            }
            case CALL_BUTTON: {
                startActivity(new Intent(Intent.ACTION_CALL_BUTTON));
                break;
            }
            case DIAL_tel: {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:555-123-4567")));
                break;
            }
            case VIEW_tel: {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("tel:555-123-4567")));
                break;
            }
            case VIEW_calllog: {
                final Intent intent = new Intent(Intent.ACTION_VIEW, null);
                intent.setType("vnd.android.cursor.dir/calls");
                startActivity(intent);
                break;
            }
            case VIEW_calllog_entry: {
                Uri uri = getCallLogUri();
                if (uri == null) {
                    Toast.makeText(this, "Call log is empty", Toast.LENGTH_LONG).show();
                    break;
                }
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(uri);
                startActivity(intent);
                break;
            }
            case LEGACY_CALL_DETAILS_ACTIVITY: {
                Uri uri = getCallLogUri();
                if (uri == null) {
                    Toast.makeText(this, "Call log is empty", Toast.LENGTH_LONG).show();
                    break;
                }
                final Intent intent = new Intent("android.intent.action.VIEW");
                intent.setData(uri);
                bindIntentToClass(intent, "com.android.dialer.CallDetailActivity");
                startActivity(intent);
                break;
            }
            case LEGACY_CALL_LOG_ACTIVITY: {
                startActivity(bindIntentToClass(new Intent(),
                        "com.android.contacts.activities.CallLogActivity"));
                break;
            }

            default: {
                Toast.makeText(this, "Sorry, we forgot to write this...", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Returns the URI of one of the items in the call log, or null if the call log is empty. */
    private Uri getCallLogUri() {
        Cursor cursor = getContentResolver().query(
                Calls.CONTENT_URI, new String[]{ Calls._ID }, null, null,
                Calls.DEFAULT_SORT_ORDER);
        if (!cursor.moveToNext()) {
            return null;
        }
        return ContentUris.withAppendedId(Calls.CONTENT_URI, cursor.getLong(0));
    }

    /** Creates an intent that is bound to a specific activity by name. */
    private Intent bindIntentToClass(Intent intent, String activityClassName) {
        intent.setComponent(new ComponentName(ANDROID_CONTACTS_PACKAGE, activityClassName));
        return intent;
    }

    private Intent buildFilterIntent(int actionCode, boolean legacy) {
        Intent intent = new Intent(UI.FILTER_CONTACTS_ACTION);
        intent.putExtra(UI.FILTER_TEXT_EXTRA_KEY, "A");
//        ContactsRequest request = new ContactsRequest();
//        request.setActionCode(actionCode);
//        intent.putExtra("originalRequest", request);
        return intent;
    }

    private void startContactListActivity(Intent intent) {
        bindIntentToClass(intent, CONTACT_LIST_ACTIVITY_CLASS_NAME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startContactSelectionActivityForResult(Intent intent) {
        startActivityForResult(intent, 12);
    }

    private void startSearchResultActivity(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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

    private long findArbitraryPhoneDataId() {
        final Cursor cursor = getContentResolver().query(Data.CONTENT_URI,
                new String[] { Data._ID },
                Data.MIMETYPE + "=" + Phone.MIMETYPE,
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

    @Override
    public void onAccountChosen(Account account, String dataSet, int tag) {
        switch (ContactsIntent.get(tag)) {
            case EDIT_NEW_CONTACT_FOR_ACCOUNT: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                intent.putExtra(Insert.ACCOUNT, account);
                intent.putExtra(Insert.DATA_SET, dataSet);
                startActivity(intent);
                break;
            }
            case EDIT_NEW_CONTACT_FOR_ACCOUNT_WITH_DATA: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);

                intent.putExtra(Insert.ACCOUNT, account);
                intent.putExtra(Insert.DATA_SET, dataSet);
                putDataExtra(intent);

                startActivity(intent);
                break;
            }
            default:
                break;
        }
    }

    public void putDataExtra(final Intent intent) {
        ContentValues row1 = new ContentValues();
        row1.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
        row1.put(Organization.COMPANY, "Android");

        ContentValues row2 = new ContentValues();
        row2.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        row2.put(Email.TYPE, Email.TYPE_CUSTOM);
        row2.put(Email.LABEL, "Green Bot");
        row2.put(Email.ADDRESS, "android@android.com");

        intent.putParcelableArrayListExtra(Insert.DATA, Lists.newArrayList(row1, row2));
    }
}
