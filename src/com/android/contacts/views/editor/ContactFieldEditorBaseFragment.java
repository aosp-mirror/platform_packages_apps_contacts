/*
 * Copyright (C) 2010 Google Inc.
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
 * limitations under the License
 */

package com.android.contacts.views.editor;

import com.android.contacts.R;
import com.android.contacts.views.ContactLoader;

import android.app.Activity;
import android.app.LoaderManagingFragment;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.content.ContentProviderOperation.Builder;
import android.content.Entity.NamedContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public abstract class ContactFieldEditorBaseFragment
        extends LoaderManagingFragment<ContactLoader.Result> {
    private static final String TAG = "ContactFieldEditorBaseFragment";

    private static final int LOADER_DETAILS = 1;

    private long mRawContactId;
    private Uri mRawContactUri;
    private long mDataId;
    private Uri mDataUri;
    private Context mContext;
    private Listener mListener;
    private ContactLoader.Result mContactData;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    public ContactLoader.Result getContactData() {
        return mContactData;
    }

    /**
     * Sets the Uris for the Fragment. If the dataUri is null, this is interpreted as an Insert
     * to an existing rawContact. rawContactUri must always be given
     */
    public void setupUris(Uri rawContactUri, Uri dataUri) {
        mRawContactUri = rawContactUri;
        mRawContactId = Long.parseLong(rawContactUri.getLastPathSegment());
        mDataUri = dataUri;
        mDataId = dataUri == null ? 0 : Long.parseLong(dataUri.getLastPathSegment());
    }

    @Override
    protected void onInitializeLoaders() {
        startLoading(LOADER_DETAILS, null);
    }

    @Override
    protected Loader<ContactLoader.Result> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case LOADER_DETAILS: {
                return new ContactLoader(mContext,
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, mRawContactId));
            }
            default: {
                Log.wtf(TAG, "Unknown ID in onCreateLoader: " + id);
            }
        }
        return null;
    }

    @Override
    protected void onLoadFinished(Loader<ContactLoader.Result> loader,
            ContactLoader.Result data) {
        final int id = loader.getId();
        switch (id) {
            case LOADER_DETAILS:
                if (data == ContactLoader.Result.NOT_FOUND) {
                    // Item has been deleted
                    Log.i(TAG, "No contact found. Closing activity");
                    if (mListener != null) mListener.onContactNotFound();
                    return;
                }
                if (data == ContactLoader.Result.ERROR) {
                    throw new IllegalStateException("Error during data loading");
                }
                mContactData = data;

                // Find the correct RawContact
                boolean valueFound = false;
                for (Entity entity : mContactData.getEntities()) {
                    final ContentValues entValues = entity.getEntityValues();
                    final long rawContactId = entValues.getAsLong(RawContacts._ID);
                    if (rawContactId == mRawContactId) {
                        for (NamedContentValues subValue : entity.getSubValues()) {
                            final long dataId = subValue.values.getAsLong(Data._ID);
                            if (dataId == mDataId) {
                                loadData(subValue);
                                valueFound = true;
                                break;
                            }
                        }
                    }
                }
                if (!valueFound) {
                    // Item has been deleted
                    Log.i(TAG, "Data item not found. Closing activity");
                    if (mListener != null) mListener.onDataNotFound();
                    return;
                }
                break;
            default: {
                Log.wtf(TAG, "Unknown ID in onLoadFinished: " + id);
            }
        }
    }

    protected void saveData() {
        final ContentResolver resolver = getActivity().getContentResolver();

        final ArrayList<ContentProviderOperation> operations =
            new ArrayList<ContentProviderOperation>();

        final Builder builder;
        if (getDataUri() == null) {
            // INSERT
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValue(Data.RAW_CONTACT_ID, getRawContactId());
            saveData(builder);
        } else {
            // UPDATE
            builder = ContentProviderOperation.newUpdate(getDataUri());
            saveData(builder);
        }
        operations.add(builder.build());

        // TODO: Do in Background thread/service
        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operations);
        } catch (RemoteException e) {
            Toast.makeText(getActivity(), R.string.edit_error_saving, Toast.LENGTH_LONG).show();
        } catch (OperationApplicationException e) {
            Toast.makeText(getActivity(), R.string.edit_error_saving, Toast.LENGTH_LONG).show();
        }
    }

    protected abstract void saveData(final Builder builder);

    protected abstract void loadData(NamedContentValues contentValues);

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public Listener getListener() {
        return mListener;
    }

    public Uri getRawContactUri() {
        return mRawContactUri;
    }

    public long getRawContactId() {
        return mRawContactId;
    }

    public Uri getDataUri() {
        return mDataUri;
    }

    public long getDataId() {
        return mDataId;
    }

    public static interface Listener {
        void onContactNotFound();
        void onDataNotFound();
        void onCancel();
        void onSaved();
    }
}
