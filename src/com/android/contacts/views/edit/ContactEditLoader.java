package com.android.contacts.views.edit;

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

public class ContactEditLoader extends Loader<ContactEditLoader.Result> {
    private static final String TAG = "ContactEditLoader";

    private final Uri mLookupUri;
    private final String mMimeType;
    private Result mContact;
    private boolean mDestroyed;
    private ForceLoadContentObserver mObserver;
    private final Bundle mIntentExtras;

    public ContactEditLoader(Context context, Uri lookupUri, String mimeType,
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
            final Uri uriCurrentFormat = convertLegacyIfNecessary(mLookupUri);

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
