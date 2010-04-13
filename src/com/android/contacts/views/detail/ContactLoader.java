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

package com.android.contacts.views.detail;

import com.android.contacts.mvcframework.Loader;
import com.android.contacts.util.DataStatus;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract.StatusUpdates;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Loads a single Contact and all it constituent RawContacts.
 */
public class ContactLoader extends Loader<ContactLoader.Result> {
    private Uri mLookupUri;
    private Result mContact;
    private ForceLoadContentObserver mObserver;
    private boolean mDestroyed;

    private static final String TAG = "ContactLoader";

    public interface Callbacks {
        public void onContactLoaded(Result contact);
    }

    /**
     * The result of a load operation. Contains all data necessary to display the contact.
     */
    public static final class Result {
        /**
         * Singleton instance that represents "No Contact Found"
         */
        public static final Result NOT_FOUND = new Result();

        private final Uri mLookupUri;
        private final String mLookupKey;
        private final Uri mUri;
        private final long mId;
        private final ArrayList<Entity> mEntities;
        private final HashMap<Long, DataStatus> mStatuses;
        private final long mNameRawContactId;
        private final int mDisplayNameSource;

        /**
         * Constructor for case "no contact found". This must only be used for the
         * final {@link Result#NOT_FOUND} singleton
         */
        private Result() {
            mLookupUri = null;
            mLookupKey = null;
            mUri = null;
            mId = -1;
            mEntities = null;
            mStatuses = null;
            mNameRawContactId = -1;
            mDisplayNameSource = DisplayNameSources.UNDEFINED;
        }

        /**
         * Constructor to call when contact was found
         */
        private Result(Uri lookupUri, String lookupKey, Uri uri, long id, long nameRawContactId,
                int displayNameSource) {
            mLookupUri = lookupUri;
            mLookupKey = lookupKey;
            mUri = uri;
            mId = id;
            mEntities = new ArrayList<Entity>();
            mStatuses = new HashMap<Long, DataStatus>();
            mNameRawContactId = nameRawContactId;
            mDisplayNameSource = displayNameSource;
        }

        public Uri getLookupUri() {
            return mLookupUri;
        }
        public String getLookupKey() {
            return mLookupKey;
        }
        public Uri getUri() {
            return mUri;
        }
        public long getId() {
            return mId;
        }
        public ArrayList<Entity> getEntities() {
            return mEntities;
        }
        public HashMap<Long, DataStatus> getStatuses() {
            return mStatuses;
        }
        public long getNameRawContactId() {
            return mNameRawContactId;
        }
        public int getDisplayNameSource() {
            return mDisplayNameSource;
        }
    }

    interface StatusQuery {
        final String[] PROJECTION = new String[] {
                Data._ID, Data.STATUS, Data.STATUS_RES_PACKAGE, Data.STATUS_ICON,
                Data.STATUS_LABEL, Data.STATUS_TIMESTAMP, Data.PRESENCE,
        };

        final int _ID = 0;
    }

    public final class LoadContactTask extends AsyncTask<Void, Void, Result> {

        /**
         * Used for synchronuous calls in unit test
         * @hide
         */
        public Result testExecute() {
            return doInBackground();
        }

        @Override
        protected Result doInBackground(Void... args) {
            final ContentResolver resolver = getContext().getContentResolver();
            Uri uriCurrentFormat = convertLegacyIfNecessary(mLookupUri);
            Result result = loadContactHeaderData(resolver, uriCurrentFormat);
            if (result == Result.NOT_FOUND) {
                // No record found. Try to lookup up a new record with the same lookupKey.
                // We might have went through a sync where Ids changed
                final Uri freshLookupUri = Contacts.getLookupUri(resolver, uriCurrentFormat);
                result = loadContactHeaderData(resolver, freshLookupUri);
                if (result == Result.NOT_FOUND) {
                    // Still not found. We now believe this contact really does not exist
                    Log.e(TAG, "invalid contact uri: " + mLookupUri);
                    return Result.NOT_FOUND;
                }
            }

            // These queries could be run in parallel (we did this until froyo). But unless
            // we actually have two database connections there is no performance gain
            loadSocial(resolver, result);
            loadRawContacts(resolver, result);

            return result;
        }

