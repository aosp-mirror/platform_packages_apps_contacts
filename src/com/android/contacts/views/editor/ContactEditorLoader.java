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

import com.android.contacts.ContactsUtils;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.EntitySet;
import com.android.contacts.model.Sources;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Loader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;

public class ContactEditorLoader extends Loader<ContactEditorLoader.Result> {
    private static final String TAG = "ContactEditorLoader";

    private final Uri mLookupUri;
    private final String mMimeType;
    private Result mContact;
    private boolean mDestroyed;
    private ForceLoadContentObserver mObserver;
    private final Bundle mIntentExtras;

    public ContactEditorLoader(Context context, Uri lookupUri, String mimeType,
            Bundle intentExtras) {
        super(context);
        mLookupUri = lookupUri;
        mMimeType = mimeType;
        mIntentExtras = intentExtras;
    }

    /**
     * The result of a load operation. Contains all data necessary to display the contact for
     * editing.
     */
    public static class Result {
        /**
         * Singleton instance that represents "No Contact Found"
         */
        public static final Result NOT_FOUND = new Result(null);

        private final EntitySet mEntitySet;

        private Result(EntitySet entitySet) {
            mEntitySet = entitySet;
        }

        public EntitySet getEntitySet() {
            return mEntitySet;
        }
    }

    private final class LoadContactTask extends AsyncTask<Void, Void, Result> {
        @Override
        protected Result doInBackground(Void... params) {
            final ContentResolver resolver = getContext().getContentResolver();
            final Uri uriCurrentFormat = ensureIsContactUri(resolver, mLookupUri);

            // Handle both legacy and new authorities

            final long contactId;
            final String selection = "0";
            if (Contacts.CONTENT_ITEM_TYPE.equals(mMimeType)) {
                // Handle selected aggregate
                contactId = ContentUris.parseId(uriCurrentFormat);
            } else if (RawContacts.CONTENT_ITEM_TYPE.equals(mMimeType)) {
                // Get id of corresponding aggregate
                final long rawContactId = ContentUris.parseId(uriCurrentFormat);
                contactId = ContactsUtils.queryForContactId(resolver, rawContactId);
            } else throw new IllegalStateException();

            return new Result(EntitySet.fromQuery(resolver, RawContacts.CONTACT_ID + "=?",
                    new String[] { String.valueOf(contactId) }, null));
        }

        /**
         * Transforms the given Uri and returns a Lookup-Uri that represents the contact.
         * For legacy contacts, a raw-contact lookup is performed.
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

        @Override
        protected void onPostExecute(Result result) {
            super.onPostExecute(result);

            // TODO: This merging of extras is probably wrong on subsequent loads

            // Load edit details in background
            final Sources sources = Sources.getInstance(getContext());

            // Handle any incoming values that should be inserted
            final boolean hasExtras = mIntentExtras != null && mIntentExtras.size() > 0;
            final boolean hasState = result.getEntitySet().size() > 0;
            if (hasExtras && hasState) {
                // Find source defining the first RawContact found
                final EntityDelta state = result.getEntitySet().get(0);
                final String accountType = state.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
                final ContactsSource source = sources.getInflatedSource(accountType,
                        ContactsSource.LEVEL_CONSTRAINTS);
                EntityModifier.parseExtras(getContext(), source, state, mIntentExtras);
            }

            // The creator isn't interested in any further updates
            if (mDestroyed) {
                return;
            }

            mContact = result;
            if (result != null) {
                if (mObserver == null) {
                    mObserver = new ForceLoadContentObserver();
                }
                // TODO: Do we want a content observer here?
//                Log.i(TAG, "Registering content observer for " + mLookupUri);
//                getContext().getContentResolver().registerContentObserver(mLookupUri, true,
//                        mObserver);
                deliverResult(result);
            }
        }
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
        LoadContactTask task = new LoadContactTask();
        task.execute((Void[])null);
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