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

package com.android.contacts.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Utility class to save and restore the locale of the system.
 * <p>
 * This can be used for tests that assume to be run in a certain locale, e.g., because they
 * check against strings in a particular language or require an assumption on how the system
 * will behave in a specific locale.
 * <p>
 * In your test, you can change the locale with the following code:
 * <pre>
 * public class CanadaFrenchTest extends AndroidTestCase {
 *     private LocaleTestUtils mLocaleTestUtils;
 *
 *     &#64;Override
 *     public void setUp() throws Exception {
 *         super.setUp();
 *         mLocaleTestUtils = new LocaleTestUtils(getContext());
 *         mLocaleTestUtils.setLocale(Locale.CANADA_FRENCH);
 *     }
 *
 *     &#64;Override
 *     public void tearDown() throws Exception {
 *         mLocaleTestUtils.restoreLocale();
 *         mLocaleTestUtils = null;
 *         super.tearDown();
 *     }
 *
 *     ...
 * }
 * </pre>
 * Note that one should not call {@link #setLocale(Locale)} more than once without calling
 * {@link #restoreLocale()} first.
 * <p>
 * This class is not thread-safe. Usually its methods should be invoked only from the test thread.
 */
public class LocaleTestUtils {
    private final Context mContext;
    private boolean mSaved;
    private Locale mSavedContextLocale;
    private Locale mSavedSystemLocale;

    /**
     * Create a new instance that can be used to set and reset the locale for the given context.
     *
     * @param context the context on which to alter the locale
     */
    public LocaleTestUtils(Context context) {
        mContext = context;
        mSaved = false;
    }

    /**
     * Set the locale to the given value and saves the previous value.
     *
     * @param locale the value to which the locale should be set
     * @throws IllegalStateException if the locale was already set
     */
    public void setLocale(Locale locale) {
        if (mSaved) {
            throw new IllegalStateException(
                    "call restoreLocale() before calling setLocale() again");
        }
        mSavedContextLocale = setResourcesLocale(mContext.getResources(), locale);
        mSavedSystemLocale = setResourcesLocale(Resources.getSystem(), locale);
        mSaved = true;
    }

    /**
     * Restores the previously set locale.
     *
     * @throws IllegalStateException if the locale was not set using {@link #setLocale(Locale)}
     */
    public void restoreLocale() {
        if (!mSaved) {
            throw new IllegalStateException("call setLocale() before calling restoreLocale()");
        }
        setResourcesLocale(mContext.getResources(), mSavedContextLocale);
        setResourcesLocale(Resources.getSystem(), mSavedSystemLocale);
        mSaved = false;
    }

    /**
     * Sets the locale for the given resources and returns the previous locale.
     *
     * @param resources the resources on which to set the locale
     * @param locale the value to which to set the locale
     * @return the previous value of the locale for the resources
     */
    private Locale setResourcesLocale(Resources resources, Locale locale) {
        Configuration contextConfiguration = new Configuration(resources.getConfiguration());
        Locale savedLocale = contextConfiguration.locale;
        contextConfiguration.locale = locale;
        resources.updateConfiguration(contextConfiguration, null);
        return savedLocale;
    }
}