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
import android.preference.PreferenceFragment;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.GoogleAccountType;
import com.android.contacts.commonbind.ObjectFactory;

import java.util.List;

/**
 * This fragment shows the preferences for "display options"
 */
public class DisplayOptionsPreferenceFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_display_options);

        removeUnsupportedPreferences();
        addExtraPreferences();

        final Preference aboutPreference = findPreference("about");
        aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ((ContactsPreferenceActivity) getActivity()).showAboutFragment();
                return true;
            }
        });
    }

    private void removeUnsupportedPreferences() {
        // Disable sort order for CJK locales where it is not supported
        final Resources resources = getResources();
        if (!resources.getBoolean(R.bool.config_sort_order_user_changeable)) {
            getPreferenceScreen().removePreference(findPreference("sortOrder"));
        }

        // Disable display order for CJK locales as well
        if (!resources.getBoolean(R.bool.config_display_order_user_changeable)) {
            getPreferenceScreen().removePreference(findPreference("displayOrder"));
        }

        // Remove the "Default account" setting if there aren't any writable accounts
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(getContext());
        final List<AccountWithDataSet> accounts = accountTypeManager.getAccounts(
                /* contactWritableOnly */ true);
        if (accounts.isEmpty()) {
            getPreferenceScreen().removePreference(findPreference("accounts"));
        }
    }

    private void addExtraPreferences() {
        final PreferenceManager preferenceManager = ObjectFactory.getPreferenceManager(
                getContext());
        if (preferenceManager != null) {
            for (Preference preference : preferenceManager.getPreferences()) {
                getPreferenceScreen().addPreference(preference);
            }
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }
}

