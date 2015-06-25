/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.editor;

import static android.provider.ContactsContract.CommonDataKinds.Event;
import static android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import static android.provider.ContactsContract.CommonDataKinds.Photo;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;
import com.android.contacts.R;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.GoogleAccountType;
import com.android.contacts.common.model.dataitem.DataKind;
import com.google.common.collect.Maps;

import java.util.HashMap;

/**
 * Utility methods for creating contact editor.
 */
public class EditorUiUtils {

    // Maps DataKind.mimeType to editor view layouts.
    private static final HashMap<String, Integer> mimetypeLayoutMap = Maps.newHashMap();
    static {
        // Generally there should be a layout mapped to each existing DataKind mimetype but lots of
        // them use the default text_fields_editor_view which we return as default so they don't
        // need to be mapped.
        //
        // Other possible mime mappings are:
        // DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME
        // Nickname.CONTENT_ITEM_TYPE
        // Email.CONTENT_ITEM_TYPE
        // StructuredPostal.CONTENT_ITEM_TYPE
        // Im.CONTENT_ITEM_TYPE
        // Note.CONTENT_ITEM_TYPE
        // Organization.CONTENT_ITEM_TYPE
        // Phone.CONTENT_ITEM_TYPE
        // SipAddress.CONTENT_ITEM_TYPE
        // Website.CONTENT_ITEM_TYPE
        // Relation.CONTENT_ITEM_TYPE
        //
        // Un-supported mime types need to mapped with -1.

        mimetypeLayoutMap.put(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME,
                R.layout.phonetic_name_editor_view);
        mimetypeLayoutMap.put(StructuredName.CONTENT_ITEM_TYPE,
                R.layout.structured_name_editor_view);
        mimetypeLayoutMap.put(GroupMembership.CONTENT_ITEM_TYPE, -1);
        mimetypeLayoutMap.put(Photo.CONTENT_ITEM_TYPE, -1);
        mimetypeLayoutMap.put(Event.CONTENT_ITEM_TYPE, R.layout.event_field_editor_view);
    }

    /**
     * Fetches a layout for a given mimetype.
     *
     * @param mimetype The mime type (e.g. StructuredName.CONTENT_ITEM_TYPE)
     * @return The layout resource id.
     */
    public static int getLayoutResourceId(String mimetype) {
        final Integer id = mimetypeLayoutMap.get(mimetype);
        if (id == null) {
            return R.layout.text_fields_editor_view;
        }
        return id;
    }

    /**
     * Returns a Pair of the account name and type to display for the given arguments or null
     * in no account information should be displayed. The account name may also be null.
     */
    public static Pair<String,String> getAccountInfo(Context context, boolean isProfile,
            String accountName, AccountType accountType) {
        CharSequence accountTypeDisplayLabel = accountType.getDisplayLabel(context);

        if (isProfile) {
            if (TextUtils.isEmpty(accountName)) {
                return new Pair<>(
                        /* accountName =*/ null,
                        context.getString(R.string.local_profile_title));
            }
            return new Pair<>(
                    accountName,
                    context.getString(R.string.external_profile_title, accountTypeDisplayLabel));
        }
        if (!TextUtils.isEmpty(accountName)) {
            final String accountNameDisplayLabel =
                    context.getString(R.string.from_account_format, accountName);

            if (TextUtils.isEmpty(accountTypeDisplayLabel)) {
                accountTypeDisplayLabel = context.getString(R.string.account_phone);
            }

            if (GoogleAccountType.ACCOUNT_TYPE.equals(accountType.accountType)
                    && accountType.dataSet == null) {
                return new Pair<>(
                        accountNameDisplayLabel,
                        context.getString(R.string.google_account_type_format,
                                accountTypeDisplayLabel));
            }
            return new Pair<>(
                    accountNameDisplayLabel,
                    context.getString(R.string.account_type_format, accountTypeDisplayLabel));
        }
        return null;
    }

    /**
     * Returns a content description String for the container of the account information
     * returned by {@link #getAccountInfo}.
     */
    public static String getAccountInfoContentDescription(CharSequence accountName,
            CharSequence accountType) {
        final StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(accountType)) {
            builder.append(accountType).append('\n');
        }
        if (!TextUtils.isEmpty(accountName)) {
            builder.append(accountName).append('\n');
        }
        return builder.toString();
    }
}
