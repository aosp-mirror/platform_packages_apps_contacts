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

import android.content.Context;
import android.os.Bundle;
import android.app.ActionBar;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import com.android.contacts.common.R;

/**
 * Contacts settings.
 */
public final class ContactsPreferenceActivity extends PreferenceActivity {

    private static final String TAG_ABOUT_CONTACTS = "about_contacts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new DisplayOptionsPreferenceFragment())
                    .commit();
            setActivityTitle(R.string.activity_title_settings);
        } else {
            final AboutPreferenceFragment fragment = (AboutPreferenceFragment) getFragmentManager()
                    .findFragmentByTag(TAG_ABOUT_CONTACTS);
            setActivityTitle(fragment == null ?
                    R.string.activity_title_settings : R.string.setting_about);
        }
    }

    public void showAboutFragment() {
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new AboutPreferenceFragment(), TAG_ABOUT_CONTACTS)
                .addToBackStack(null)
                .commit();
        setActivityTitle(R.string.setting_about);
    }

    /**
     * Returns true if there are no preferences to display and therefore the
     * corresponding menu item can be removed.
     */
    public static boolean isEmpty(Context context) {
        return !context.getResources().getBoolean(R.bool.config_sort_order_user_changeable)
                && !context.getResources().getBoolean(R.bool.config_display_order_user_changeable)
                && !context.getResources().getBoolean(
                        R.bool.config_default_account_user_changeable);
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
}
