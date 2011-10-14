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
 * limitations under the License.
 */

package com.android.contacts.calllog;

import com.android.contacts.util.UriUtils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

/**
 * Utility class to look up the contact information for a given number.
 */
public class ContactInfoHelper {
    private final Context mContext;
    private final String mCurrentCountryIso;

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
    public ContactInfo lookupNumber(String number, String countryIso) {
        final ContactInfo info;

        // Determine the contact info.
        if (PhoneNumberUtils.isUriNumber(number)) {
            // This "number" is really a SIP address.
            ContactInfo sipInfo = queryContactInfoForSipAddress(number);
            if (sipInfo == null || sipInfo == ContactInfo.EMPTY) {
                // Check whether the username is actually a phone number of contact.
                String username = number.substring(0, number.indexOf('@'));
                if (PhoneNumberUtils.isGlobalPhoneNumber(username)) {
                    sipInfo = queryContactInfoForPhoneNumber(username, countryIso);
                }
            }
            info = sipInfo;
        } else {
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
            } else {
                updatedInfo = info;
            }
        }
        return updatedInfo;
    }

    /**
     * Determines the contact information for the given SIP address.
     * <p>
     * It returns the contact info if found.
     * <p>
     * If no contact corresponds to the given SIP address, returns {@link ContactInfo#EMPTY}.
     * <p>
     * If the lookup fails for some other reason, it returns null.
     */
    private ContactInfo queryContactInfoForSipAddress(String sipAddress) {
        final ContactInfo info;

        // TODO: This code is duplicated from the
        // CallerInfoAsyncQuery class.  To avoid that, could the
        // code here just use CallerInfoAsyncQuery, rather than
        // manually running ContentResolver.query() itself?

        // We look up SIP addresses directly in the Data table:
        Uri contactRef = Data.CONTENT_URI;

        // Note Data.DATA1 and SipAddress.SIP_ADDRESS are equivalent.
        //
        // Also note we use "upper(data1)" in the WHERE clause, and
        // uppercase the incoming SIP address, in order to do a
        // case-insensitive match.
        //
        // TODO: May also need to normalize by adding "sip:" as a
        // prefix, if we start storing SIP addresses that way in the
        // database.
        String selection = "upper(" + Data.DATA1 + ")=?"
                + " AND "
                + Data.MIMETYPE + "='" + SipAddress.CONTENT_ITEM_TYPE + "'";
        String[] selectionArgs = new String[] { sipAddress.toUpperCase() };

        Cursor dataTableCursor =
                mContext.getContentResolver().query(
                        contactRef,
                        null,  // projection
                        selection,  // selection
                        selectionArgs,  // selectionArgs
                        null);  // sortOrder

        if (dataTableCursor != null) {
            if (dataTableCursor.moveToFirst()) {
                info = new ContactInfo();

                // TODO: we could slightly speed this up using an
                // explicit projection (and thus not have to do
                // those getColumnIndex() calls) but the benefit is
                // very minimal.

                // Note the Data.CONTACT_ID column here is
                // equivalent to the PERSON_ID_COLUMN_INDEX column
                // we use with "phonesCursor" below.
                long contactId = dataTableCursor.getLong(
                        dataTableCursor.getColumnIndex(Data.CONTACT_ID));
                String lookupKey = dataTableCursor.getString(
                        dataTableCursor.getColumnIndex(Data.LOOKUP_KEY));
                info.lookupUri = Contacts.getLookupUri(contactId, lookupKey);
                info.name = dataTableCursor.getString(
                        dataTableCursor.getColumnIndex(Data.DISPLAY_NAME));
                // "type" and "label" are currently unused for SIP addresses
                info.type = SipAddress.TYPE_OTHER;
                info.label = null;

                // And "number" is the SIP address.
                // Note Data.DATA1 and SipAddress.SIP_ADDRESS are equivalent.
                info.number = dataTableCursor.getString(dataTableCursor.getColumnIndex(Data.DATA1));
                info.normalizedNumber = null;  // meaningless for SIP addresses
                info.photoId = dataTableCursor.getLong(
                        dataTableCursor.getColumnIndex(Data.PHOTO_ID));
                info.photoUri = UriUtils.parseUriOrNull(dataTableCursor.getString(
                        dataTableCursor.getColumnIndex(Data.PHOTO_URI)));
                info.formattedNumber = null;  // meaningless for SIP addresses
            } else {
                info = ContactInfo.EMPTY;
            }
            dataTableCursor.close();
        } else {
            // Failed to fetch the data, ignore this request.
            info = null;
        }
        return info;
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
        final ContactInfo info;

        String contactNumber = number;
        if (!TextUtils.isEmpty(countryIso)) {
            // Normalize the number: this is needed because the PhoneLookup query below does not
            // accept a country code as an input.
            String numberE164 = PhoneNumberUtils.formatNumberToE164(number, countryIso);
            if (!TextUtils.isEmpty(numberE164)) {
                // Only use it if the number could be formatted to E164.
                contactNumber = numberE164;
            }
        }

        // "contactNumber" is a regular phone number, so use the
        // PhoneLookup table:
        Cursor phonesCursor =
                mContext.getContentResolver().query(
                    Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(contactNumber)),
                            PhoneQuery._PROJECTION, null, null, null);

        if (phonesCursor != null) {
            if (phonesCursor.moveToFirst()) {
                info = new ContactInfo();
                long contactId = phonesCursor.getLong(PhoneQuery.PERSON_ID);
                String lookupKey = phonesCursor.getString(PhoneQuery.LOOKUP_KEY);
                info.lookupUri = Contacts.getLookupUri(contactId, lookupKey);
                info.name = phonesCursor.getString(PhoneQuery.NAME);
                info.type = phonesCursor.getInt(PhoneQuery.PHONE_TYPE);
                info.label = phonesCursor.getString(PhoneQuery.LABEL);
                info.number = phonesCursor.getString(PhoneQuery.MATCHED_NUMBER);
                info.normalizedNumber = phonesCursor.getString(PhoneQuery.NORMALIZED_NUMBER);
                info.photoId = phonesCursor.getLong(PhoneQuery.PHOTO_ID);
                info.photoUri =
                        UriUtils.parseUriOrNull(phonesCursor.getString(PhoneQuery.PHOTO_URI));
                info.formattedNumber = formatPhoneNumber(number, null, countryIso);

            } else {
                info = ContactInfo.EMPTY;
            }
            phonesCursor.close();
        } else {
            // Failed to fetch the data, ignore this request.
            info = null;
        }
        return info;
    }

    /**
     * Format the given phone number
     *
     * @param number the number to be formatted.
     * @param normalizedNumber the normalized number of the given number.
     * @param countryIso the ISO 3166-1 two letters country code, the country's
     *        convention will be used to format the number if the normalized
     *        phone is null.
     *
     * @return the formatted number, or the given number if it was formatted.
     */
    private String formatPhoneNumber(String number, String normalizedNumber,
            String countryIso) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        // If "number" is really a SIP address, don't try to do any formatting at all.
        if (PhoneNumberUtils.isUriNumber(number)) {
            return number;
        }
        if (TextUtils.isEmpty(countryIso)) {
            countryIso = mCurrentCountryIso;
        }
        return PhoneNumberUtils.formatNumber(number, normalizedNumber, countryIso);
    }
}
