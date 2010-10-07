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

package com.android.contacts.list;

import com.android.contacts.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Contact list filter parameters.
 */
public class ContactListFilterView extends LinearLayout {

    private ImageView mIcon;
    private TextView mLabel;
    private TextView mIndentedLabel;
    private ContactListFilter mFilter;

    public ContactListFilterView(Context context) {
        super(context);
    }

    public ContactListFilterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setContactListFilter(ContactListFilter filter) {
        mFilter = filter;
    }

    public ContactListFilter getContactListFilter() {
        return mFilter;
    }

    public void bindView(boolean dropdown) {
        if (mLabel == null) {
            mIcon = (ImageView) findViewById(R.id.icon);
            mLabel = (TextView) findViewById(R.id.label);
            mIndentedLabel = (TextView) findViewById(R.id.indented_label);
        }

        if (mFilter == null) {
            mLabel.setText(R.string.contactsList);
            mLabel.setVisibility(View.VISIBLE);
            return;
        }

        switch (mFilter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
                if (mIcon != null) {
                    mIcon.setVisibility(View.VISIBLE);
                    mIcon.setImageResource(R.drawable.ic_contact_list_filter_all);
                }
                mLabel.setText(R.string.list_filter_all_accounts);
                mLabel.setVisibility(View.VISIBLE);
                if (dropdown) {
                    mIndentedLabel.setVisibility(View.GONE);
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_STARRED: {
                if (mIcon != null) {
                    mIcon.setVisibility(View.VISIBLE);
                    mIcon.setImageResource(R.drawable.ic_contact_list_filter_starred);
                }
                mLabel.setText(R.string.list_filter_all_starred);
                mLabel.setVisibility(View.VISIBLE);
                if (dropdown) {
                    mIndentedLabel.setVisibility(View.GONE);
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                if (mIcon != null) {
                    mIcon.setVisibility(View.VISIBLE);
                    mIcon.setImageResource(R.drawable.ic_contact_list_filter_custom);
                }
                mLabel.setText(dropdown
                        ? R.string.list_filter_customize
                        : R.string.list_filter_custom);
                mLabel.setVisibility(View.VISIBLE);
                if (dropdown) {
                    mIndentedLabel.setVisibility(View.GONE);
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                if (mIcon != null) {
                    mIcon.setVisibility(View.VISIBLE);
                    if (mFilter.icon != null) {
                        mIcon.setImageDrawable(mFilter.icon);
                    } else {
                        mIcon.setImageResource(R.drawable.unknown_source);
                    }
                }
                mLabel.setText(mFilter.accountName);
                mLabel.setVisibility(View.VISIBLE);
                if (dropdown) {
                    mIndentedLabel.setVisibility(View.GONE);
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_GROUP: {
                if (mIcon != null) {
                    mIcon.setVisibility(View.GONE);
                }
                if (dropdown) {
                    mLabel.setVisibility(View.GONE);
                    mIndentedLabel.setText(mFilter.title);
                    mIndentedLabel.setVisibility(View.VISIBLE);
                } else {
                    mLabel.setText(mFilter.title);
                    mLabel.setVisibility(View.VISIBLE);
                }
                break;
            }
        }
    }
}
