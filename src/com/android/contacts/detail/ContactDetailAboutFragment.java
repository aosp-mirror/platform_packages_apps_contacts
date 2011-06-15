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
 * limitations under the License
 */

package com.android.contacts.detail;

import com.android.contacts.ContactLoader;
import com.android.contacts.R;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class ContactDetailAboutFragment extends ContactDetailFragment {

    private static final String TAG = "ContactDetailAboutFragment";

    public ContactDetailAboutFragment() {
        // Explicit constructor for inflation
    }

    @Override
    protected View createNewHeaderView(ViewGroup parent) {
        ViewGroup headerView = (ViewGroup) inflate(
                R.layout.simple_contact_detail_header_view_list_item, parent, false);
        TextView phoneticNameView = (TextView) headerView.findViewById(R.id.phonetic_name);
        TextView attributionView = (TextView) headerView.findViewById(R.id.attribution);
        ContactDetailDisplayUtils.setPhoneticName(getContext(), getContactData(), phoneticNameView);
        ContactDetailDisplayUtils.setAttribution(getContext(), getContactData(), attributionView);
        return headerView;
    }

    @Override
    protected void bindData() {
        ContactLoader.Result contactData = getContactData();
        if (contactData != null) {
            // Setup the activity title and subtitle with contact name and company
            Activity activity = getActivity();
            CharSequence displayName = ContactDetailDisplayUtils.getDisplayName(activity,
                    contactData);
            String company =  ContactDetailDisplayUtils.getCompany(activity, contactData);

            ActionBar actionBar = activity.getActionBar();
            actionBar.setTitle(displayName);
            actionBar.setSubtitle(company);

            // Pass the contact loader result to the listener to finish setup
            Listener listener = getListener();
            if (listener != null) {
                listener.onDetailsLoaded(contactData);
            }
        }

        super.bindData();
    }
}
