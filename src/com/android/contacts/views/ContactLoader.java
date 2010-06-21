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

package com.android.contacts.views;

import com.android.contacts.util.DataStatus;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Loader;
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
    private static final String TAG = "ContactLoader";

    private Uri mLookupUri;
    private Result mContact;
    private ForceLoadContentObserver mObserver;
    private boolean mDestroyed;

    public interface Listener {
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

        /**
         * Singleton instance that represents an error, e.g. because of an invalid Uri
         * TODO: We should come up with something nicer here. Maybe use an Either type so
         * that we can capture the Exception?
         */
        public static final Result ERROR = new Result();

        private final Uri mLookupUri;
        private final String mLookupKey;
        private final Uri mUri;
        private final long mId;
        private final long mNameRawContactId;
        private final int mDisplayNameSource;
        private final long mPhotoId;
        private final String mDisplayName;
        private final String mPhoneticName;
        private final boolean mStarred;
        private final Integer mPresence;
        private final ArrayList<Entity> mEntities;
        private final HashMap<Long, DataStatus> mStatuses;
        private final String mStatus;
        private final Long mStatusTimestamp;
        private final Integer mStatusLabel;
        private final String mStatusResPackage;

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
            mPhotoId = -1;
            mDisplayName = null;
            mPhoneticName = null;
            mStarred = false;
            mPresence = null;
            mStatus = null;
            mStatusTimestamp = null;
            mStatusLabel = null;
            mStatusResPackage = null;
        }

        /**
         * Constructor to call when contact was found
         */
        private Result(Uri lookupUri, String lookupKey, Uri uri, long id, long nameRawContactId,
                int displayNameSource, long photoId, String displayName, String phoneticName,
                boolean starred, Integer presence, String status, Long statusTimestamp,
                Integer statusLabel, String statusResPackage) {
            mLookupUri = lookupUri;
            mLookupKey = lookupKey;
            mUri = uri;
            mId = id;
            mEntities = new ArrayList<Entity>();
            mStatuses = new HashMap<Long, DataStatus>();
            mNameRawContactId = nameRawContactId;
            mDisplayNameSource = displayNameSource;
            mPhotoId = photoId;
            mDisplayName = displayName;
            mPhoneticName = phoneticName;
            mStarred = starred;
            mPresence = presence;
            mStatus = status;
            mStatusTimestamp = statusTimestamp;
            mStatusLabel = statusLabel;
            mStatusResPackage = statusResPackage;
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
        public long getNameRawContactId() {
            return mNameRawContactId;
        }
        public int getDisplayNameSource() {
            return mDisplayNameSource;
        }
        public long getPhotoId() {
            return mPhotoId;
        }
        public String getDisplayName() {
            return mDisplayName;
        }
        public String getPhoneticName() {
            return mPhoneticName;
        }
        public boolean getStarred() {
            return mStarred;
        }
        public Integer getPresence() {
            return mPresence;
        }
        public String getStatus() {
            return mStatus;
        }
        public Long getStatusTimestamp() {
            return mStatusTimestamp;
        }
        public Integer getStatusLabel() {
            return mStatusLabel;
        }
        public String getStatusResPackage() {
            return mStatusResPackage;
        }
        public ArrayList<Entity> getEntities() {
            return mEntities;
        }
        public HashMap<Long, DataStatus> getStatuses() {
            return mStatuses;
        }
    }

    private interface StatusQuery {
        final String[] PROJECTION = new String[] {
                Data._ID, Data.STATUS, Data.STATUS_RES_PACKAGE, Data.STATUS_ICON,
                Data.STATUS_LABEL, Data.STATUS_TIMESTAMP, Data.PRESENCE,
        };

        final int _ID = 0;
    }

    private interface ContactQuery {
        //Projection used for the summary info in the header.
        final static String[] COLUMNS = new String[] {
                Contacts.NAME_RAW_CONTACT_ID,
                Contacts.DISPLAY_NAME_SOURCE,
                Contacts.LOOKUP_KEY,
                Contacts.DISPLAY_NAME,
                Contacts.PHONETIC_NAME,
                Contacts.PHOTO_ID,
                Contacts.STARRED,
                Contacts.CONTACT_PRESENCE,
                Contacts.CONTACT_STATUS,
                Contacts.CONTACT_STATUS_TIMESTAMP,
                Contacts.CONTACT_STATUS_RES_PACKAGE,
                Contacts.CONTACT_STATUS_LABEL,
        };
        final static int NAME_RAW_CONTACT_ID = 0;
        final static int DISPLAY_NAME_SOURCE = 1;
        final static int LOOKUP_KEY = 2;
        final static int DISPLAY_NAME = 3;
        final static int PHONETIC_NAME = 4;
        final static int PHOTO_ID = 5;
        final static int STARRED = 6;
        final static int CONTACT_PRESENCE = 7;
        final static int CONTACT_STATUS = 8;
        final static int CONTACT_STATUS_TIMESTAMP = 9;
        final static int CONTACT_STATUS_RES_PACKAGE = 10;
        final static int CONTACT_STATUS_LABEL = 11;
    }

    private final class LoadContactTask extends AsyncTask<Void, Void, Result> {

        @Override
        protected Result doInBackground(Void... args) {
            try {
                final ContentResolver resolver = getContext().getContentResolver();
                final Uri uriCurrentFormat = ensureIsContactUri(resolver, mLookupUri);
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
            } catch (Exception e) {
                Log.w(TAG, "Error loading the contact: " + e.getMessage());
                return Result.ERROR;
            }
        }

        /**
         * Transforms the given Uri and returns a Lookup-Uri that represents the contact.
         * For legacy contacts, a raw-contact lookup is performed.
         * @param resolver
         */
        private Uri ensureIsContactUri(final ContentResolver resolver, final Uri uri) {
            if (uri == null) throw new IllegalArgumentException("uri must not be null");

            final String authority = uri.getAuthority();

            // Current Style Uri?
            if (ContactsContract.AUTHORITY.equals(authority)) {
                final String type = resolver.getType(uri);
                // Contact-Uri? Good, return it
                if (Contacts.CONTENT_ITEM_TYPE.equals(type)) {
                    return uri;
                }

                // RawContact-Uri? Transform it to ContactUri
                if (RawContacts.CONTENT_ITEM_TYPE.equals(type)) {
                    final long rawContactId = ContentUris.parseId(uri);
                    return RawContacts.getContactLookupUri(getContext().getContentResolver(),
                            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
                }

                // Anything else? We don't know what this is
                throw new IllegalArgumentException("uri format is unknown");
            }

            // Legacy Style? Convert to RawContact
            final String OBSOLETE_AUTHORITY = "contacts";
            if (OBSOLETE_AUTHORITY.equals(authority)) {
                // Legacy Format. Convert to RawContact-Uri and then lookup the contact
                final long rawContactId = ContentUris.parseId(uri);
                return RawContacts.getContactLookupUri(resolver,
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
            }

            throw new IllegalArgumentException("uri authority is unknown");
        }

        /**
         * Tries to lookup a contact using both Id and lookup key of the given Uri. Returns a
         * valid Result instance if successful or {@link Result#NOT_FOUND} if empty
         */
        private Result loadContactHeaderData(final ContentResolver resolver, final Uri lookupUri) {
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

            final Cursor cursor = resolver.query(contactUri, ContactQuery.COLUMNS, null, null,
                    null);
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
                final String lookupKey = cursor.getString(ContactQuery.LOOKUP_KEY);
                if (!lookupKey.equals(uriLookupKey)) {
                    // ID and lookup key do not match
                    Log.w(TAG, "Contact with Id=" + uriContactId + " has a wrong lookupKey ("
                            + lookupKey + " instead of the expected " + uriLookupKey + ")");
                    return Result.NOT_FOUND;
                }

                final long nameRawContactId = cursor.getLong(ContactQuery.NAME_RAW_CONTACT_ID);
                final int displayNameSource = cursor.getInt(ContactQuery.DISPLAY_NAME_SOURCE);
                final String displayName = cursor.getString(ContactQuery.DISPLAY_NAME);
                final String phoneticName = cursor.getString(ContactQuery.PHONETIC_NAME);
                final long photoId = cursor.getLong(ContactQuery.PHOTO_ID);
                final boolean starred = cursor.getInt(ContactQuery.STARRED) != 0;
                final Integer presence = cursor.isNull(ContactQuery.CONTACT_PRESENCE)
                        ? null
                        : cursor.getInt(ContactQuery.CONTACT_PRESENCE);
                final String status = cursor.getString(ContactQuery.CONTACT_STATUS);
                final Long statusTimestamp = cursor.isNull(ContactQuery.CONTACT_STATUS_TIMESTAMP)
                        ? null
                        : cursor.getLong(ContactQuery.CONTACT_STATUS_TIMESTAMP);
                final Integer statusLabel = cursor.isNull(ContactQuery.CONTACT_STATUS_LABEL)
                        ? null
                        : cursor.getInt(ContactQuery.CONTACT_STATUS_LABEL);
                final String statusResPackage = cursor.getString(
                        ContactQuery.CONTACT_STATUS_RES_PACKAGE);

                return new Result(lookupUri, lookupKey, contactUri, uriContactId, nameRawContactId,
                        displayNameSource, photoId, displayName, phoneticName, starred, presence,
                        status, statusTimestamp, statusLabel, statusResPackage);
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
            mLookupUri = result.getLookupUri();
            if (result != null) {
                unregisterObserver();
                if (mObserver == null) {
                    mObserver = new ForceLoadContentObserver();
                }
                Log.i(TAG, "Registering content observer for " + mLookupUri);

                if (result != Result.ERROR && result != Result.NOT_FOUND) {
                    getContext().getContentResolver().registerContentObserver(mLookupUri, true,
                            mObserver);
                }
                deliverResult(result);
            }
        }
    }

    private void unregisterObserver() {
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
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
        final LoadContactTask task = new LoadContactTask();
        task.execute((Void[])null);
    }

    @Override
    public void stopLoading() {
        mContact = null;
    }

    @Override
    public void destroy() {
        mContact = null;
        mDestroyed = true;
    }
}
