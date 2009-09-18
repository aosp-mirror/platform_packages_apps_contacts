/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.contacts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.LiveFolders;

public class ContactsLiveFolders {
    public static class StarredContacts extends Activity {
        public static final Uri CONTENT_URI =
                Uri.parse("content://contacts/live_folders/favorites");

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Intent intent = getIntent();
            final String action = intent.getAction();

            if (LiveFolders.ACTION_CREATE_LIVE_FOLDER.equals(action)) {
                setResult(RESULT_OK, createLiveFolder(this, CONTENT_URI,
                        getString(R.string.liveFolder_favorites_label),
                        R.drawable.ic_launcher_folder_live_contacts_starred));
            } else {
                setResult(RESULT_CANCELED);
            }

            finish();
        }
    }

    public static class PhoneContacts extends Activity {
        public static final Uri CONTENT_URI =
                Uri.parse("content://contacts/live_folders/people_with_phones");

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Intent intent = getIntent();
            final String action = intent.getAction();

            if (LiveFolders.ACTION_CREATE_LIVE_FOLDER.equals(action)) {
                setResult(RESULT_OK, createLiveFolder(this, CONTENT_URI,
                        getString(R.string.liveFolder_phones_label),
                        R.drawable.ic_launcher_folder_live_contacts_phone));
            } else {
                setResult(RESULT_CANCELED);
            }

            finish();
        }
    }

    public static class AllContacts extends Activity {
        public static final Uri CONTENT_URI =
                Uri.parse("content://contacts/live_folders/people");

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Intent intent = getIntent();
            final String action = intent.getAction();

            if (LiveFolders.ACTION_CREATE_LIVE_FOLDER.equals(action)) {
                setResult(RESULT_OK, createLiveFolder(this, CONTENT_URI,
                        getString(R.string.liveFolder_all_label),
                        R.drawable.ic_launcher_folder_live_contacts));
            } else {
                setResult(RESULT_CANCELED);
            }

            finish();
        }
    }

    private static Intent createLiveFolder(Context context, Uri uri, String name,
            int icon) {

        final Intent intent = new Intent();

        intent.setData(uri);
        intent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_BASE_INTENT,
                new Intent(Intent.ACTION_VIEW, Contacts.CONTENT_URI));
        intent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME, name);
        intent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_ICON,
                Intent.ShortcutIconResource.fromContext(context, icon));
        intent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE, LiveFolders.DISPLAY_MODE_LIST);

        return intent;
    }
}
