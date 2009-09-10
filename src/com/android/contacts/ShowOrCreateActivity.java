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

import com.android.contacts.ui.FastTrackWindow;
import com.android.contacts.util.Constants;
import com.android.contacts.util.NotifyingAsyncQueryHandler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.EntityIterator;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.util.Log;

/**
 * Handle several edge cases around showing or possibly creating contacts in
 * connected with a specific E-mail address or phone number. Will search based
 * on incoming {@link Intent#getData()} as described by
 * {@link Intents#SHOW_OR_CREATE_CONTACT}.
 * <ul>
 * <li>If no matching contacts found, will prompt user with dialog to add to a
 * contact, then will use {@link Intent#ACTION_INSERT_OR_EDIT} to let create new
 * contact or edit new data into an existing one.
 * <li>If one matching contact found, show the {@link FastTrackWindow}
 * associated with the found contact. Will show translucent over the caller.
 * <li>If more than one matching found, show list of matching contacts using
 * {@link Intent#ACTION_SEARCH}.
 * </ul>
 */
public final class ShowOrCreateActivity extends Activity implements
        NotifyingAsyncQueryHandler.AsyncQueryListener, FastTrackWindow.OnDismissListener {
    static final String TAG = "ShowOrCreateActivity";
    static final boolean LOGD = false;

    static final String[] PHONES_PROJECTION = new String[] {
        PhoneLookup._ID,
    };

    static final String[] CONTACTS_PROJECTION = new String[] {
        RawContacts.CONTACT_ID,
    };

    static final int AGGREGATE_ID_INDEX = 0;

    static final int QUERY_TOKEN = 42;

    private NotifyingAsyncQueryHandler mQueryHandler;

    private Bundle mCreateExtras;
    private String mCreateDescrip;
    private boolean mCreateForce;

    private FastTrackWindow mFastTrack;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

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
        mCreateDescrip = intent.getStringExtra(Intents.EXTRA_CREATE_DESCRIPTION);
        if (mCreateDescrip == null) {
            mCreateDescrip = ssp;
        }

        // Allow caller to bypass dialog prompt
        mCreateForce = intent.getBooleanExtra(Intents.EXTRA_FORCE_CREATE, false);

        // Handle specific query request
        if (Constants.SCHEME_MAILTO.equals(scheme)) {
            mCreateExtras.putString(Intents.Insert.EMAIL, ssp);

            Uri uri = Uri.withAppendedPath(Email.CONTENT_FILTER_EMAIL_URI, Uri.encode(ssp));
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, CONTACTS_PROJECTION, null, null, null);

        } else if (Constants.SCHEME_TEL.equals(scheme)) {
            mCreateExtras.putString(Intents.Insert.PHONE, ssp);

            Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, ssp);
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, PHONES_PROJECTION, null, null, null);

        } else {
            // Otherwise assume incoming aggregate Uri
            showFastTrack(data);

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
    private void showFastTrack(Uri aggUri) {
        // Use our local window token for now
        final Bundle extras = getIntent().getExtras();

        Rect targetRect;
        if (extras.containsKey(Intents.EXTRA_TARGET_RECT)) {
            targetRect = (Rect)extras.getParcelable(Intents.EXTRA_TARGET_RECT);
        } else {
            // TODO: this default rect matches gmail messages, and should move over there
            Log.w(TAG, "Using default TARGET_RECT");
            targetRect = new Rect(15, 110, 15+18, 110+18);
        }

        // Use requested display mode, defaulting to medium
        final int mode = extras.getInt(Intents.EXTRA_MODE, Intents.MODE_MEDIUM);
        final String[] excludeMimes = extras.getStringArray(Intents.EXTRA_EXCLUDE_MIMES);

        mFastTrack = new FastTrackWindow(this, this);
        mFastTrack.show(aggUri, targetRect, mode, excludeMimes);
    }

    /** {@inheritDoc} */
    public void onDismiss(FastTrackWindow dialog) {
        // When dismissed, finish this activity
        finish();
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cursor == null) {
            // Bail when problem running query in background
            finish();
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
            // If we only found one item, show fast-track
            final Uri aggUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, aggId);
            showFastTrack(aggUri);

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
                Intent createIntent = new Intent(Intent.ACTION_INSERT, RawContacts.CONTENT_URI);
                createIntent.putExtras(mCreateExtras);
                createIntent.setType(RawContacts.CONTENT_TYPE);

                startActivity(createIntent);
                finish();

            } else {
                // Prompt user to insert or edit contact
                Intent createIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                createIntent.putExtras(mCreateExtras);
                createIntent.setType(RawContacts.CONTENT_ITEM_TYPE);

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

    /** {@inheritDoc} */
    public void onQueryEntitiesComplete(int token, Object cookie, EntityIterator iterator) {
        // No actions
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
}
