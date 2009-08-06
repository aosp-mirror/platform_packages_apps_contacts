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

import com.android.contacts.ScrollingTabWidget.OnTabSelectionChangedListener;
import com.android.contacts.NotifyingAsyncQueryHandler.QueryCompleteListener;
import com.android.internal.widget.ContactHeaderWidget;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.SocialContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.SocialContract.Activities;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The base Activity class for viewing and editing a contact.
 */
public abstract class BaseContactCardActivity extends Activity
        implements QueryCompleteListener, OnTabSelectionChangedListener {

    private static final String TAG = "BaseContactCardActivity";

    private SparseArray<Long> mTabRawContactIdMap;
    protected Uri mUri;
    protected ScrollingTabWidget mTabWidget;
    protected ContactHeaderWidget mContactHeaderWidget;
    private NotifyingAsyncQueryHandler mHandler;

    protected LayoutInflater mInflater;

    //Projection used for the query that determines which tabs to add.
    protected static final String[] TAB_PROJECTION = new String[] {
        RawContacts._ID,
        RawContacts.ACCOUNT_NAME,
        RawContacts.ACCOUNT_TYPE
    };
    protected static final int TAB_CONTACT_ID_COLUMN_INDEX = 0;
    protected static final int TAB_ACCOUNT_NAME_COLUMN_INDEX = 1;
    protected static final int TAB_ACCOUNT_TYPE_COLUMN_INDEX = 2;

    private static final int TOKEN_TABS = 0;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final Intent intent = getIntent();
        mUri = intent.getData();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.contact_card_layout);

        mContactHeaderWidget = (ContactHeaderWidget) findViewById(R.id.contact_header_widget);
        mContactHeaderWidget.showStar(true);
        mContactHeaderWidget.bindFromContactId(ContentUris.parseId(mUri));
        mTabWidget = (ScrollingTabWidget) findViewById(R.id.tab_widget);

        mTabWidget.setTabSelectionListener(this);
        mTabRawContactIdMap = new SparseArray<Long>();

        mHandler = new NotifyingAsyncQueryHandler(this, this);

        setupTabs();
    }

    private void setupTabs() {
        long contactId = ContentUris.parseId(mUri);
        mHandler.startQuery(TOKEN_TABS, null, RawContacts.CONTENT_URI, TAB_PROJECTION,
                RawContacts.CONTACT_ID + "=" + contactId, null, null);
    }

    /**
     * Return the contactId associated with the tab at an index.
     *
     * @param index The index of the tab in question.
     * @return The contactId associated with the tab at the specified index.
     */
    protected long getTabRawContactId(int index) {
        return mTabRawContactIdMap.get(index);
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        try{
            if (token == TOKEN_TABS) {
                clearCurrentTabs();
                bindTabs(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Adds a tab for each {@link RawContact} associated with this contact.
     * Override this method if you want to additional tabs and/or different
     * tabs for your activity.
     *
     * @param tabsCursor A cursor over all the RawContacts associated with
     * the contact being displayed. Use {@link TAB_CONTACT_ID_COLUMN_INDEX},
     * {@link TAB_ACCOUNT_NAME_COLUMN_INDEX}, {@link TAB_ACCOUNT_TYPE_COLUMN_INDEX},
     * and {@link TAB_PACKAGE_COLUMN_INDEX} as column indexes on the cursor.
     */
    protected void bindTabs(Cursor tabsCursor) {
        while (tabsCursor.moveToNext()) {
            long contactId = tabsCursor.getLong(TAB_CONTACT_ID_COLUMN_INDEX);

            //TODO: figure out how to get the icon
            Drawable tabIcon = null;
            addTab(contactId, null, tabIcon);
        }
        selectDefaultTab();

    }

    /**
     * Add a tab to be displayed in the {@link ScrollingTabWidget}.
     *
     * @param contactId The contact id associated with the tab.
     * @param label A label to display in the tab indicator.
     * @param icon An icon to display in the tab indicator.
     */
    protected void addTab(long contactId, String label, Drawable icon) {
        addTab(contactId, createTabIndicatorView(mTabWidget, label, icon));
    }

    /**
     * Add a tab to be displayed in the {@link ScrollingTabWidget}.
     *
     * @param contactId The contact id associated with the tab.
     * @param view A view to use as the tab indicator.
     */
    protected void addTab(long contactId, View view) {
        mTabRawContactIdMap.put(mTabWidget.getTabCount(), contactId);
        mTabWidget.addTab(view);
    }


    protected void clearCurrentTabs() {
        mTabRawContactIdMap.clear();
        mTabWidget.removeAllTabs();
    }

    /**
     * Makes the default tab selection. This is called after the tabs have been
     * bound for the first time, and whenever a new intent is received. Override
     * this method if you want to customize the default tab behavior.
     */
    protected void selectDefaultTab() {
        // Select the first tab.
        mTabWidget.setCurrentTab(0);
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        setIntent(newIntent);
        selectDefaultTab();
        mUri = newIntent.getData();
    }

    /**
     * Utility for creating a standard tab indicator view.
     *
     * @param label The label to display in the tab indicator. If null, not label will be displayed.
     * @param icon The icon to display. If null, no icon will be displayed.
     * @return The tab indicator View.
     */
    public static View createTabIndicatorView(ViewGroup parent, CharSequence label, Drawable icon) {
        final LayoutInflater inflater = (LayoutInflater)parent.getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final View tabIndicator = inflater.inflate(R.layout.tab_indicator, parent, false);

        final TextView tv = (TextView) tabIndicator.findViewById(R.id.tab_title);
        tv.setText(label);

        final ImageView iconView = (ImageView) tabIndicator.findViewById(R.id.tab_icon);
        iconView.setImageDrawable(icon);

        return tabIndicator;
    }

}
