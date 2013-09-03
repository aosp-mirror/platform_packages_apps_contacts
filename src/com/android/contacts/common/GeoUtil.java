/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.common;

import android.content.Context;
import android.location.Country;
import android.location.CountryDetector;

import com.android.i18n.phonenumbers.NumberParseException;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.i18n.phonenumbers.Phonenumber;
import com.android.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder;

import java.util.Locale;

/**
 * Static methods related to Geo.
 */
public class GeoUtil {

    /**
     * @return The ISO 3166-1 two letters country code of the country the user
     *         is in.
     */
    public static String getCurrentCountryIso(Context context) {
        final CountryDetector detector =
                (CountryDetector) context.getSystemService(Context.COUNTRY_DETECTOR);
        if (detector != null) {
            final Country country = detector.detectCountry();
            if (country != null) {
                return country.getCountryIso();
            }
        }
        // Fallback to Locale if have issues with CountryDetector
        return Locale.getDefault().getCountry();
    }

    public static String getGeocodedLocationFor(Context context,  String phoneNumber) {
        final PhoneNumberOfflineGeocoder geocoder = PhoneNumberOfflineGeocoder.getInstance();
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        final CountryDetector countryDetector =
                (CountryDetector) context.getSystemService(Context.COUNTRY_DETECTOR);
        try {
            final Phonenumber.PhoneNumber structuredPhoneNumber =
                    phoneNumberUtil.parse(phoneNumber, getCurrentCountryIso(context));
            final Locale locale = context.getResources().getConfiguration().locale;
            return geocoder.getDescriptionForNumber(structuredPhoneNumber, locale);
        } catch (NumberParseException e) {
            return null;
        }
    }
}
