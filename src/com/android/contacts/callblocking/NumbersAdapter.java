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
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.callblocking.ContactInfo;
import com.android.contacts.callblocking.ContactInfoHelper;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.util.UriUtils;

import java.util.Locale;

public class NumbersAdapter extends SimpleCursorAdapter {

    private Context mContext;
    private FragmentManager mFragmentManager;
    private ContactInfoHelper mContactInfoHelper;
    private BidiFormatter mBidiFormatter = BidiFormatter.getInstance();
    private ContactPhotoManager mContactPhotoManager;

    public NumbersAdapter(
            Context context,
            FragmentManager fragmentManager,
            ContactInfoHelper contactInfoHelper,
            ContactPhotoManager contactPhotoManager) {
        super(context, R.layout.blocked_number_item, null, new String[]{}, new int[]{}, 0);
        mContext = context;
        mFragmentManager = fragmentManager;
        mContactInfoHelper = contactInfoHelper;
        mContactPhotoManager = contactPhotoManager;
    }

    public void updateView(View view, String number, String countryIso) {
        // TODO: add touch feedback on list item.
        final TextView callerName = (TextView) view.findViewById(R.id.caller_name);
        final TextView callerNumber = (TextView) view.findViewById(R.id.caller_number);
        final QuickContactBadge quickContactBadge =
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo);
        quickContactBadge.setOverlay(null);
        if (CompatUtils.hasPrioritizedMimeType()) {
            quickContactBadge.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
        }

        ContactInfo info = mContactInfoHelper.lookupNumber(number, countryIso);
        if (info == null) {
            info = new ContactInfo();
            info.number = number;
        }
        final CharSequence locationOrType = getNumberTypeOrLocation(info);
        final String displayNumber = getDisplayNumber(info);
        final String displayNumberStr = mBidiFormatter.unicodeWrap(displayNumber,
                TextDirectionHeuristics.LTR);

        String nameForDefaultImage;
        if (!TextUtils.isEmpty(info.name)) {
            nameForDefaultImage = info.name;
            callerName.setText(info.name);
            callerNumber.setText(locationOrType + " " + displayNumberStr);
        } else {
            nameForDefaultImage = displayNumber;
            callerName.setText(displayNumberStr);
            if (!TextUtils.isEmpty(locationOrType)) {
                callerNumber.setText(locationOrType);
                callerNumber.setVisibility(View.VISIBLE);
            } else {
                callerNumber.setVisibility(View.GONE);
            }
        }
        loadContactPhoto(info, nameForDefaultImage, quickContactBadge);
    }

    private void loadContactPhoto(ContactInfo info, String displayName, QuickContactBadge badge) {
        final String lookupKey = info.lookupUri == null
                ? null : UriUtils.getLookupKeyFromUri(info.lookupUri);
        final DefaultImageRequest request = new DefaultImageRequest(displayName, lookupKey,
                ContactPhotoManager.TYPE_DEFAULT, /* isCircular */ true);
        badge.assignContactUri(info.lookupUri);
        badge.setContentDescription(
                mContext.getResources().getString(R.string.description_contact_details, displayName));
        mContactPhotoManager.loadDirectoryPhoto(badge, info.photoUri,
                /* darkTheme */ false, /* isCircular */ true, request);
    }

    private String getDisplayNumber(ContactInfo info) {
        if (!TextUtils.isEmpty(info.formattedNumber)) {
            return info.formattedNumber;
        } else if (!TextUtils.isEmpty(info.number)) {
            return info.number;
        } else {
            return "";
        }
    }

    private CharSequence getNumberTypeOrLocation(ContactInfo info) {
        if (!TextUtils.isEmpty(info.name)) {
            return Phone.getTypeLabel(
                    mContext.getResources(), info.type, info.label);
        } else {
            return FilteredNumbersUtil.getGeoDescription(mContext, info.number);
        }
    }

    protected Context getContext() {
        return mContext;
    }

    protected FragmentManager getFragmentManager() {
        return mFragmentManager;
    }
}
