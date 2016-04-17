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

package com.android.contacts.common.preference;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.commonbind.ObjectFactory;

import java.util.List;

/**
 * This fragment shows the preferences for "display options"
 */
public class DisplayOptionsPreferenceFragment extends PreferenceFragment {

    private static final String KEY_LIST_FORMAT = "list_format";
    private static final String KEY_SORT_ORDER = "sortOrder";
    private static final String KEY_DISPLAY_ORDER = "displayOrder";
    private static final String KEY_ABOUT = "about";
    private static final String KEY_MY_INFO = "my_info";
    private static final String KEY_CONTACTS_METADATA = "contacts_metadata";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_display_options);

        addLycheePreferences();

        final Preference aboutPreference = findPreference(KEY_ABOUT);
        aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ((ContactsPreferenceActivity) getActivity()).showAboutFragment();
                return true;
            }
        });

        removeUnsupportedPreferences();
    }

    private void removeUnsupportedPreferences() {
        final Resources resources = getResources();

        // List format
        final PreferenceCategory listFormatCategory =
                (PreferenceCategory) findPreference(KEY_LIST_FORMAT);

        // Disable sort order for CJK locales where it is not supported
        boolean isSortOrderRemoved = false;
        if (!resources.getBoolean(R.bool.config_sort_order_user_changeable)) {
            isSortOrderRemoved = true;
            listFormatCategory.removePreference(findPreference(KEY_SORT_ORDER));
        }

        // Disable display order for CJK locales as well
        if (!resources.getBoolean(R.bool.config_display_order_user_changeable)) {
            listFormatCategory.removePreference(findPreference(KEY_DISPLAY_ORDER));
            if (isSortOrderRemoved) {
                getPreferenceScreen().removePreference(listFormatCategory);
            }
        }

        // Remove the "Default account" setting if there aren't any writable accounts
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(getContext());
        final List<AccountWithDataSet> accounts = accountTypeManager.getAccounts(
                /* contactWritableOnly */ true);
        if (accounts.isEmpty()) {
            getPreferenceScreen().removePreference(
                    (PreferenceCategory) findPreference(KEY_MY_INFO));
        }
    }

    private void addLycheePreferences() {
        final PreferenceManager preferenceManager = ObjectFactory.getPreferenceManager(
                getContext());
        final PreferenceCategory metadataCategory =
                (PreferenceCategory) findPreference(KEY_CONTACTS_METADATA);
        if (preferenceManager != null && !preferenceManager.getPreferences().isEmpty()) {
            for (Preference preference : preferenceManager.getPreferences()) {
                metadataCategory.addPreference(preference);
            }
        } else {
            getPreferenceScreen().removePreference(metadataCategory);
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }
}

