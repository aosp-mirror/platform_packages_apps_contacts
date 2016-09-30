package com.android.contacts.editor;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;

/**
 * Loader for the pick a raw contact to edit activity. Loads all raw contact metadata for the
 * given Contact {@link Uri}.
 */
public class PickRawContactLoader extends CursorLoader {
    private Uri mContactUri;

    public static final String[] COLUMNS = new String[] {
            RawContacts.ACCOUNT_NAME,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.DATA_SET,
            RawContacts._ID,
            RawContacts.DISPLAY_NAME_PRIMARY,
            RawContacts.DISPLAY_NAME_ALTERNATIVE
    };

    public static final String SELECTION = RawContacts.CONTACT_ID + "=?";

    public static final int ACCOUNT_NAME = 0;
    public static final int ACCOUNT_TYPE = 1;
    public static final int DATA_SET = 2;
    public static final int RAW_CONTACT_ID = 3;
    public static final int DISPLAY_NAME_PRIMARY = 4;
    public static final int DISPLAY_NAME_ALTERNATIVE = 5;

    public PickRawContactLoader(Context context, Uri contactUri) {
        super(context, ensureIsContactUri(contactUri), COLUMNS, SELECTION, null, RawContacts._ID);
        mContactUri = contactUri;
    }

    @Override
    public Cursor loadInBackground() {
        // Get the id of the contact we're looking at.
        final Cursor cursor = getContext().getContentResolver()
                .query(mContactUri, new String[] { Contacts._ID }, null,
                null, null);

        if (cursor == null) {
            return null;
        }

        if (cursor.getCount() < 1) {
            cursor.close();
            return null;
        }

        cursor.moveToFirst();
        final long contactId = cursor.getLong(0);
        cursor.close();
        // Update selection arguments and uri.
        setSelectionArgs(new String[]{ Long.toString(contactId) });
        setUri(RawContacts.CONTENT_URI);
        return super.loadInBackground();
    }

    /**
     * Ensures that this is a valid contact URI. If invalid, then an exception is
     * thrown. Otherwise, the original URI is returned.
     */
    private static Uri ensureIsContactUri(final Uri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Uri must not be null");
        }
        if (!uri.toString().startsWith(Contacts.CONTENT_URI.toString())) {
            throw new IllegalArgumentException("Invalid contact Uri: " + uri);
        }
        return uri;
    }
}
