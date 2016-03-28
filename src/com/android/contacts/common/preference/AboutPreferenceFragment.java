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

package com.android.contacts.common.preference;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.android.contacts.common.R;
import com.android.contacts.common.activity.LicenseActivity;

/**
 * This fragment shows the preferences for "about".
 */
public class AboutPreferenceFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_about);

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

