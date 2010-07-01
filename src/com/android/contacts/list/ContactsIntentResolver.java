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

import com.android.contacts.CallContactActivity;
import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Intents.UI;
import android.text.TextUtils;
import android.util.Log;

/**
 * Parses a Contacts intent, extracting all relevant parts and packaging them
 * as a {@link ContactsRequest} object.
 */
@SuppressWarnings("deprecation")
public class ContactsIntentResolver {

    private static final String TAG = "ContactsIntentResolver";

    private final Activity mContext;

    public ContactsIntentResolver(Activity context) {
        this.mContext = context;
    }

    public ContactsRequest resolveIntent(Intent intent) {
        ContactsRequest request = new ContactsRequest();
        request.setDisplayOnlyVisible(true);

        String action = intent.getAction();

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
            if (!TextUtils.isEmpty(groupName)) {
                request.setGroupName(groupName);
            } else {
                Log.e(TAG, "Intent missing a required extra: " + UI.GROUP_NAME_EXTRA_KEY);
                request.setValid(false);
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
            String component = intent.getComponent().getClassName();
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
            String type = intent.getType();
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
                request.setSearchMode(true);
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

        // Since this is the filter activity it receives all intents
        // dispatched from the SearchManager for security reasons
        // so we need to re-dispatch from here to the intended target.
        } else if (Intents.SEARCH_SUGGESTION_CLICKED.equals(action)) {
            Uri data = intent.getData();
            // See if the suggestion was clicked with a search action key (call button)
            if ("call".equals(intent.getStringExtra(SearchManager.ACTION_MSG))) {
                Intent newIntent = new Intent(mContext, CallContactActivity.class);
                newIntent.setData(data);
                request.setRedirectIntent(newIntent);
            } else {
                request.setRedirectIntent(new Intent(Intent.ACTION_VIEW, data));
            }
        } else if (Intents.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED.equals(action)) {
            request.setRedirectIntent(new Intent(Intent.ACTION_CALL_PRIVILEGED, intent.getData()));
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
}
