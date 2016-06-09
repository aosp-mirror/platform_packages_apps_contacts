/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.ActionBar;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.QuickContact;
import android.text.TextUtils;
import android.view.MenuItem;

import com.android.contacts.common.R;
import com.android.contacts.common.list.ProviderStatusWatcher;
import com.android.contacts.common.preference.DisplayOptionsPreferenceFragment.ProfileListener;
import com.android.contacts.common.preference.DisplayOptionsPreferenceFragment.ProfileQuery;

/**
 * Contacts settings.
 */
public final class ContactsPreferenceActivity extends PreferenceActivity implements
        ProfileListener {

    private static final String TAG_ABOUT = "about_contacts";
    private static final String TAG_DISPLAY_OPTIONS = "display_options";

    private String mNewLocalProfileExtra;
    private String mPreviousScreenExtra;
    private int mModeFullyExpanded;
    private boolean mAreContactsAvailable;

    private ProviderStatusWatcher mProviderStatusWatcher;

    public static final String EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile";
    public static final String EXTRA_MODE_FULLY_EXPANDED = "modeFullyExpanded";
    public static final String EXTRA_PREVIOUS_SCREEN_TYPE = "previousScreenType";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }

        mProviderStatusWatcher = ProviderStatusWatcher.getInstance(this);

        mNewLocalProfileExtra = getIntent().getStringExtra(EXTRA_NEW_LOCAL_PROFILE);
        mModeFullyExpanded = getIntent().getIntExtra(EXTRA_MODE_FULLY_EXPANDED,
                QuickContact.MODE_LARGE);
        mPreviousScreenExtra = getIntent().getStringExtra(EXTRA_PREVIOUS_SCREEN_TYPE);
        final int providerStatus = mProviderStatusWatcher.getProviderStatus();
        mAreContactsAvailable = providerStatus == ProviderStatus.STATUS_NORMAL;

        if (savedInstanceState == null) {
            final DisplayOptionsPreferenceFragment fragment = DisplayOptionsPreferenceFragment
                    .newInstance(mNewLocalProfileExtra, mPreviousScreenExtra, mModeFullyExpanded,
                            mAreContactsAvailable);
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment, TAG_DISPLAY_OPTIONS)
                    .commit();
            setActivityTitle(R.string.activity_title_settings);
        } else {
            final AboutPreferenceFragment aboutFragment = (AboutPreferenceFragment)
                    getFragmentManager().findFragmentByTag(TAG_ABOUT);
            setActivityTitle(aboutFragment == null ?
                    R.string.activity_title_settings : R.string.setting_about);
        }
    }

    public void showAboutFragment() {
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, AboutPreferenceFragment.newInstance(), TAG_ABOUT)
                .addToBackStack(null)
                .commit();
        setActivityTitle(R.string.setting_about);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            setActivityTitle(R.string.activity_title_settings);
            getFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    private void setActivityTitle(int res) {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(res);
        }
    }

    @Override
    public void onProfileLoaded(Cursor cursor) {
        boolean hasProfile = false;
        String displayName = null;
        long contactId = -1;
        if (cursor != null && cursor.moveToFirst()) {
            hasProfile = cursor.getInt(ProfileQuery.CONTACT_IS_USER_PROFILE) == 1;
            displayName = cursor.getString(ProfileQuery.CONTACT_DISPLAY_NAME);
            contactId = cursor.getLong(ProfileQuery.CONTACT_ID);
        }
        if (hasProfile && TextUtils.isEmpty(displayName)) {
            displayName = getString(R.string.missing_name);
        }
        final DisplayOptionsPreferenceFragment fragment = (DisplayOptionsPreferenceFragment)
                getFragmentManager().findFragmentByTag(TAG_DISPLAY_OPTIONS);
        fragment.updateMyInfoPreference(hasProfile, displayName, contactId);
    }
}
