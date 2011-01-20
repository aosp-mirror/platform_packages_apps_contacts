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

package com.android.contacts.interactions;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.google.android.collect.Sets;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Entity;

import java.util.HashSet;

/**
 * An interaction invoked to delete a contact.
 */
public class ContactDeletionInteraction extends Fragment
        implements LoaderCallbacks<Cursor>, OnDismissListener {

    private static final String FRAGMENT_TAG = "deleteContact";

    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CONTACT_URI = "contactUri";
    public static final String ARG_CONTACT_URI = "contactUri";

    private static final String[] ENTITY_PROJECTION = new String[] {
        Entity.RAW_CONTACT_ID, //0
        Entity.ACCOUNT_TYPE, //1
        Entity.CONTACT_ID, // 2
        Entity.LOOKUP_KEY, // 3
    };

    private static final int COLUMN_INDEX_RAW_CONTACT_ID = 0;
    private static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
    private static final int COLUMN_INDEX_CONTACT_ID = 2;
    private static final int COLUMN_INDEX_LOOKUP_KEY = 3;

    private boolean mActive;
    private Uri mContactUri;
    private Context mContext;

    private AlertDialog mDialog;

    // Visible for testing
    int mMessageId;

    public static ContactDeletionInteraction start(Activity activity, Uri contactUri) {
        FragmentManager fragmentManager = activity.getFragmentManager();
        ContactDeletionInteraction fragment =
                (ContactDeletionInteraction) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ContactDeletionInteraction();
            fragment.setContactUri(contactUri);
            fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG).commit();
        } else {
            fragment.setContactUri(contactUri);
        }
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    public void setContactUri(Uri contactUri) {
        mContactUri = contactUri;
        mActive = true;
        if (isStarted()) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().restartLoader(R.id.dialog_delete_contact_loader_id, args, this);
        }
    }

    /* Visible for testing */
    boolean isStarted() {
        return isAdded();
    }

    @Override
    public void onStart() {
        if (mActive) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().initLoader(R.id.dialog_delete_contact_loader_id, args, this);
        }
        super.onStart();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri contactUri = args.getParcelable(ARG_CONTACT_URI);
        return new CursorLoader(mContext,
                Uri.withAppendedPath(contactUri, Entity.CONTENT_DIRECTORY), ENTITY_PROJECTION,
                null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (!mActive) {
            return;
        }

        long contactId = 0;
        String lookupKey = null;

        // This cursor may contain duplicate raw contacts, so we need to de-dupe them first
        HashSet<Long>  readOnlyRawContacts = Sets.newHashSet();
        HashSet<Long>  writableRawContacts = Sets.newHashSet();

        AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long rawContactId = cursor.getLong(COLUMN_INDEX_RAW_CONTACT_ID);
            final String accountType = cursor.getString(COLUMN_INDEX_ACCOUNT_TYPE);
            contactId = cursor.getLong(COLUMN_INDEX_CONTACT_ID);
            lookupKey = cursor.getString(COLUMN_INDEX_LOOKUP_KEY);
            AccountType type = accountTypes.getAccountType(accountType);
            boolean readonly = type != null && type.readOnly;
            if (readonly) {
                readOnlyRawContacts.add(rawContactId);
            } else {
                writableRawContacts.add(rawContactId);
            }
        }

        int readOnlyCount = readOnlyRawContacts.size();
        int writableCount = writableRawContacts.size();
        if (readOnlyCount > 0 && writableCount > 0) {
            mMessageId = R.string.readOnlyContactDeleteConfirmation;
        } else if (readOnlyCount > 0 && writableCount == 0) {
            mMessageId = R.string.readOnlyContactWarning;
        } else if (readOnlyCount == 0 && writableCount > 1) {
            mMessageId = R.string.multipleContactDeleteConfirmation;
        } else {
            mMessageId = R.string.deleteConfirmation;
        }

        final Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);
        showDialog(mMessageId, contactUri);
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void showDialog(int messageId, final Uri contactUri) {
        mDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.deleteConfirmation_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(messageId)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            doDeleteContact(contactUri);
                        }
                    }
                )
                .create();

        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mActive = false;
        mDialog = null;
        getLoaderManager().destroyLoader(R.id.dialog_delete_contact_loader_id);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ACTIVE, mActive);
        outState.putParcelable(KEY_CONTACT_URI, mContactUri);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mActive = savedInstanceState.getBoolean(KEY_ACTIVE);
            mContactUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
        }
    }

    protected void doDeleteContact(Uri contactUri) {
        mContext.startService(ContactSaveService.createDeleteContactIntent(mContext, contactUri));
    }
}
