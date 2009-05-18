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

import com.android.contacts.NotifyingAsyncQueryHandler.QueryCompleteListener;
import com.android.contacts.SocialStreamActivity.MappingCache;
import com.android.providers.contacts2.ContactsContract;
import com.android.providers.contacts2.ContactsContract.Aggregates;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Contacts;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.ContactMethodsColumns;
import android.provider.Contacts.Intents;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * Handle several edge cases around showing or possibly creating contacts in
 * connected with a specific E-mail address or phone number. Will search based
 * on incoming {@link Intent#getData()} as described by
 * {@link Intents#SHOW_OR_CREATE_CONTACT}.
 *
 * <ul>
 * <li>If no matching contacts found, will prompt user with dialog to add to a
 * contact, then will use {@link Intent#ACTION_INSERT_OR_EDIT} to let create new
 * contact or edit new data into an existing one.
 * <li>If one matching contact found, directly show {@link Intent#ACTION_VIEW}
 * that specific contact.
 * <li>If more than one matching found, show list of matching contacts using
 * {@link Intent#ACTION_SEARCH}.
 * </ul>
 */
public final class ShowOrCreateActivity extends Activity implements QueryCompleteListener {
    static final String TAG = "ShowOrCreateActivity";
    static final boolean LOGD = false;

    static final String[] PHONES_PROJECTION = new String[] {
        Phones.PERSON_ID,
    };

    static final String[] CONTACTS_PROJECTION = new String[] {
        ContactsContract.Contacts.AGGREGATE_ID,
//        People._ID,
    };
    
    static final String SCHEME_MAILTO = "mailto";
    static final String SCHEME_TEL = "tel";
    
    static final int AGGREGATE_ID_INDEX = 0;

    /**
     * Query clause to filter {@link ContactMethods#CONTENT_URI} to only search
     * {@link Contacts#KIND_EMAIL} or {@link Contacts#KIND_IM}.
     */
    static final String QUERY_KIND_EMAIL_OR_IM = ContactMethodsColumns.KIND +
            " IN (" + Contacts.KIND_EMAIL + "," + Contacts.KIND_IM + ")";
    
    /**
     * Extra used to request a specific {@link FastTrackWindow} position.
     */
    private static final String EXTRA_Y = "pixel_y";
    private static final int DEFAULT_Y = 90;

    static final int QUERY_TOKEN = 42;
    
    private NotifyingAsyncQueryHandler mQueryHandler;

    private Bundle mCreateExtras;
    private String mCreateDescrip;
    private boolean mCreateForce;

    private FastTrackWindow mFastTrack;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Throw an empty layout up so we have a window token later
        setContentView(R.layout.empty);

        // Create handler if doesn't exist, otherwise cancel any running
        if (mQueryHandler == null) {
            mQueryHandler = new NotifyingAsyncQueryHandler(this, this);
        } else {
            mQueryHandler.cancelOperation(QUERY_TOKEN);
        }

        final Intent intent = getIntent();
        final Uri data = intent.getData();

        // Unpack scheme and target data from intent
        String scheme = null;
        String ssp = null;
        if (data != null) {
            scheme = data.getScheme();
            ssp = data.getSchemeSpecificPart();
        }

        // Build set of extras for possible use when creating contact
        mCreateExtras = new Bundle();
        Bundle originalExtras = intent.getExtras();
        if (originalExtras != null) {
            mCreateExtras.putAll(originalExtras);
        }

        // Read possible extra with specific title
        String mCreateDescrip = intent.getStringExtra(Intents.EXTRA_CREATE_DESCRIPTION);
        if (mCreateDescrip == null) {
            mCreateDescrip = ssp;
        }

        // Allow caller to bypass dialog prompt
        mCreateForce = intent.getBooleanExtra(Intents.EXTRA_FORCE_CREATE, false);

        // Handle specific query request
        if (SCHEME_MAILTO.equals(scheme)) {
            mCreateExtras.putString(Intents.Insert.EMAIL, ssp);
//            Uri uri = Uri.withAppendedPath(People.WITH_EMAIL_OR_IM_FILTER_URI, Uri.encode(ssp));
//            mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
//                    PEOPLE_PROJECTION, null, null, null);

            Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_EMAIL_FILTER_URI, Uri.encode(ssp));
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                    CONTACTS_PROJECTION, null, null, null);

        } else if (SCHEME_TEL.equals(scheme)) {
            mCreateExtras.putString(Intents.Insert.PHONE, ssp);
//            mQueryHandler.startQuery(QUERY_TOKEN, null,
//                    Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, ssp),
//                    PHONES_PROJECTION, null, null, null);

        } else {
            // Otherwise assume incoming aggregate Uri
            final int y = getIntent().getExtras().getInt(EXTRA_Y, DEFAULT_Y);
            showFastTrack(data, y);

        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mQueryHandler != null) {
            mQueryHandler.cancelOperation(QUERY_TOKEN);
        }
        if (mFastTrack != null) {
            mFastTrack.dismiss();
        }
    }

    /**
     * Show a {@link FastTrackWindow} for the given aggregate at the requested
     * screen location.
     */
    private void showFastTrack(Uri aggUri, int y) {
        // Use our local window token for now
        final IBinder windowToken = findViewById(android.R.id.empty).getWindowToken();
        FakeView fakeView = new FakeView(this, windowToken);

        final MappingCache mappingCache = MappingCache.createAndFill(this);

        mFastTrack = new FastTrackWindow(this, fakeView, aggUri, mappingCache);
        mFastTrack.showAt(0, y);
    }

    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cursor == null) {
            return;
        }

        // Count contacts found by query
        int count = 0;
        long aggId = -1;
        try {
            count = cursor.getCount();
            if (count == 1 && cursor.moveToFirst()) {
                // Try reading ID if only one contact returned
                aggId = cursor.getLong(AGGREGATE_ID_INDEX);
            }
        } finally {
            cursor.close();
        }

        if (count == 1 && aggId != -1) {
            // If we only found one item, jump right to viewing it
            final Uri aggUri = ContentUris.withAppendedId(Aggregates.CONTENT_URI, aggId);
            final int y = getIntent().getExtras().getInt(EXTRA_Y, DEFAULT_Y);
            showFastTrack(aggUri, y);

//            Intent viewIntent = new Intent(Intent.ACTION_VIEW,
//                    ContentUris.withAppendedId(People.CONTENT_URI, personId));
//            activity.startActivity(viewIntent);
//            activity.finish();

        } else if (count > 1) {
            // If more than one, show pick list
            Intent listIntent = new Intent(Intent.ACTION_SEARCH);
            listIntent.setComponent(new ComponentName(this, ContactsListActivity.class));
            listIntent.putExtras(mCreateExtras);
            startActivity(listIntent);
            finish();

        } else {
            // No matching contacts found
            if (mCreateForce) {
                // Forced to create new contact
                Intent createIntent = new Intent(Intent.ACTION_INSERT, People.CONTENT_URI);
                createIntent.putExtras(mCreateExtras);
                createIntent.setType(People.CONTENT_TYPE);

                startActivity(createIntent);
                finish();

            } else {
                // Prompt user to insert or edit contact
                Intent createIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                createIntent.putExtras(mCreateExtras);
                createIntent.setType(People.CONTENT_ITEM_TYPE);

                CharSequence message = getResources().getString(
                        R.string.add_contact_dlg_message_fmt, mCreateDescrip);

                new AlertDialog.Builder(this)
                        .setTitle(R.string.add_contact_dlg_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok,
                                new IntentClickListener(this, createIntent))
                        .setNegativeButton(android.R.string.cancel,
                                new IntentClickListener(this, null))
                        .show();
            }
        }
    }

    /**
     * Listener for {@link DialogInterface} that launches a given {@link Intent}
     * when clicked. When clicked, this also closes the parent using
     * {@link Activity#finish()}.
     */
    private static class IntentClickListener implements DialogInterface.OnClickListener {
        private Activity mParent;
        private Intent mIntent;

        /**
         * @param parent {@link Activity} to use for launching target.
         * @param intent Target {@link Intent} to launch when clicked.
         */
        public IntentClickListener(Activity parent, Intent intent) {
            mParent = parent;
            mIntent = intent;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (mIntent != null) {
                mParent.startActivity(mIntent);
            }
            mParent.finish();
        }
    }

    /**
     * Fake view that simply exists to pass through a specific {@link IBinder}
     * window token.
     */
    private static class FakeView extends View {
        private IBinder mWindowToken;

        public FakeView(Context context, IBinder windowToken) {
            super(context);
            mWindowToken = windowToken;
        }

        @Override
        public IBinder getWindowToken() {
            return mWindowToken;
        }
    }
}
