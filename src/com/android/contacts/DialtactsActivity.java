/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.TabActivity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Contacts;
import android.provider.CallLog.Calls;
import android.provider.Contacts.Intents.UI;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.TabHost;
import com.android.internal.telephony.ITelephony;

/**
 * The dialer activity that has one tab with the virtual 12key dialer,
 * and another tab with recent calls in it. This is the container and the tabs
 * are embedded using intents.
 */
public class DialtactsActivity extends TabActivity {
    private static final String TAG = "Dailtacts";

    public static final String EXTRA_IGNORE_STATE = "ignore-state";

    private static final int FAVORITES_STARRED = 1;
    private static final int FAVORITES_FREQUENT = 2;
    private static final int FAVORITES_STREQUENT = 3;
    
    /** Defines what is displayed in the right tab */
    private static final int FAVORITES_TAB_MODE = FAVORITES_STREQUENT;

    protected TabHost mTabHost;
    
    private String mFilterText;
    
    private Uri mDialUri;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();
        fixIntent(intent);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialer_activity);

        mTabHost = getTabHost();

        // Setup the tabs
        setupDialerTab();
        setupCallLogTab();
        setupContactsTab();
        setupFavoritesTab();

        setCurrentTab(intent);
        
        if (intent.getAction().equals(Contacts.Intents.UI.FILTER_CONTACTS_ACTION) 
                && icicle == null) {
            setupFilterText(intent);
        }
    }

    private void fixIntent(Intent intent) {
        // This should be cleaned up: the call key used to send an Intent
        // that just said to go to the recent calls list.  It now sends this
        // abstract action, but this class hasn't been rewritten to deal with it.
        if (Intent.ACTION_CALL_BUTTON.equals(intent.getAction())) {
            intent.setDataAndType(Calls.CONTENT_URI, Calls.CONTENT_TYPE);
            intent.putExtra("call_key", true);
            setIntent(intent);
        }
    }
    
    private void setupCallLogTab() {
        mTabHost.addTab(mTabHost.newTabSpec("call_log")
                .setIndicator(getString(R.string.recentCallsIconLabel),
                        getResources().getDrawable(R.drawable.ic_tab_recent))
                .setContent(new Intent("com.android.phone.action.RECENT_CALLS")));
    }

    private void setupDialerTab() {
        mTabHost.addTab(mTabHost.newTabSpec("dialer")
                .setIndicator(getString(R.string.dialerIconLabel),
                        getResources().getDrawable(R.drawable.ic_tab_dialer))
                .setContent(new Intent("com.android.phone.action.TOUCH_DIALER")));
    }

    private void setupContactsTab() {
        mTabHost.addTab(mTabHost.newTabSpec("contacts")
                .setIndicator(getText(R.string.contactsIconLabel),
                        getResources().getDrawable(R.drawable.ic_tab_contacts))
                .setContent(new Intent(UI.LIST_DEFAULT)));
    }

    private void setupFavoritesTab() {
        Intent tab2Intent;
        switch (FAVORITES_TAB_MODE) {
            case FAVORITES_STARRED:
                tab2Intent = new Intent(UI.LIST_STARRED_ACTION);
                break;

            case FAVORITES_FREQUENT:
                tab2Intent = new Intent(UI.LIST_FREQUENT_ACTION);
                break;

            case FAVORITES_STREQUENT:
                tab2Intent = new Intent(UI.LIST_STREQUENT_ACTION);
                break;

            default:
                throw new UnsupportedOperationException("unknown default mode");
        }
        Drawable tab2Icon = getResources().getDrawable(R.drawable.ic_tab_starred);

        mTabHost.addTab(mTabHost.newTabSpec("favorites")
                .setIndicator(getString(R.string.contactsFavoritesLabel), tab2Icon)
                .setContent(tab2Intent));
    }

    /**
     * Returns true if the intent is due to hitting the green send key while in a call.
     * 
     * @param intent the intent that launched this activity
     * @param recentCallsRequest true if the intent is requesting to view recent calls
     * @return true if the intent is due to hitting the green send key while in a call 
     */
    private boolean isSendKeyWhileInCall(final Intent intent, final boolean recentCallsRequest) {
        // If there is a call in progress go to the call screen
        if (recentCallsRequest) {
            final boolean callKey = intent.getBooleanExtra("call_key", false);

            try {
                ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
                if (callKey && phone != null && phone.showCallScreen()) {
                    return true;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to handle send while in call", e);
            }
        }

        return false;
    }

    /**
     * Sets the current tab based on the intent's request type
     * 
     * @param recentCallsRequest true is the recent calls tab is desired, false oltherwise
     */
    private void setCurrentTab(Intent intent) {
        final boolean recentCallsRequest = Calls.CONTENT_TYPE.equals(intent.getType());
        if (isSendKeyWhileInCall(intent, recentCallsRequest)) {
            finish();
            return;
        }
        intent.putExtra(EXTRA_IGNORE_STATE, true);
        if (intent.getComponent().getClassName().equals(getClass().getName())) {
            if (recentCallsRequest) {
                mTabHost.setCurrentTab(1);
            } else {
                mTabHost.setCurrentTab(0);
            }
        } else {
            mTabHost.setCurrentTab(2);
        }
        intent.putExtra(EXTRA_IGNORE_STATE, false);
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        fixIntent(newIntent);
        setCurrentTab(newIntent);
        final String action = newIntent.getAction();
        if (action.equals(Contacts.Intents.UI.FILTER_CONTACTS_ACTION)) {
            setupFilterText(newIntent);
        } else if (isDialIntent(newIntent)) {
            setupDialUri(newIntent);
        }
    }
    
    private boolean isDialIntent(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action)) {
            return true;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            final Uri data = intent.getData();
            if (data != null && "tel".equals(data.getScheme())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Retrieves the filter text stored in {@link #setupFilterText(Intent)}.
     * This text originally came from a FILTER_CONTACTS_ACTION intent received
     * by this activity. The stored text will then be cleared after after this
     * method returns.
     * 
     * @return The stored filter text
     */
    public String getAndClearFilterText() {
        String filterText = mFilterText;
        mFilterText = null;
        return filterText;
    }

    /**
     * Stores the filter text associated with a FILTER_CONTACTS_ACTION intent.
     * This is so child activities can check if they are supposed to display a filter.
     * 
     * @param intent The intent received in {@link #onNewIntent(Intent)}
     */
    private void setupFilterText(Intent intent) {
        // If the intent was relaunched from history, don't apply the filter text.
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }
        String filter = intent.getStringExtra(Contacts.Intents.UI.FILTER_TEXT_EXTRA_KEY);
        if (filter != null && filter.length() > 0) {
            mFilterText = filter;
        }
    }

    /**
     * Retrieves the uri stored in {@link #setupDialUri(Intent)}. This uri
     * originally came from a dial intent received by this activity. The stored
     * uri will then be cleared after after this method returns.
     * 
     * @return The stored uri
     */
    public Uri getAndClearDialUri() {
        Uri dialUri = mDialUri;
        mDialUri = null;
        return dialUri;
    }

    /**
     * Stores the uri associated with a dial intent. This is so child activities can
     * check if they are supposed to display new dial info.
     * 
     * @param intent The intent received in {@link #onNewIntent(Intent)}
     */
    private void setupDialUri(Intent intent) {
        // If the intent was relaunched from history, don't reapply the intent.
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return;
        }
        mDialUri = intent.getData();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle BACK
        if (keyCode == KeyEvent.KEYCODE_BACK && isTaskRoot()) {
            // Instead of stopping, simply push this to the back of the stack.
            // This is only done when running at the top of the stack;
            // otherwise, we have been launched by someone else so need to
            // allow the user to go back to the caller.
            moveTaskToBack(false);
            return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }
}
