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

import com.android.contacts.R;
import com.android.contacts.util.Constants;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;

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

    private static final String[] PHONE_COLUMNS = {
        Phone.DISPLAY_NAME,
        Phone.PHOTO_ID,
        Phone.NUMBER,
        Phone.TYPE,
    };

    private static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 0;
    private static final int PHONE_PHOTO_ID_COLUMN_INDEX = 1;
    private static final int PHONE_NUMBER_COLUMN_INDEX = 2;
    private static final int PHONE_TYPE_COLUMN_INDEX = 3;

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
        new ContactLoadingAsyncTask(contactUri).execute();
    }

    public void createPhoneNumberShortcutIntent(Uri dataUri, String shortcutAction) {
        new PhoneNumberLoadingAsyncTask(dataUri, shortcutAction).execute();
    }

    /**
     * An asynchronous task that loads name, photo and other data from the database.
     */
    private abstract class LoadingAsyncTask extends AsyncTask<Void, Void, Void> {
        protected Uri mUri;
        protected String mDisplayName;
        protected byte[] mBitmapData;
        protected long mPhotoId;

        public LoadingAsyncTask(Uri uri) {
            mUri = uri;
        }

        @Override
        protected Void doInBackground(Void... params) {
            loadData();
            loadPhoto();
            return null;
        }

        protected abstract void loadData();

        private void loadPhoto() {
            if (mPhotoId == 0) {
                return;
            }

            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(Data.CONTENT_URI, PHOTO_COLUMNS, PHOTO_SELECTION,
                    new String[] { String.valueOf(mPhotoId) }, null);
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
    }

    private final class ContactLoadingAsyncTask extends LoadingAsyncTask {
        public ContactLoadingAsyncTask(Uri uri) {
            super(uri);
        }

        @Override
        protected void loadData() {
            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(mUri, CONTACT_COLUMNS, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mDisplayName = cursor.getString(CONTACT_DISPLAY_NAME_COLUMN_INDEX);
                        mPhotoId = cursor.getLong(CONTACT_PHOTO_ID_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        @Override
        protected void onPostExecute(Void result) {
            createContactShortcutIntent(mUri, mDisplayName, mBitmapData);
        }
    }

    private final class PhoneNumberLoadingAsyncTask extends LoadingAsyncTask {
        private final String mShortcutAction;
        private String mPhoneNumber;
        private int mPhoneType;

        public PhoneNumberLoadingAsyncTask(Uri uri, String shortcutAction) {
            super(uri);
            mShortcutAction = shortcutAction;
        }

        @Override
        protected void loadData() {
            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(mUri, PHONE_COLUMNS, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mDisplayName = cursor.getString(PHONE_DISPLAY_NAME_COLUMN_INDEX);
                        mPhotoId = cursor.getLong(PHONE_PHOTO_ID_COLUMN_INDEX);
                        mPhoneNumber = cursor.getString(PHONE_NUMBER_COLUMN_INDEX);
                        mPhoneType = cursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            createPhoneNumberShortcutIntent(mUri, mDisplayName, mBitmapData, mPhoneNumber,
                    mPhoneType, mShortcutAction);
        }
    }

    private void createContactShortcutIntent(Uri contactUri, String displayName,
            byte[] bitmapData) {
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

        final Bitmap icon = scaleToAppIconSize(framePhoto(bitmap));

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName);

        mListener.onShortcutIntentCreated(contactUri, intent);
    }

    private void createPhoneNumberShortcutIntent(Uri uri, String displayName, byte[] bitmapData,
            String phoneNumber, int phoneType, String shortcutAction) {
        Bitmap bitmap = null;
        if (bitmapData != null) {
            bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length, null);
        }

        Uri phoneUri;
        if (Intent.ACTION_CALL.equals(shortcutAction)) {
            // Make the URI a direct tel: URI so that it will always continue to work
            phoneUri = Uri.fromParts(Constants.SCHEME_TEL, phoneNumber, null);
            bitmap = generatePhoneNumberIcon(bitmap, phoneType, R.drawable.badge_action_call);
        } else {
            phoneUri = Uri.fromParts(Constants.SCHEME_SMSTO, phoneNumber, null);
            bitmap = generatePhoneNumberIcon(bitmap, phoneType, R.drawable.badge_action_sms);
        }

        Intent shortcutIntent = new Intent(shortcutAction, phoneUri);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName);

        mListener.onShortcutIntentCreated(uri, intent);
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

    /**
     * Generates a phone number shortcut icon. Adds an overlay describing the type of the phone
     * number, and if there is a photo also adds the call action icon.
     */
    private Bitmap generatePhoneNumberIcon(Bitmap photo, int phoneType, int actionResId) {
        final Resources r = mContext.getResources();
        boolean drawPhoneOverlay = true;
        final float scaleDensity = r.getDisplayMetrics().scaledDensity;

        Bitmap phoneIcon = ((BitmapDrawable) r.getDrawable(actionResId)).getBitmap();

        // If there isn't a photo use the generic phone action icon instead
        if (photo == null) {
            photo = phoneIcon;
            drawPhoneOverlay = false;
        }

        // Setup the drawing classes
        Bitmap icon = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        Rect dst = new Rect(0, 0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        // Create an overlay for the phone number type
        String overlay = null;
        switch (phoneType) {
            case Phone.TYPE_HOME:
                overlay = mContext.getString(R.string.type_short_home);
                break;

            case Phone.TYPE_MOBILE:
                overlay = mContext.getString(R.string.type_short_mobile);
                break;

            case Phone.TYPE_WORK:
                overlay = mContext.getString(R.string.type_short_work);
                break;

            case Phone.TYPE_PAGER:
                overlay = mContext.getString(R.string.type_short_pager);
                break;

            case Phone.TYPE_OTHER:
                overlay = mContext.getString(R.string.type_short_other);
                break;
        }

        if (overlay != null) {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            textPaint.setTextSize(20.0f * scaleDensity);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setColor(r.getColor(R.color.textColorIconOverlay));
            textPaint.setShadowLayer(3f, 1, 1, r.getColor(R.color.textColorIconOverlayShadow));
            canvas.drawText(overlay, 2 * scaleDensity, 16 * scaleDensity, textPaint);
        }

        // Draw the phone action icon as an overlay
        if (drawPhoneOverlay) {
            src.set(0, 0, phoneIcon.getWidth(), phoneIcon.getHeight());
            int iconWidth = icon.getWidth();
            dst.set(iconWidth - ((int) (20 * scaleDensity)), -1,
                    iconWidth, ((int) (19 * scaleDensity)));
            canvas.drawBitmap(phoneIcon, src, dst, photoPaint);
        }

        return icon;
    }
}
