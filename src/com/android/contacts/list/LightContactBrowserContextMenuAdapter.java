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
import com.android.contacts.widget.ContextMenuAdapter;

import android.net.Uri;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;

/**
 * A contextual menu adapter for the light version of the contact browser.
 */
public class LightContactBrowserContextMenuAdapter implements ContextMenuAdapter {

    private static final int MENU_ITEM_VIEW_CONTACT = 1;
    private static final int MENU_ITEM_CALL = 2;
    private static final int MENU_ITEM_SEND_SMS = 3;
    private static final int MENU_ITEM_EDIT = 4;
    private static final int MENU_ITEM_DELETE = 5;
    private static final int MENU_ITEM_TOGGLE_STAR = 6;

    private static final String TAG = "LightContactBrowserContextMenuAdapter";

    private final LightContactBrowser mBrowser;

    public LightContactBrowserContextMenuAdapter(LightContactBrowser browser) {
        this.mBrowser = browser;
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.wtf(TAG, "Bad menuInfo", e);
            return;
        }

        ContactEntryListAdapter adapter = mBrowser.getAdapter();
        adapter.moveToPosition(info.position);

        // Setup the menu header
        menu.setHeaderTitle(adapter.getContactDisplayName());

        // View contact details
        menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact);

        if (adapter.getHasPhoneNumber()) {
            // Calling contact
            menu.add(0, MENU_ITEM_CALL, 0, R.string.menu_call);
            // Send SMS item
            menu.add(0, MENU_ITEM_SEND_SMS, 0, R.string.menu_sendSMS);
        }

        // Star toggling
        if (!adapter.isContactStarred()) {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_addStar);
        } else {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_removeStar);
        }

        // Contact editing
        menu.add(0, MENU_ITEM_EDIT, 0, R.string.menu_editContact);
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact);
    }

    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.wtf(TAG, "Bad menuInfo", e);
            return false;
        }

        ContactEntryListAdapter adapter = mBrowser.getAdapter();
        adapter.moveToPosition(info.position);
        final Uri contactUri = adapter.getContactUri();

        switch (item.getItemId()) {
            case MENU_ITEM_VIEW_CONTACT: {
                mBrowser.viewContact(contactUri);
                return true;
            }

            case MENU_ITEM_TOGGLE_STAR: {
                if (adapter.isContactStarred()) {
                    mBrowser.removeFromFavorites(contactUri);
                } else {
                    mBrowser.addToFavorites(contactUri);
                }
                return true;
            }

            case MENU_ITEM_CALL: {
                mBrowser.callContact(contactUri);
                return true;
            }

            case MENU_ITEM_SEND_SMS: {
                mBrowser.smsContact(contactUri);
                return true;
            }

            case MENU_ITEM_EDIT: {
                mBrowser.editContact(contactUri);
                return true;
            }

            case MENU_ITEM_DELETE: {
                mBrowser.deleteContact(contactUri);
                return true;
            }
        }

        return false;
    }
}