        /**
         * Transforms the given Uri and returns a Lookup-Uri that represents the contact.
         * For legacy contacts, a raw-contact lookup is performed.
         */
        private Uri convertLegacyIfNecessary(Uri uri) {
            if (uri == null) throw new IllegalArgumentException("uri must not be null");

            final String authority = uri.getAuthority();

            // Current Style Uri? Just return it
            if (ContactsContract.AUTHORITY.equals(authority)) {
                return uri;
            }

            // Legacy Style? Convert to RawContact
            final String OBSOLETE_AUTHORITY = "contacts";
            if (OBSOLETE_AUTHORITY.equals(authority)) {
                // Legacy Format. Convert to RawContact-Uri and then lookup the contact
                final long rawContactId = ContentUris.parseId(uri);
                return RawContacts.getContactLookupUri(getContext().getContentResolver(),
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
            }

            throw new IllegalArgumentException("uri format is unknown");
        }

        /**
         * Tries to lookup a contact using both Id and lookup key of the given Uri. Returns a
         * valid Result instance if successful or {@link Result#NOT_FOUND} if empty
         */
        private Result loadContactHeaderData(final ContentResolver resolver,
                final Uri lookupUri) {
            if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
            if (lookupUri == null) {
                // This can happen if the row was removed
                return Result.NOT_FOUND;
            }

            final List<String> segments = lookupUri.getPathSegments();
            if (segments.size() != 4) {
                // Does not contain an Id. Return to caller so that a lookup is performed
                Log.w(TAG, "Uri does not contain an Id, so we return to the caller who should " +
                        "perform a lookup to get a proper uri. Value: " + lookupUri);
                return Result.NOT_FOUND;
            }

            final long uriContactId = Long.parseLong(segments.get(3));
            final String uriLookupKey = Uri.encode(segments.get(2));
            final Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, uriContactId);
            final Uri dataUri = Uri.withAppendedPath(contactUri, Contacts.Data.CONTENT_DIRECTORY);

            final Cursor cursor = resolver.query(dataUri,
                    new String[] {
                        Contacts.NAME_RAW_CONTACT_ID,
                        Contacts.DISPLAY_NAME_SOURCE,
                        Contacts.LOOKUP_KEY
                    }, null, null, null);
            if (cursor == null) {
                Log.e(TAG, "No cursor returned in trySetupContactHeader/query");
                return null;
            }
            try {
                if (!cursor.moveToFirst()) {
                    Log.w(TAG, "Cursor returned by trySetupContactHeader/query is empty. " +
                            "ContactId must have changed or item has been removed");
                    return Result.NOT_FOUND;
                }
                String lookupKey =
                        cursor.getString(cursor.getColumnIndex(Contacts.LOOKUP_KEY));
                if (!lookupKey.equals(uriLookupKey)) {
                    // ID and lookup key do not match
                    Log.w(TAG, "Contact with Id=" + uriContactId + " has a wrong lookupKey ("
                            + lookupKey + " instead of the expected " + uriLookupKey + ")");
                    return Result.NOT_FOUND;
                }

                long nameRawContactId = cursor.getLong(cursor.getColumnIndex(
                        Contacts.NAME_RAW_CONTACT_ID));
                int displayNameSource = cursor.getInt(cursor.getColumnIndex(
                        Contacts.DISPLAY_NAME_SOURCE));

                return new Result(lookupUri, lookupKey, contactUri, uriContactId, nameRawContactId,
                        displayNameSource);
            } finally {
                cursor.close();
            }
        }

        /**
         * Loads the social rows into the result structure. Expects the statuses in the
         * result structure to be empty
         */
        private void loadSocial(final ContentResolver resolver, final Result result) {
            if (result == null) throw new IllegalArgumentException("result must not be null");
            if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
            if (result == Result.NOT_FOUND) {
                throw new IllegalArgumentException("result must not be NOT_FOUND");
            }

            final Uri dataUri = Uri.withAppendedPath(result.getUri(),
                    Contacts.Data.CONTENT_DIRECTORY);
            final Cursor cursor = resolver.query(dataUri, StatusQuery.PROJECTION,
                    StatusUpdates.PRESENCE + " IS NOT NULL OR " + StatusUpdates.STATUS +
                    " IS NOT NULL", null, null);

            if (cursor == null) {
                Log.e(TAG, "Social cursor is null but it shouldn't be");
                return;
            }

            try {
                HashMap<Long, DataStatus> statuses = result.getStatuses();

                // Walk found statuses, creating internal row for each
                while (cursor.moveToNext()) {
                    final DataStatus status = new DataStatus(cursor);
                    final long dataId = cursor.getLong(StatusQuery._ID);
                    statuses.put(dataId, status);
                }
            } finally {
                cursor.close();
            }
        }

        /**
         * Loads the raw row contact rows into the result structure. Expects the entities in the
         * result structure to be empty
         */
        private void loadRawContacts(final ContentResolver resolver, final Result result) {
            if (result == null) throw new IllegalArgumentException("result must not be null");
            if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
            if (result == Result.NOT_FOUND) {
                throw new IllegalArgumentException("result must not be NOT_FOUND");
            }

            // Read the constituent raw contacts
            final Cursor cursor = resolver.query(RawContactsEntity.CONTENT_URI, null,
                    RawContacts.CONTACT_ID + "=?", new String[] {
                            String.valueOf(result.mId)
                    }, null);
            if (cursor == null) {
                Log.e(TAG, "Raw contacts cursor is null but it shouldn't be");
                return;
            }

            try {
                ArrayList<Entity> entities = result.getEntities();
                entities.ensureCapacity(cursor.getCount());
                EntityIterator iterator = RawContacts.newEntityIterator(cursor);
                try {
                    while (iterator.hasNext()) {
                        Entity entity = iterator.next();
                        entities.add(entity);
                    }
                } finally {
                    iterator.close();
                }
            } finally {
                cursor.close();
            }
        }

        @Override
        protected void onPostExecute(Result result) {
            // The creator isn't interested in any further updates
            if (mDestroyed) {
                return;
            }

            mContact = result;
            if (result != null) {
                if (mObserver == null) {
                    mObserver = new ForceLoadContentObserver();
                }
                Log.i(TAG, "Registering content observer for " + mLookupUri);
                getContext().getContentResolver().registerContentObserver(
                        mLookupUri, true, mObserver);
                deliverResult(result);
            }
        }
    }

    public ContactLoader(Context context, Uri lookupUri) {
            super(context);
        mLookupUri = lookupUri;
    }

    @Override
    public void startLoading() {
        if (mContact != null) {
            deliverResult(mContact);
        } else {
            forceLoad();
        }
    }

    @Override
    public void forceLoad() {
        new LoadContactTask().execute((Void[])null);
    }

    @Override
    public void stopLoading() {
        mContact = null;
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
        }
    }

    @Override
    public void destroy() {
        mContact = null;
        mDestroyed = true;
    }
}
