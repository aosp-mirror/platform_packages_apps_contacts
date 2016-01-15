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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import com.android.contacts.common.R;
import com.android.contacts.common.activity.LicenseActivity;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;

import java.util.List;

/**
 * This fragment shows the preferences for the first header.
 */
public class DisplayOptionsPreferenceFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_display_options);

        // Remove "Default account" setting if no writable accounts.
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(getContext());
        final List<AccountWithDataSet> accounts = accountTypeManager.getAccounts(
                /* contactWritableOnly */ true);
        if (accounts.isEmpty()) {
            final PreferenceScreen preferenceScreen = getPreferenceScreen();
            preferenceScreen.removePreference((ListPreference) findPreference("accounts"));
        }

        // STOPSHIP Show this option when 1) metadata sync is enabled and 2) at least one
        // focus google account.
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removePreference((ListPreference) findPreference("contactMetadata"));

        // Set build version of Contacts App.
        final PackageManager manager = getActivity().getPackageManager();
        try {
            final PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);
            final Preference versionPreference = findPreference(
                    getString(R.string.pref_build_version_key));
            versionPreference.setSummary(info.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            // Nothing
        }

        final Preference licensePreference = findPreference(
                getString(R.string.pref_open_source_licenses_key));
        licensePreference.setIntent(new Intent(getActivity(), LicenseActivity.class));
    }

    @Override
    public Context getContext() {
        return getActivity();
    }
}

