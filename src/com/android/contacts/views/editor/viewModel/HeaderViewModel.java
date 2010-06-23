/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.contacts.views.editor.viewModel;

import com.android.contacts.R;
import com.android.contacts.views.editor.DisplayRawContact;
import com.android.contacts.views.editor.view.HeaderView;
import com.android.contacts.views.editor.view.ViewTypes;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class HeaderViewModel extends BaseViewModel {
    private boolean mCollapsed;

    public HeaderViewModel(Context context, DisplayRawContact rawContact) {
        super(context, rawContact);
    }

    public boolean isCollapsed() {
        return mCollapsed;
    }

    public void setCollapsed(boolean collapsed) {
        mCollapsed = collapsed;
    }

    @Override
    public int getEntryType() {
        return ViewTypes.RAW_CONTACT_HEADER;
    }

    @Override
    public View getView(LayoutInflater inflater, ViewGroup parent) {
        final HeaderView result = HeaderView.inflate(inflater, parent, false);

        CharSequence accountType = getRawContact().getSource().getDisplayLabel(getContext());
        if (TextUtils.isEmpty(accountType)) {
            accountType = getContext().getString(R.string.account_phone);
        }
        final String accountName = getRawContact().getAccountName();

        final String accountTypeDisplay;
        if (TextUtils.isEmpty(accountName)) {
            accountTypeDisplay = getContext().getString(R.string.account_type_format,
                    accountType);
        } else {
            accountTypeDisplay = getContext().getString(R.string.account_type_and_name,
                    accountType, accountName);
        }

        result.setCaptionText(accountTypeDisplay);
        result.setLogo(getRawContact().getSource().getDisplayIcon(getContext()));

        return result;
    }
}
