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
package com.android.contacts.list;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Data;

import java.util.Random;

/**
 * Constructs shortcut intents.
 */
public class ShortcutIntentBuilder {

    private static final String[] CONTACT_COLUMNS = {
        Contacts.DISPLAY_NAME,
        Contacts.PHOTO_ID,
    };

    private static final int CONTACT_DISPLAY_NAME_COLUMN_INDEX = 0;
    private static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 1;

    private static final String[] PHOTO_COLUMNS = {
        Photo.PHOTO,
    };

    private static final int PHOTO_PHOTO_COLUMN_INDEX = 0;

    private static final String PHOTO_SELECTION = Photo._ID + "=?";

    private final OnShortcutIntentCreatedListener mListener;
    private final Context mContext;
    private final int mIconSize;

    /**
     * Listener interface.
     */
    public interface OnShortcutIntentCreatedListener {

        /**
         * Callback for shortcut intent creation.
         *
         * @param uri the original URI for which the shortcut intent has been
         *            created.
         * @param shortcutIntent resulting shortcut intent.
         */
        void onShortcutIntentCreated(Uri uri, Intent shortcutIntent);
    }

    public ShortcutIntentBuilder(Context context, OnShortcutIntentCreatedListener listener) {
        mContext = context;
        mListener = listener;

        mIconSize = context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
    }

    public void createContactShortcutIntent(Uri contactUri) {
        new ContactLoadingAsyncTask().execute(contactUri);
    }

    /**
     * An asynchronous task that loads the photo from the database.
     */
    private final class ContactLoadingAsyncTask extends AsyncTask<Uri, Void, Void> {
        private Uri mUri;
        private String mDisplayName;
        private byte[] mBitmapData;

        @Override
        protected Void doInBackground(Uri... uris) {
            mUri = uris[0];

            ContentResolver resolver = mContext.getContentResolver();

            long photoId = 0;

            Cursor cursor = resolver.query(mUri, CONTACT_COLUMNS, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mDisplayName = cursor.getString(CONTACT_DISPLAY_NAME_COLUMN_INDEX);
                        photoId = cursor.getLong(CONTACT_PHOTO_ID_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }

            if (photoId != 0) {
                cursor = resolver.query(Data.CONTENT_URI, PHOTO_COLUMNS, PHOTO_SELECTION,
                        new String[] { String.valueOf(photoId) }, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            mBitmapData = cursor.getBlob(PHOTO_PHOTO_COLUMN_INDEX);
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            createContactShortcutIntent(mUri, mDisplayName, mBitmapData);
        }
    }

    public void createContactShortcutIntent(Uri contactUri, String displayName, byte[] bitmapData) {

        Bitmap bitmap;

        if (bitmapData != null) {
            bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length, null);
        } else {
            final int[] fallbacks = {
                R.drawable.ic_contact_picture,
                R.drawable.ic_contact_picture_2,
                R.drawable.ic_contact_picture_3
            };
            bitmap = BitmapFactory.decodeResource(mContext.getResources(),
                    fallbacks[new Random().nextInt(fallbacks.length)]);
        }

        Intent shortcutIntent;
        // This is a simple shortcut to view a contact.
        shortcutIntent = new Intent(ContactsContract.QuickContact.ACTION_QUICK_CONTACT);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        shortcutIntent.setData(contactUri);
        shortcutIntent.putExtra(ContactsContract.QuickContact.EXTRA_MODE,
                ContactsContract.QuickContact.MODE_LARGE);
        shortcutIntent.putExtra(ContactsContract.QuickContact.EXTRA_EXCLUDE_MIMES,
                (String[]) null);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent intent = new Intent();

        final Bitmap icon = framePhoto(bitmap);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, scaleToAppIconSize(icon));
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName);

        mListener.onShortcutIntentCreated(contactUri, intent);
    }

    private Bitmap framePhoto(Bitmap photo) {
        final Resources r = mContext.getResources();
        final Drawable frame = r.getDrawable(com.android.internal.R.drawable.quickcontact_badge);

        final int width = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_width);
        final int height = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_height);

        frame.setBounds(0, 0, width, height);

        final Rect padding = new Rect();
        frame.getPadding(padding);

        final Rect source = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        final Rect destination = new Rect(padding.left, padding.top,
                width - padding.right, height - padding.bottom);

        final int d = Math.max(width, height);
        final Bitmap b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);

        c.translate((d - width) / 2.0f, (d - height) / 2.0f);
        frame.draw(c);
        c.drawBitmap(photo, source, destination, new Paint(Paint.FILTER_BITMAP_FLAG));

        return b;
    }

    private Bitmap scaleToAppIconSize(Bitmap photo) {

        // Setup the drawing classes
        Bitmap icon = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0,0, photo.getWidth(),photo.getHeight());
        Rect dst = new Rect(0,0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        return icon;
    }
}
