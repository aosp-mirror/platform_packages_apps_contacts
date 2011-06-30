/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.calllog;

import com.android.contacts.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.VoicemailContract;

/**
 * Implementation of {@link VoicemailNotifier} that shows a notification in the status bar.
 */
public class DefaultVoicemailNotifier implements VoicemailNotifier {
    /** The tag used to identify notifications from this class. */
    private static final String NOTIFICATION_TAG = "DefaultVoicemailNotifier";
    /** The identifier of the notification of new voicemails. */
    private static final int NOTIFICATION_ID = 1;

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final VoicemailNumberQuery mVoicemailNumberQuery;
    private final NameLookupQuery mNameLookupQuery;

    public DefaultVoicemailNotifier(Context context, NotificationManager notificationManager,
            VoicemailNumberQuery voicemailNumberQuery, NameLookupQuery nameLookupQuery) {
        mContext = context;
        mNotificationManager = notificationManager;
        mVoicemailNumberQuery = voicemailNumberQuery;
        mNameLookupQuery = nameLookupQuery;
    }

    @Override
    public void notifyNewVoicemail(Uri uri) {
        // Lookup the number that left the voicemail.
        String number = mVoicemailNumberQuery.query(uri);
        // Lookup the name of the contact associated with this number.
        String name = mNameLookupQuery.query(number);
        // Show the name of the contact if available, falling back to using the number if not.
        String displayName = name == null ? number : name;
        Notification notification = new Notification.Builder(mContext)
                .setSmallIcon(android.R.drawable.stat_notify_voicemail)
                .setContentTitle(mContext.getString(R.string.notification_voicemail_title))
                .setContentText(displayName)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .getNotification();

        // Open the voicemail when clicking on the notification.
        notification.contentIntent =
                PendingIntent.getActivity(mContext, 0, new Intent(Intent.ACTION_VIEW, uri), 0);

        mNotificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification);
    }

    @Override
    public void clearNewVoicemailNotification() {
        mNotificationManager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
    }

    /** Allows determining the number associated with a given voicemail. */
    public interface VoicemailNumberQuery {
        /**
         * Returns the number associated with a voicemail URI, or null if the URI does not actually
         * correspond to a voicemail.
         *
         * @throws IllegalArgumentException if the given {@code uri} is not a voicemail URI.
         */
        public String query(Uri uri);
    }

    /** Create a new instance of {@link VoicemailNumberQuery}. */
    public static VoicemailNumberQuery createVoicemailNumberQuery(ContentResolver contentResolver) {
        return new DefaultVoicemailNumberQuery(contentResolver);
    }

    /**
     * Default implementation of {@link VoicemailNumberQuery} that looks up the number in the
     * voicemail content provider.
     */
    private static final class DefaultVoicemailNumberQuery implements VoicemailNumberQuery {
        private static final String[] PROJECTION = { VoicemailContract.Voicemails.NUMBER };
        private static final int NUMBER_COLUMN_INDEX = 0;

        private final ContentResolver mContentResolver;

        private DefaultVoicemailNumberQuery(ContentResolver contentResolver) {
            mContentResolver = contentResolver;
        }

        @Override
        public String query(Uri uri) {
            validateVoicemailUri(uri);
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(uri, PROJECTION, null, null, null);
                if (cursor.getCount() != 1) return null;
                if (!cursor.moveToFirst()) return null;
                return cursor.getString(NUMBER_COLUMN_INDEX);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        /**
         * Makes sure that the given URI is a valid voicemail URI.
         *
         * @throws IllegalArgumentException if the URI is not valid
         */
        private void validateVoicemailUri(Uri uri) {
            // Cannot be null.
            if (uri == null) throw new IllegalArgumentException("invalid voicemail URI");
            // Must have the right schema.
            if (!VoicemailContract.Voicemails.CONTENT_URI.getScheme().equals(uri.getScheme())) {
                throw new IllegalArgumentException("invalid voicemail URI");
            }
            // Must have the right authority.
            if (!VoicemailContract.AUTHORITY.equals(uri.getAuthority())) {
                throw new IllegalArgumentException("invalid voicemail URI");
            }
            // Must have a valid path.
            if (uri.getPath() == null) {
                throw new IllegalArgumentException("invalid voicemail URI");
            }
            // Must be a path within the voicemails table.
            if (!uri.getPath().startsWith(VoicemailContract.Voicemails.CONTENT_URI.getPath())) {
                throw new IllegalArgumentException("invalid voicemail URI");
            }
        }
    }

    /** Allows determining the name associated with a given phone number. */
    public interface NameLookupQuery {
        /**
         * Returns the name associated with the given number in the contacts database, or null if
         * the number does not correspond to any of the contacts.
         * <p>
         * If there are multiple contacts with the same phone number, it will return the name of one
         * of the matching contacts.
         */
        public String query(String number);
    }

    /** Create a new instance of {@link NameLookupQuery}. */
    public static NameLookupQuery createNameLookupQuery(ContentResolver contentResolver) {
        return new DefaultNameLookupQuery(contentResolver);
    }

    private static final class DefaultNameLookupQuery implements NameLookupQuery {
        private static final String[] PROJECTION = { PhoneLookup.DISPLAY_NAME };
        private static final int DISPLAY_NAME_COLUMN_INDEX = 0;

        private final ContentResolver mContentResolver;

        private DefaultNameLookupQuery(ContentResolver contentResolver) {
            mContentResolver = contentResolver;
        }

        @Override
        public String query(String number) {
            Cursor cursor = null;
            try {
                cursor = mContentResolver.query(
                        Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                        PROJECTION, null, null, null);
                if (!cursor.moveToFirst()) return null;
                return cursor.getString(DISPLAY_NAME_COLUMN_INDEX);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }
}
