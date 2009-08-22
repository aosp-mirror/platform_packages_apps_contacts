/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.util.ArrayList;

import com.android.contacts.ScrollingTabWidget.OnTabSelectionChangedListener;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.util.NotifyingAsyncQueryHandler;
import com.android.contacts.model.Sources;
import com.android.internal.widget.ContactHeaderWidget;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The base Activity class for viewing and editing a contact.
 */
public abstract class BaseContactCardActivity extends Activity implements
        NotifyingAsyncQueryHandler.AsyncQueryListener, OnTabSelectionChangedListener {

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

    protected static final String SELECTED_RAW_CONTACT_ID_KEY = "selectedRawContact";
    protected long mSelectedRawContactId;

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
        mTabWidget.setVisibility(View.INVISIBLE);
        mTabRawContactIdMap = new SparseArray<Long>();

        mHandler = new NotifyingAsyncQueryHandler(this, this);

        // TODO: turn this into async call instead of blocking ui
        asyncSetupTabs();
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mSelectedRawContactId = savedInstanceState.getLong(SELECTED_RAW_CONTACT_ID_KEY);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(SELECTED_RAW_CONTACT_ID_KEY, mSelectedRawContactId);
    }

    private void asyncSetupTabs() {
        long contactId = ContentUris.parseId(mUri);
        mHandler.startQueryEntities(TOKEN_TABS, null,
                RawContacts.CONTENT_URI, RawContacts.CONTACT_ID + "=" + contactId, null, null);
    }

    /**
     * Return the RawContact id associated with the tab at an index.
     *
     * @param index The index of the tab in question.
     * @return The contactId associated with the tab at the specified index.
     */
    protected long getTabRawContactId(int index) {
        return mTabRawContactIdMap.get(index);
    }

    /**
     * Return the tab index associated with the RawContact id.
     *
     * @param index The index of the tab in question.
     * @return The contactId associated with the tab at the specified index.
     */
    protected int getTabIndexForRawContactId(long rawContactId) {
        int numTabs = mTabRawContactIdMap.size();
        for (int i=0; i < numTabs; i++) {
            if (mTabRawContactIdMap.get(i) == rawContactId) {
                return i;
            }
        }
        return -1;
    }

    /** {@inheritDoc} */
    public void onQueryEntitiesComplete(int token, Object cookie, EntityIterator iterator) {
        try{
            if (token == TOKEN_TABS) {
                clearCurrentTabs();
                bindTabs(readEntities(iterator));
            }
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        // Emtpy
    }

    private ArrayList<Entity> readEntities(EntityIterator iterator) {
        ArrayList<Entity> entities = new ArrayList<Entity>();
        try {
            while (iterator.hasNext()) {
                entities.add(iterator.next());
            }
        } catch (RemoteException e) {
        }

        return entities;
    }

    /**
     * Adds a tab for each {@link RawContact} associated with this contact.
     * Override this method if you want to additional tabs and/or different
     * tabs for your activity.
     *
     * @param entities An {@link ArrayList} of {@link Entity}s of all the RawContacts
     * associated with the contact being displayed.
     */
    protected void bindTabs(ArrayList<Entity> entities) {
        final Sources sources = Sources.getInstance(this);

        for (Entity entity : entities) {
            final String accountType = entity.getEntityValues().
                    getAsString(RawContacts.ACCOUNT_TYPE);
            final Long rawContactId = entity.getEntityValues().
                    getAsLong(RawContacts._ID);

            // TODO: ensure inflation on background task so we don't block UI thread here
            final ContactsSource source = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_SUMMARY);
            addTab(rawContactId, createTabIndicatorView(mTabWidget, source));
        }

        selectInitialTab();
        mTabWidget.setVisibility(View.VISIBLE);
        mTabWidget.postInvalidate();
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
    protected void addTab(long rawContactId, View view) {
        mTabRawContactIdMap.put(mTabWidget.getTabCount(), rawContactId);
        mTabWidget.addTab(view);
    }


    protected void clearCurrentTabs() {
        mTabRawContactIdMap.clear();
        mTabWidget.removeAllTabs();
    }

    protected void selectInitialTab() {
        int selectedTabIndex = -1;
        if (mSelectedRawContactId > 0) {
            selectedTabIndex = getTabIndexForRawContactId(mSelectedRawContactId);
        }
        if (selectedTabIndex >= 0) {
            mTabWidget.setCurrentTab(selectedTabIndex);
        } else {
            selectDefaultTab();
        }
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
     * @param parent The parent ViewGroup to attach the new view to.
     * @param label The label to display in the tab indicator. If null, not label will be displayed.
     * @param icon The icon to display. If null, no icon will be displayed.
     * @return The tab indicator View.
     */
    public static View createTabIndicatorView(ViewGroup parent, CharSequence label, Drawable icon) {
        final LayoutInflater inflater = (LayoutInflater)parent.getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final View tabIndicator = inflater.inflate(R.layout.tab_indicator, parent, false);
        tabIndicator.getBackground().setDither(true);

        final TextView tv = (TextView) tabIndicator.findViewById(R.id.tab_title);
        tv.setText(label);

        final ImageView iconView = (ImageView) tabIndicator.findViewById(R.id.tab_icon);
        iconView.setImageDrawable(icon);

        return tabIndicator;
    }

    /**
     * Utility for creating a standard tab indicator view.
     *
     * @param context The label to display in the tab indicator. If null, not label will be displayed.
     * @param parent The parent ViewGroup to attach the new view to.
     * @param source The {@link ContactsSource} to build the tab view from.
     * @return The tab indicator View.
     */
    public static View createTabIndicatorView(ViewGroup parent, ContactsSource source) {
        Drawable icon = null;
        if (source != null) {
            final String packageName = source.resPackageName;
            if (source.iconRes > 0) {
                try {
                    final Context authContext = parent.getContext().
                            createPackageContext(packageName, 0);
                    icon = authContext.getResources().getDrawable(source.iconRes);

                } catch (PackageManager.NameNotFoundException e) {
                    Log.d(TAG, "error getting the Package Context for " + packageName, e);
                }
            }
        }
        return createTabIndicatorView(parent, null, icon);
    }
}
