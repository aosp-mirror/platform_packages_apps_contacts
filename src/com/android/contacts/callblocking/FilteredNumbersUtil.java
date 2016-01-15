/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.callblocking;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.util.TelephonyManagerUtils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder;

import java.util.Locale;

/**
 * Utility to help with tasks related to filtered numbers.
 */
final public class FilteredNumbersUtil {

    private static final String TAG = "FilteredNumbersUtil";

    public interface CheckForSendToVoicemailContactListener {
        void onComplete(boolean hasSendToVoicemailContact);
    }

    public interface ImportSendToVoicemailContactsListener {
        void onImportComplete();
    }

    private static class ContactsQuery {
        static final String[] PROJECTION = {
            Contacts._ID
        };

        // TODO: as user can set "send to voicemail" for a contact that doesn't have a phone number,
        // if those are the only contacts that are marked as "send to voicemail", then when you view
        // numbers it'll be blank. We should also
        static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";

        static final int ID_COLUMN_INDEX = 0;
    }

    public static class PhoneQuery {
        static final String[] PROJECTION = {
            Contacts._ID,
            Phone.NORMALIZED_NUMBER,
            Phone.NUMBER
        };

        static final int ID_COLUMN_INDEX = 0;
        static final int NORMALIZED_NUMBER_COLUMN_INDEX = 1;
        static final int NUMBER_COLUMN_INDEX = 2;

        static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";
    }

    /**
     * Checks if there exists a contact with {@code Contacts.SEND_TO_VOICEMAIL} set to true.
     */
    public static void checkForSendToVoicemailContact(
            final Context context, final CheckForSendToVoicemailContactListener listener) {
        final AsyncTask task = new AsyncTask<Object, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Object[]  params) {
                if (context == null) {
                    return false;
                }

                final Cursor cursor = context.getContentResolver().query(
                        Contacts.CONTENT_URI,
                        ContactsQuery.PROJECTION,
                        ContactsQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null,
                        null);

                boolean hasSendToVoicemailContacts = false;
                if (cursor != null) {
                    try {
                        hasSendToVoicemailContacts = cursor.getCount() > 0;
                    } finally {
                        cursor.close();
                    }
                }

                return hasSendToVoicemailContacts;
            }

            @Override
            public void onPostExecute(Boolean hasSendToVoicemailContact) {
                if (listener != null) {
                    listener.onComplete(hasSendToVoicemailContact);
                }
            }
        };
        task.execute();
    }

    /**
     * Blocks all the phone numbers of any contacts marked as SEND_TO_VOICEMAIL, then clears the
     * SEND_TO_VOICEMAIL flag on those contacts.
     */
    public static void importSendToVoicemailContacts(
            final Context context, final ImportSendToVoicemailContactsListener listener) {
        final FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(context.getContentResolver());

        final AsyncTask<Object, Void, Boolean> task = new AsyncTask<Object, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Object[] params) {
                if (context == null) {
                    return false;
                }

                // Get the phone number of contacts marked as SEND_TO_VOICEMAIL.
                final Cursor phoneCursor = context.getContentResolver().query(
                        Phone.CONTENT_URI,
                        PhoneQuery.PROJECTION,
                        PhoneQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null,
                        null);

                if (phoneCursor == null) {
                    return false;
                }

                try {
                    while (phoneCursor.moveToNext()) {
                        final String normalizedNumber = phoneCursor.getString(
                                PhoneQuery.NORMALIZED_NUMBER_COLUMN_INDEX);
                        final String number = phoneCursor.getString(
                                PhoneQuery.NUMBER_COLUMN_INDEX);
                        if (normalizedNumber != null) {
                            // Block the phone number of the contact.
                            mFilteredNumberAsyncQueryHandler.blockNumber(
                                    null, normalizedNumber, number, null);
                        }
                    }
                } finally {
                    phoneCursor.close();
                }

                // Clear SEND_TO_VOICEMAIL on all contacts. The setting has been imported to Dialer.
                ContentValues newValues = new ContentValues();
                newValues.put(Contacts.SEND_TO_VOICEMAIL, 0);
                context.getContentResolver().update(
                        Contacts.CONTENT_URI,
                        newValues,
                        ContactsQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null);

                return true;
            }

            @Override
            public void onPostExecute(Boolean success) {
                if (success) {
                    if (listener != null) {
                        listener.onImportComplete();
                    }
                } else if (context != null) {
                    String toastStr = context.getString(R.string.send_to_voicemail_import_failed);
                    Toast.makeText(context, toastStr, Toast.LENGTH_SHORT).show();
                }
            }
        };
        task.execute();
    }

    public static boolean canBlockNumber(Context context, String number, String countryIso) {
        final String normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        return !TextUtils.isEmpty(normalizedNumber)
                && !PhoneNumberUtils.isEmergencyNumber(normalizedNumber);
    }

    /**
     * @return a geographical description string for the specified number.
     * @see com.android.i18n.phonenumbers.PhoneNumberOfflineGeocoder
     *
     * Copied from com.android.dialer.util.PhoneNumberUtil.getGeoDescription(mContext, info.number);
     */
    public static String getGeoDescription(Context context, String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }

        com.google.i18n.phonenumbers.PhoneNumberUtil util =
                com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        PhoneNumberOfflineGeocoder geocoder = PhoneNumberOfflineGeocoder.getInstance();

        Locale locale = context.getResources().getConfiguration().locale;
        String countryIso = TelephonyManagerUtils.getCurrentCountryIso(context, locale);
        Phonenumber.PhoneNumber pn = null;
        try {
            pn = util.parse(number, countryIso);
        } catch (NumberParseException e) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "getGeoDescription: NumberParseException for incoming number '" +
                        number + "'");
            }
        }

        if (pn != null) {
            String description = geocoder.getDescriptionForNumber(pn, locale);
            return description;
        }

        return null;
    }
}
