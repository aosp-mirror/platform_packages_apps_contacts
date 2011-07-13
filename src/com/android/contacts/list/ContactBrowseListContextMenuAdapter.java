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

import com.android.contacts.R;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.contacts.widget.ContextMenuAdapter;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;

/**
 * A contextual menu adapter for the basic contact list.
 *
 * TODO Not used any more.  Remove it.
 */
public class ContactBrowseListContextMenuAdapter implements ContextMenuAdapter {

    private static final int MENU_ITEM_VIEW_CONTACT = 1;
    private static final int MENU_ITEM_CALL = 2;
    private static final int MENU_ITEM_SEND_SMS = 3;
    private static final int MENU_ITEM_EDIT = 4;
    private static final int MENU_ITEM_DELETE = 5;
    private static final int MENU_ITEM_TOGGLE_STAR = 6;

    private static final String TAG = "LightContactBrowserContextMenuAdapter";

    private final ContactBrowseListFragment mContactListFragment;

    public ContactBrowseListContextMenuAdapter(ContactBrowseListFragment fragment) {
        this.mContactListFragment = fragment;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.wtf(TAG, "Bad menuInfo", e);
            return;
        }

        ContactListAdapter adapter = mContactListFragment.getAdapter();
        int headerViewsCount = mContactListFragment.getListView().getHeaderViewsCount();
        int position = info.position - headerViewsCount;

        // Setup the menu header
        menu.setHeaderTitle(adapter.getContactDisplayName(position));

        // View contact details
        menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact);

        if (adapter.getHasPhoneNumber(position)) {
            final Context context = mContactListFragment.getContext();
            boolean hasPhoneApp = PhoneCapabilityTester.isPhone(context);
            boolean hasSmsApp = PhoneCapabilityTester.isSmsIntentRegistered(context);
            // Calling contact
            if (hasPhoneApp) menu.add(0, MENU_ITEM_CALL, 0, R.string.menu_call);
            // Send SMS item
            if (hasSmsApp) menu.add(0, MENU_ITEM_SEND_SMS, 0, R.string.menu_sendSMS);
        }

        // Star toggling
        if (!adapter.isContactStarred(position)) {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_addStar);
        } else {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_removeStar);
        }

        // Contact editing
        menu.add(0, MENU_ITEM_EDIT, 0, R.string.menu_editContact);
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.wtf(TAG, "Bad menuInfo", e);
            return false;
        }

        ContactListAdapter adapter = mContactListFragment.getAdapter();
        int headerViewsCount = mContactListFragment.getListView().getHeaderViewsCount();
        int position = info.position - headerViewsCount;

        final Uri contactUri = adapter.getContactUri(position);
        switch (item.getItemId()) {
            case MENU_ITEM_VIEW_CONTACT: {
                mContactListFragment.viewContact(contactUri);
                return true;
            }

            case MENU_ITEM_TOGGLE_STAR: {
                if (adapter.isContactStarred(position)) {
                    mContactListFragment.removeFromFavorites(contactUri);
                } else {
                    mContactListFragment.addToFavorites(contactUri);
                }
                return true;
            }

            case MENU_ITEM_CALL: {
                mContactListFragment.callContact(contactUri);
                return true;
            }

            case MENU_ITEM_SEND_SMS: {
                mContactListFragment.smsContact(contactUri);
                return true;
            }

            case MENU_ITEM_EDIT: {
                mContactListFragment.editContact(contactUri);
                return true;
            }

            case MENU_ITEM_DELETE: {
                mContactListFragment.deleteContact(contactUri);
                return true;
            }
        }

        return false;
    }
}
