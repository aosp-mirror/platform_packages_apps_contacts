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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.UriUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility class to look up the contact information for a given number.
 */
public class ContactInfoHelper {
    private final Context mContext;
    private final String mCurrentCountryIso;

    /**
     * The queries to look up the {@link ContactInfo} for a given number in the Call Log.
     */
    private static final class PhoneQuery {

        /**
         * Projection to look up the ContactInfo. Does not include DISPLAY_NAME_ALTERNATIVE as that
         * column isn't available in ContactsCommon.PhoneLookup
         */
        public static final String[] PHONE_LOOKUP_PROJECTION = new String[] {
                PhoneLookup._ID,
                PhoneLookup.DISPLAY_NAME,
                PhoneLookup.TYPE,
                PhoneLookup.LABEL,
                PhoneLookup.NUMBER,
                PhoneLookup.NORMALIZED_NUMBER,
                PhoneLookup.PHOTO_ID,
                PhoneLookup.LOOKUP_KEY,
                PhoneLookup.PHOTO_URI};

        public static final int PERSON_ID = 0;
        public static final int NAME = 1;
        public static final int PHONE_TYPE = 2;
        public static final int LABEL = 3;
        public static final int MATCHED_NUMBER = 4;
        public static final int NORMALIZED_NUMBER = 5;
        public static final int PHOTO_ID = 6;
        public static final int LOOKUP_KEY = 7;
        public static final int PHOTO_URI = 8;
    }

    private static final class NameAlternativeQuery {
        /**
         * Projection to look up a contact's DISPLAY_NAME_ALTERNATIVE
         */
        public static final String[] DISPLAY_NAME_PROJECTION = new String[] {
                Contacts.DISPLAY_NAME_ALTERNATIVE,
        };

        public static final int NAME = 0;
    }

    public ContactInfoHelper(Context context, String currentCountryIso) {
        mContext = context;
        mCurrentCountryIso = currentCountryIso;
    }

