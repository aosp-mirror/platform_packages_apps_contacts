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

import com.android.contacts.R;
import com.android.contacts.model.GoogleAccountType;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorDescription;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;

/**
 * Utility class for controlling whether the standard "no account" prompt on launch is shown.
 */
public class AccountPromptUtils {

    private static final String TAG = AccountPromptUtils.class.getSimpleName();

    /** {@link SharedPreferences} key for whether or not the "no account" prompt should be shown. */
    private static final String KEY_SHOW_ACCOUNT_PROMPT = "settings.showAccountPrompt";

    /**
     * The following intent keys are understood by the {@link AccountManager} and should not be
     * changed unless the API changes.
     */
    private static final String KEY_INTRO_MESSAGE = "introMessage";
    private static final String KEY_ALLOW_SKIP_ACCOUNT_SETUP = "allowSkip";
    private static final String KEY_USER_SKIPPED_ACCOUNT_SETUP = "setupSkipped";

    private static SharedPreferences getSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Returns true if the "no account" prompt should be shown
     * (according to {@link SharedPreferences}), otherwise return false. Since this prompt is
     * Google-specific for the time being, this method will also return false if the Google
     * account type is not available from the {@link AccountManager}.
     */
    public static boolean shouldShowAccountPrompt(Context context) {
        // TODO: Remove the filtering of account types once there is an API in
        // {@link AccountManager} to show a similar account prompt
        // (see {@link AccountManager#addAccount()} in {@link #launchAccountPrompt()}
        // for any type of account. Bug: 5375902
        AuthenticatorDescription[] allTypes =
                AccountManager.get(context).getAuthenticatorTypes();
        for (AuthenticatorDescription authenticatorType : allTypes) {
            if (GoogleAccountType.ACCOUNT_TYPE.equals(authenticatorType.type)) {
                return getSharedPreferences(context).getBoolean(KEY_SHOW_ACCOUNT_PROMPT, true);
            }
        }
        return false;
    }

    /**
     * Remember to never show the "no account" prompt again by saving this to
     * {@link SharedPreferences}.
     */
    public static void neverShowAccountPromptAgain(Context context) {
        getSharedPreferences(context).edit()
                .putBoolean(KEY_SHOW_ACCOUNT_PROMPT, false)
                .apply();
    }

    /**
     * Launch the "no account" prompt. (We assume the caller has already verified that the prompt
     * can be shown, so checking the {@link #KEY_SHOW_ACCOUNT_PROMPT} value in
     * {@link SharedPreferences} will not be done in this method).
     */
    public static void launchAccountPrompt(Activity activity) {
        Bundle options = new Bundle();
        options.putCharSequence(KEY_INTRO_MESSAGE, activity.getString(R.string.no_account_prompt));
        options.putBoolean(KEY_ALLOW_SKIP_ACCOUNT_SETUP, true);
        AccountManager.get(activity).addAccount(GoogleAccountType.ACCOUNT_TYPE, null, null, options,
                activity, getAccountManagerCallback(activity), null);
    }

    private static AccountManagerCallback<Bundle> getAccountManagerCallback(
            final Activity activity) {
        return new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                if (future.isCancelled()) {
                    // The account creation process was canceled
                    activity.finish();
                    return;
                }
                try {
                    Bundle result = future.getResult();
                    if (result.getBoolean(KEY_USER_SKIPPED_ACCOUNT_SETUP)) {
                        AccountPromptUtils.neverShowAccountPromptAgain(activity);
                    }
                } catch (OperationCanceledException ignore) {
                    Log.e(TAG, "Account setup error: account creation process canceled");
                } catch (IOException ignore) {
                    Log.e(TAG, "Account setup error: No authenticator was registered for this"
                            + "account type or the authenticator failed to respond");
                } catch (AuthenticatorException ignore) {
                    Log.e(TAG, "Account setup error: Authenticator experienced an I/O problem");
                }
            }
        };
    }
}
