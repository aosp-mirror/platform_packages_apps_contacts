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

package com.android.contacts.callblocking;

import android.app.FragmentManager;
import android.database.Cursor;
import android.content.Context;
import android.view.View;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.R;
import com.android.contacts.callblocking.ContactInfoHelper;
import com.android.contacts.callblocking.FilteredNumbersUtil;
import com.android.contacts.callblocking.NumbersAdapter;

public class ViewNumbersToImportAdapter extends NumbersAdapter {

    private ViewNumbersToImportAdapter(
            Context context,
            FragmentManager fragmentManager,
            ContactInfoHelper contactInfoHelper,
            ContactPhotoManager contactPhotoManager) {
        super(context, fragmentManager, contactInfoHelper, contactPhotoManager);
    }

    public static ViewNumbersToImportAdapter newViewNumbersToImportAdapter(
            Context context, FragmentManager fragmentManager) {
        return new ViewNumbersToImportAdapter(
                context,
                fragmentManager,
                new ContactInfoHelper(context, GeoUtil.getCurrentCountryIso(context)),
                ContactPhotoManager.getInstance(context));
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        super.bindView(view, context, cursor);

        final String number = cursor.getString(
                FilteredNumbersUtil.PhoneQuery.NUMBER_COLUMN_INDEX);

        view.findViewById(R.id.delete_button).setVisibility(View.GONE);
        updateView(view, number, /* countryIso */ null);
    }
}