    /**
     * Returns the contact information for the given number.
     * <p>
     * If the number does not match any contact, returns a contact info containing only the number
     * and the formatted number.
     * <p>
     * If an error occurs during the lookup, it returns null.
     *
     * @param number the number to look up
     * @param countryIso the country associated with this number
     */
    @Nullable
    public ContactInfo lookupNumber(String number, String countryIso) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }

        ContactInfo info;

        if (PhoneNumberHelper.isUriNumber(number)) {
            // The number is a SIP address..
            info = lookupContactFromUri(getContactInfoLookupUri(number));
            if (info == null || info == ContactInfo.EMPTY) {
                // If lookup failed, check if the "username" of the SIP address is a phone number.
                String username = PhoneNumberHelper.getUsernameFromUriNumber(number);
                if (PhoneNumberUtils.isGlobalPhoneNumber(username)) {
                    info = queryContactInfoForPhoneNumber(username, countryIso);
                }
            }
        } else {
            // Look for a contact that has the given phone number.
            info = queryContactInfoForPhoneNumber(number, countryIso);
        }

        final ContactInfo updatedInfo;
        if (info == null) {
            // The lookup failed.
            updatedInfo = null;
        } else {
            // If we did not find a matching contact, generate an empty contact info for the number.
            if (info == ContactInfo.EMPTY) {
                // Did not find a matching contact.
                updatedInfo = new ContactInfo();
                updatedInfo.number = number;
                updatedInfo.formattedNumber = formatPhoneNumber(number, null, countryIso);
                updatedInfo.normalizedNumber = PhoneNumberUtils.formatNumberToE164(
                        number, countryIso);
                updatedInfo.lookupUri = createTemporaryContactUri(updatedInfo.formattedNumber);
            } else {
                updatedInfo = info;
            }
        }
        return updatedInfo;
    }

    /**
     * Creates a JSON-encoded lookup uri for a unknown number without an associated contact
     *
     * @param number - Unknown phone number
     * @return JSON-encoded URI that can be used to perform a lookup when clicking on the quick
     *         contact card.
     */
    private static Uri createTemporaryContactUri(String number) {
        try {
            final JSONObject contactRows = new JSONObject().put(Phone.CONTENT_ITEM_TYPE,
                    new JSONObject().put(Phone.NUMBER, number).put(Phone.TYPE, Phone.TYPE_CUSTOM));

            final String jsonString = new JSONObject().put(Contacts.DISPLAY_NAME, number)
                    .put(Contacts.DISPLAY_NAME_SOURCE, DisplayNameSources.PHONE)
                    .put(Contacts.CONTENT_ITEM_TYPE, contactRows).toString();

            return Contacts.CONTENT_LOOKUP_URI
                    .buildUpon()
                    .appendPath(Constants.LOOKUP_URI_ENCODED)
                    .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                            String.valueOf(Long.MAX_VALUE))
                    .encodedFragment(jsonString)
                    .build();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Looks up a contact using the given URI.
     * <p>
     * It returns null if an error occurs, {@link ContactInfo#EMPTY} if no matching contact is
     * found, or the {@link ContactInfo} for the given contact.
     * <p>
     * The {@link ContactInfo#formattedNumber} field is always set to {@code null} in the returned
     * value.
     */
    public ContactInfo lookupContactFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        if (!PermissionsUtil.hasContactsPermissions(mContext)) {
            return ContactInfo.EMPTY;
        }

        Cursor phoneLookupCursor = null;
        try {
            phoneLookupCursor = mContext.getContentResolver().query(uri,
                    PhoneQuery.PHONE_LOOKUP_PROJECTION, null, null, null);
        } catch (NullPointerException e) {
            // Trap NPE from pre-N CP2
            return null;
        }
        if (phoneLookupCursor == null) {
            return null;
        }

        try {
            if (!phoneLookupCursor.moveToFirst()) {
                return ContactInfo.EMPTY;
            }
            String lookupKey = phoneLookupCursor.getString(PhoneQuery.LOOKUP_KEY);
            ContactInfo contactInfo = createPhoneLookupContactInfo(phoneLookupCursor, lookupKey);
            contactInfo.nameAlternative = lookUpDisplayNameAlternative(mContext, lookupKey);
            return contactInfo;
        } finally {
            phoneLookupCursor.close();
        }
    }

    private ContactInfo createPhoneLookupContactInfo(Cursor phoneLookupCursor, String lookupKey) {
        ContactInfo info = new ContactInfo();
        info.lookupKey = lookupKey;
        info.lookupUri = Contacts.getLookupUri(phoneLookupCursor.getLong(PhoneQuery.PERSON_ID),
                lookupKey);
        info.name = phoneLookupCursor.getString(PhoneQuery.NAME);
        info.type = phoneLookupCursor.getInt(PhoneQuery.PHONE_TYPE);
        info.label = phoneLookupCursor.getString(PhoneQuery.LABEL);
        info.number = phoneLookupCursor.getString(PhoneQuery.MATCHED_NUMBER);
        info.normalizedNumber = phoneLookupCursor.getString(PhoneQuery.NORMALIZED_NUMBER);
        info.photoId = phoneLookupCursor.getLong(PhoneQuery.PHOTO_ID);
        info.photoUri = UriUtils.parseUriOrNull(phoneLookupCursor.getString(PhoneQuery.PHOTO_URI));
        info.formattedNumber = null;
        // TODO: pass in directory ID rather than null, and make sure it works with work profiles.
        info.userType = ContactsUtils.determineUserType(null,
                phoneLookupCursor.getLong(PhoneQuery.PERSON_ID));
        return info;
    }

    public static String lookUpDisplayNameAlternative(Context context, String lookupKey) {
        if (lookupKey == null) {
            return null;
        }

        final Uri uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    NameAlternativeQuery.DISPLAY_NAME_PROJECTION, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(NameAlternativeQuery.NAME);
            }
        } catch (IllegalArgumentException e) {
            // Thrown for work profile queries. For those, we don't support
            // alternative display names.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    /**
     * Determines the contact information for the given phone number.
     * <p>
     * It returns the contact info if found.
     * <p>
     * If no contact corresponds to the given phone number, returns {@link ContactInfo#EMPTY}.
     * <p>
     * If the lookup fails for some other reason, it returns null.
     */
    private ContactInfo queryContactInfoForPhoneNumber(String number, String countryIso) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }

        ContactInfo info = lookupContactFromUri(getContactInfoLookupUri(number));
        if (info != null && info != ContactInfo.EMPTY) {
            info.formattedNumber = formatPhoneNumber(number, null, countryIso);
        }
        return info;
    }

    /**
     * Format the given phone number
     *
     * @param number the number to be formatted.
     * @param normalizedNumber the normalized number of the given number.
     * @param countryIso the ISO 3166-1 two letters country code, the country's convention will be
     *        used to format the number if the normalized phone is null.
     *
     * @return the formatted number, or the given number if it was formatted.
     */
    private String formatPhoneNumber(String number, String normalizedNumber, String countryIso) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        // If "number" is really a SIP address, don't try to do any formatting at all.
        if (com.android.contacts.common.util.PhoneNumberHelper.isUriNumber(number)) {
            return number;
        }
        if (TextUtils.isEmpty(countryIso)) {
            countryIso = mCurrentCountryIso;
        }
        return PhoneNumberUtils.formatNumber(number, normalizedNumber, countryIso);
    }


    public static Uri getContactInfoLookupUri(String number) {
        return getContactInfoLookupUri(number, -1);
    }

    public static Uri getContactInfoLookupUri(String number, long directoryId) {
        // Get URI for the number in the PhoneLookup table, with a parameter to indicate whether
        // the number is a SIP number.
        Uri uri = PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI;
        if (!ContactsUtils.FLAG_N_FEATURE) {
            if (directoryId != -1) {
                // ENTERPRISE_CONTENT_FILTER_URI in M doesn't support directory lookup
                uri = PhoneLookup.CONTENT_FILTER_URI;
            } else {
                // b/25900607 in M. PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, encodes twice.
                number = Uri.encode(number);
            }
        }
        Uri.Builder builder = uri.buildUpon()
                .appendPath(number)
                .appendQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
                        String.valueOf(PhoneNumberHelper.isUriNumber(number)));
        if (directoryId != -1) {
            builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                    String.valueOf(directoryId));
        }
        return builder.build();
    }
}
