package com.android.contacts.common.preference;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.android.contacts.common.R;
import com.android.contacts.common.util.ImplicitIntentsUtil;

public class AccountsPreferenceFragment extends PreferenceFragment {
    public static AccountsPreferenceFragment newInstance() {
        return new AccountsPreferenceFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_accounts);

        final Preference addAccountPreference = findPreference("addAccount");
        addAccountPreference.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Intent intent = ImplicitIntentsUtil.getIntentForAddingAccount();
                ImplicitIntentsUtil.startActivityOutsideApp(getContext(), intent);
                return true;
            }
        });
    }

    @Override
    public Context getContext() {
        return getActivity();
    }
}
