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

package com.android.contacts.group;

import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

public class GroupDetailDisplayUtils {

    private GroupDetailDisplayUtils() {
        // Disallow explicit creation of this class.
    }

    public static View getNewGroupSourceView(Context context) {
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.group_source_button, null);
    }

    public static void bindGroupSourceView(Context context, View view, String accountTypeString,
            String dataSet) {
        ImageView accountIcon = (ImageView) view.findViewById(android.R.id.icon);
        if (accountIcon == null) {
            throw new IllegalStateException("Group source view must contain view with id"
                    + "android.R.id.icon");
        }
        AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(context);
        AccountType accountType = accountTypeManager.getAccountType(accountTypeString, dataSet);
        accountIcon.setImageDrawable(accountType.getDisplayIcon(context));
    }
}