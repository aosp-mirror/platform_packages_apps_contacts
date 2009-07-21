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
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The base Activity class for viewing and editing a contact.
 */
public abstract class BaseContactCardActivity extends Activity
        implements QueryCompleteListener, OnTabSelectionChangedListener, View.OnClickListener {

    private static final String TAG = "BaseContactCardActivity";

    private SparseArray<Long> mTabRawContactIdMap;
    protected Uri mUri;
    protected long mContactId;
    protected ScrollingTabWidget mTabWidget;
    private NotifyingAsyncQueryHandler mHandler;
    private TextView mDisplayNameView;
    private TextView mPhoneticNameView;
    private CheckBox mStarredView;
    private ImageView mPhotoView;
    private TextView mStatusView;

    private int mNoPhotoResource;

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

    //Projection used for the summary info in the header.
    protected static final String[] HEADER_PROJECTION = new String[] {
        Contacts.DISPLAY_NAME,
        Contacts.STARRED,
        Contacts.PHOTO_ID,
    };
    protected static final int HEADER_DISPLAY_NAME_COLUMN_INDEX = 0;
    //TODO: We need to figure out how we're going to get the phonetic name.
    //static final int HEADER_PHONETIC_NAME_COLUMN_INDEX
    protected static final int HEADER_STARRED_COLUMN_INDEX = 1;
    protected static final int HEADER_PHOTO_ID_COLUMN_INDEX = 2;

    //Projection used for finding the most recent social status.
    protected static final String[] SOCIAL_PROJECTION = new String[] {
        Activities.TITLE,
        Activities.PUBLISHED,
    };
    protected static final int SOCIAL_TITLE_COLUMN_INDEX = 0;
    protected static final int SOCIAL_PUBLISHED_COLUMN_INDEX = 1;

    private static final int TOKEN_HEADER = 0;
    private static final int TOKEN_SOCIAL = 1;
    private static final int TOKEN_TABS = 2;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final Intent intent = getIntent();
        mUri = intent.getData();
        mContactId = ContentUris.parseId(mUri);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.contact_card_layout);

        mTabWidget = (ScrollingTabWidget) findViewById(R.id.tab_widget);
        mDisplayNameView = (TextView) findViewById(R.id.name);
        mPhoneticNameView = (TextView) findViewById(R.id.phonetic_name);
        mStarredView = (CheckBox) findViewById(R.id.star);
        mStarredView.setOnClickListener(this);
        mPhotoView = (ImageView) findViewById(R.id.photo);
        mStatusView = (TextView) findViewById(R.id.status);

        mTabWidget.setTabSelectionListener(this);
        mTabRawContactIdMap = new SparseArray<Long>();

        // Set the photo with a random "no contact" image
        long now = SystemClock.elapsedRealtime();
        int num = (int) now & 0xf;
        if (num < 9) {
            // Leaning in from right, common
            mNoPhotoResource = R.drawable.ic_contact_picture;
        } else if (num < 14) {
            // Leaning in from left uncommon
            mNoPhotoResource = R.drawable.ic_contact_picture_2;
        } else {
            // Coming in from the top, rare
            mNoPhotoResource = R.drawable.ic_contact_picture_3;
        }

        mHandler = new NotifyingAsyncQueryHandler(this, this);

        setupTabs();
        setupHeader();
    }

    private void setupHeader() {
        Uri headerUri = Uri.withAppendedPath(mUri, "data");

        Uri socialUri = ContentUris.withAppendedId(
                SocialContract.Activities.CONTENT_CONTACT_STATUS_URI, mContactId);

        mHandler.startQuery(TOKEN_HEADER, null, headerUri, HEADER_PROJECTION, null, null, null);
        mHandler.startQuery(TOKEN_SOCIAL, null, socialUri, SOCIAL_PROJECTION, null, null, null);
    }

    private void setupTabs() {
        Uri tabsUri = Uri.withAppendedPath(mUri, "raw_contacts");
        mHandler.startQuery(TOKEN_TABS, null, tabsUri, TAB_PROJECTION, null, null, null);
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
        if (cursor == null) {
            return;
        }
        try{
            if (token == TOKEN_HEADER) {
                bindHeader(cursor);
            } else if (token == TOKEN_SOCIAL) {
                bindSocial(cursor);
            } else if (token == TOKEN_TABS) {
                clearCurrentTabs();
                bindTabs(cursor);
            }
        } finally {
            cursor.close();
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

    //TODO: This will be part of the ContactHeaderWidget eventually.
    protected void bindHeader(Cursor c) {
        if (c == null) {
            return;
        }
        if (c.moveToFirst()) {
            //Set name
            String displayName = c.getString(HEADER_DISPLAY_NAME_COLUMN_INDEX);
            Log.i(TAG, displayName);
            mDisplayNameView.setText(displayName);
            //TODO: Bring back phonetic name
            /*if (mPhoneticNameView != null) {
                String phoneticName = c.getString(CONTACT_PHONETIC_NAME_COLUMN);
                mPhoneticNameView.setText(phoneticName);
            }*/

            //Set starred
            boolean starred = c.getInt(HEADER_STARRED_COLUMN_INDEX) == 1;
            mStarredView.setChecked(starred);

            //Set the photo
            long photoId = c.getLong(HEADER_PHOTO_ID_COLUMN_INDEX);
            Bitmap photoBitmap = ContactsUtils.loadContactPhoto(
                    this, photoId, null);
            if (photoBitmap == null) {
                photoBitmap = ContactsUtils.loadPlaceholderPhoto(mNoPhotoResource, this, null);
            }
            mPhotoView.setImageBitmap(photoBitmap);
        }
    }

    //TODO: This will be part of the ContactHeaderWidget eventually.
    protected void bindSocial(Cursor c) {
        if (c == null) {
            return;
        }
        if (c.moveToFirst()) {
            String status = c.getString(SOCIAL_TITLE_COLUMN_INDEX);
            mStatusView.setText(status);
        }
    }

    //TODO: This will be part of the ContactHeaderWidget eventually.
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.star: {
                Uri uri = Uri.withAppendedPath(mUri, "data");
                Cursor c = getContentResolver().query(uri, HEADER_PROJECTION, null, null, null);
                try {
                    c.moveToFirst();
                    int oldStarredState = c.getInt(HEADER_STARRED_COLUMN_INDEX);
                    ContentValues values = new ContentValues(1);
                    values.put(Contacts.STARRED, oldStarredState == 1 ? 0 : 1);
                    getContentResolver().update(mUri, values, null, null);
                } finally {
                    c.close();
                }
                setupHeader();
                break;
            }
        }
    }

    /**
     * Add a tab to be displayed in the {@link ScrollingTabWidget}.
     *
     * @param contactId The contact id associated with the tab.
     * @param label A label to display in the tab indicator.
     * @param icon An icon to display in the tab indicator.
     */
    protected void addTab(long contactId, String label, Drawable icon) {
        addTab(contactId, createTabIndicatorView(label, icon));
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
    protected View createTabIndicatorView(String label, Drawable icon) {
        View tabIndicator = mInflater.inflate(R.layout.tab_indicator, mTabWidget, false);

        final TextView tv = (TextView) tabIndicator.findViewById(R.id.tab_title);
        tv.setText(label);

        final ImageView iconView = (ImageView) tabIndicator.findViewById(R.id.tab_icon);
        iconView.setImageDrawable(icon);

        return tabIndicator;
    }

}
