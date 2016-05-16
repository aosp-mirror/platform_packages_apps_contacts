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

import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Profile;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.commonbind.ObjectFactory;

import java.util.List;

/**
 * This fragment shows the preferences for "display options"
 */
public class DisplayOptionsPreferenceFragment extends PreferenceFragment {

    private static final String ARG_NEW_LOCAL_PROFILE = "new_local_profile";
    private static final String ARG_MODE_FULLY_EXPANDED = "mode_fully_expanded";

    private static final String KEY_ABOUT = "about";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_DISPLAY_ORDER = "displayOrder";
    private static final String KEY_MY_INFO = "myInfo";
    private static final String KEY_SORT_ORDER = "sortOrder";

    private static final int LOADER_PROFILE = 0;

    /**
     * Callbacks for hosts of the {@link DisplayOptionsPreferenceFragment}.
     */
    public interface ProfileListener  {
        /**
         * Invoked after profile has been loaded.
         */
        void onProfileLoaded(Cursor data);
    }

    /**
     * The projections that are used to obtain user profile
     */
    public static class ProfileQuery {
        /**
         * Not instantiable.
         */
        private ProfileQuery() {}

        private static final String[] PROFILE_PROJECTION_PRIMARY = new String[] {
                Contacts._ID,                           // 0
                Contacts.DISPLAY_NAME_PRIMARY,          // 1
                Contacts.IS_USER_PROFILE,               // 2
        };

        private static final String[] PROFILE_PROJECTION_ALTERNATIVE = new String[] {
                Contacts._ID,                           // 0
                Contacts.DISPLAY_NAME_ALTERNATIVE,      // 1
                Contacts.IS_USER_PROFILE,               // 2
        };

        public static final int CONTACT_ID               = 0;
        public static final int CONTACT_DISPLAY_NAME     = 1;
        public static final int CONTACT_IS_USER_PROFILE  = 2;
    }

    private String mNewLocalProfileExtra;
    private int mModeFullyExpanded;

    private Preference mMyInfoPreference;

    private ProfileListener mListener;

    private final LoaderManager.LoaderCallbacks<Cursor> mProfileLoaderListener =
            new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            final CursorLoader loader = createCursorLoader(getContext());
            loader.setUri(Profile.CONTENT_URI);
            loader.setProjection(getProjection(getContext()));
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (mListener != null) {
                mListener.onProfileLoaded(data);
            }
        }

        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    public static DisplayOptionsPreferenceFragment newInstance(String newLocalProfileExtra,
            int modeFullyExpanded) {
        final DisplayOptionsPreferenceFragment fragment = new DisplayOptionsPreferenceFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_NEW_LOCAL_PROFILE, newLocalProfileExtra);
        args.putInt(ARG_MODE_FULLY_EXPANDED, modeFullyExpanded);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_display_options);

        removeUnsupportedPreferences();
        addExtraPreferences();

        final Bundle args = getArguments();
        mNewLocalProfileExtra = args.getString(ARG_NEW_LOCAL_PROFILE);
        mModeFullyExpanded = args.getInt(ARG_MODE_FULLY_EXPANDED);

        mMyInfoPreference = findPreference(KEY_MY_INFO);

        final Preference aboutPreference = findPreference(KEY_ABOUT);
        aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ((ContactsPreferenceActivity) getActivity()).showAboutFragment();
                return true;
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().restartLoader(LOADER_PROFILE, null, mProfileLoaderListener);
    }

    public void updateMyInfoPreference(final boolean hasProfile, String displayName,
            final long contactId) {
        final CharSequence summary = hasProfile ? getString(R.string.me_contact_name, displayName)
                : getString(R.string.set_up_profile);
        mMyInfoPreference.setSummary(summary);
        mMyInfoPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Intent intent;
                if (hasProfile) {
                    final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    intent = ImplicitIntentsUtil.composeQuickContactIntent(uri, mModeFullyExpanded);
                } else {
                    intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                    intent.putExtra(mNewLocalProfileExtra, true);
                }
                ImplicitIntentsUtil.startActivityInApp(getActivity(), intent);
                return true;
            }
        });
    }

    private void removeUnsupportedPreferences() {
        // Disable sort order for CJK locales where it is not supported
        final Resources resources = getResources();
        if (!resources.getBoolean(R.bool.config_sort_order_user_changeable)) {
            getPreferenceScreen().removePreference(findPreference(KEY_SORT_ORDER));
        }

        // Disable display order for CJK locales as well
        if (!resources.getBoolean(R.bool.config_display_order_user_changeable)) {
            getPreferenceScreen().removePreference(findPreference(KEY_DISPLAY_ORDER));
        }

        // Remove the "Default account" setting if there aren't any writable accounts
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(getContext());
        final List<AccountWithDataSet> accounts = accountTypeManager.getAccounts(
                /* contactWritableOnly */ true);
        if (accounts.isEmpty()) {
            getPreferenceScreen().removePreference(findPreference(KEY_ACCOUNTS));
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

    private CursorLoader createCursorLoader(Context context) {
        return new CursorLoader(context) {
            @Override
            protected Cursor onLoadInBackground() {
                try {
                    return super.onLoadInBackground();
                } catch (RuntimeException e) {
                    return null;
                }
            }
        };
    }

    private String[] getProjection(Context context) {
        final ContactsPreferences contactsPrefs = new ContactsPreferences(context);
        final int displayOrder = contactsPrefs.getDisplayOrder();
        if (displayOrder == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            return ProfileQuery.PROFILE_PROJECTION_PRIMARY;
        }
        return ProfileQuery.PROFILE_PROJECTION_ALTERNATIVE;
    }

    public void setListener(ProfileListener listener) {
        mListener = listener;
    }
}

