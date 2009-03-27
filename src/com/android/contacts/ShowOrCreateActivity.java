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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.ContactMethodsColumns;
import android.provider.Contacts.Intents;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Handle several edge cases around showing or possibly creating contacts in
 * connected with a specific E-mail address or phone number. Will search based
 * on incoming {@link Intent#getData()} as described by
 * {@link Intents#SHOW_OR_CREATE_CONTACT}.
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
public final class ShowOrCreateActivity extends Activity {
    static final String TAG = "ShowOrCreateActivity";
    static final boolean LOGD = false;

    static final String[] PHONES_PROJECTION = new String[] {
        Phones.PERSON_ID,
    };

    static final String[] PEOPLE_PROJECTION = new String[] {
        People._ID,
    };
    
    static final String SCHEME_MAILTO = "mailto";
    static final String SCHEME_TEL = "tel";
    
    static final int PERSON_ID_INDEX = 0;

    /**
     * Query clause to filter {@link ContactMethods#CONTENT_URI} to only search
     * {@link Contacts#KIND_EMAIL} or {@link Contacts#KIND_IM}.
     */
    static final String QUERY_KIND_EMAIL_OR_IM = ContactMethodsColumns.KIND +
            " IN (" + Contacts.KIND_EMAIL + "," + Contacts.KIND_IM + ")";
    
    static final int QUERY_TOKEN = 42;
    
    private QueryHandler mQueryHandler;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        // Create handler if doesn't exist, otherwise cancel any running
        if (mQueryHandler == null) {
            mQueryHandler = new QueryHandler(this);
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
        Bundle createExtras = new Bundle();
        Bundle originalExtras = intent.getExtras();
        if (originalExtras != null) {
            createExtras.putAll(originalExtras);
        }
        mQueryHandler.setCreateExtras(createExtras);
        
        // Read possible extra with specific title
        String createDescrip = intent.getStringExtra(Intents.EXTRA_CREATE_DESCRIPTION);
        if (createDescrip == null) {
            createDescrip = ssp;
        }
        mQueryHandler.setCreateDescription(createDescrip);
        
        // Allow caller to bypass dialog prompt
        boolean createForce = intent.getBooleanExtra(Intents.EXTRA_FORCE_CREATE, false);
        mQueryHandler.setCreateForce(createForce);
        
        // Handle specific query request
        if (SCHEME_MAILTO.equals(scheme)) {
            createExtras.putString(Intents.Insert.EMAIL, ssp);
            Uri uri = Uri.withAppendedPath(People.WITH_EMAIL_OR_IM_FILTER_URI, Uri.encode(ssp));
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                    PEOPLE_PROJECTION, null, null, null);
        } else if (SCHEME_TEL.equals(scheme)) {
            createExtras.putString(Intents.Insert.PHONE, ssp);
            mQueryHandler.startQuery(QUERY_TOKEN, null,
                    Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, ssp),
                    PHONES_PROJECTION, null, null, null);
            
        } else {
            Log.w(TAG, "Invalid intent:" + getIntent());
            finish();
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (mQueryHandler != null) {
            mQueryHandler.cancelOperation(QUERY_TOKEN);
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
     * Handle asynchronous query to find matching contacts. When query finishes,
     * will handle based on number of matching contacts found.
     */
    private static final class QueryHandler extends AsyncQueryHandler {
        private final WeakReference<Activity> mActivity;
        private Bundle mCreateExtras;
        private String mCreateDescrip;
        private boolean mCreateForce;

        public QueryHandler(Activity activity) {
            super(activity.getContentResolver());
            mActivity = new WeakReference<Activity>(activity);
        }
        
        public void setCreateExtras(Bundle createExtras) {
            mCreateExtras = createExtras;
        }
        
        public void setCreateDescription(String createDescrip) {
            mCreateDescrip = createDescrip;
        }
        
        public void setCreateForce(boolean createForce) {
            mCreateForce = createForce;
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            Activity activity = mActivity.get();
            if (activity == null) {
                return;
            }
            
            // Count contacts found by query
            int count = 0;
            long personId = -1;
            if (cursor != null) {
                try {
                    count = cursor.getCount();
                    if (count == 1 && cursor.moveToFirst()) {
                        // Try reading ID if only one contact returned
                        personId = cursor.getLong(PERSON_ID_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
            
            if (LOGD) Log.d(TAG, "onQueryComplete count=" + count);
            
            if (count == 1) {
                // If we only found one item, jump right to viewing it
                Intent viewIntent = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(People.CONTENT_URI, personId));
                activity.startActivity(viewIntent);
                activity.finish();
                
            } else if (count > 1) {
                // If more than one, show pick list
                Intent listIntent = new Intent(Intent.ACTION_SEARCH);
                listIntent.setComponent(new ComponentName(activity, ContactsListActivity.class));
                listIntent.putExtras(mCreateExtras);
                activity.startActivity(listIntent);
                activity.finish();
                
            } else {
                // No matching contacts found
                if (mCreateForce) {
                    // Forced to create new contact
                    Intent createIntent = new Intent(Intent.ACTION_INSERT, People.CONTENT_URI);
                    createIntent.putExtras(mCreateExtras);
                    createIntent.setType(People.CONTENT_TYPE);
                    
                    activity.startActivity(createIntent);
                    activity.finish();
                    
                } else {
                    // Prompt user to insert or edit contact 
                    Intent createIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    createIntent.putExtras(mCreateExtras);
                    createIntent.setType(People.CONTENT_ITEM_TYPE);
                    
                    CharSequence message = activity.getResources().getString(
                            R.string.add_contact_dlg_message_fmt, mCreateDescrip);
                    
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.add_contact_dlg_title)
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok,
                                    new IntentClickListener(activity, createIntent))
                            .setNegativeButton(android.R.string.cancel,
                                    new IntentClickListener(activity, null))
                            .show();
                }
            }
        }
    }
}
