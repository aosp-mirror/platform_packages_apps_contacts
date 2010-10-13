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
    private boolean mGroupsIndented;

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

    public void setGroupsIndented(boolean flag) {
        this.mGroupsIndented = flag;
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
                bindView(R.drawable.ic_contact_list_filter_all,
                        R.string.list_filter_all_accounts);
                break;
            }
            case ContactListFilter.FILTER_TYPE_STARRED: {
                bindView(R.drawable.ic_contact_list_filter_starred,
                        R.string.list_filter_all_starred);
                break;
            }
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                bindView(R.drawable.ic_contact_list_filter_custom,
                        dropdown ? R.string.list_filter_customize : R.string.list_filter_custom);
                break;
            }
            case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY: {
                bindView(0, R.string.list_filter_phones);
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
                    mIcon.setVisibility(View.INVISIBLE);
                }
                if (dropdown && mGroupsIndented) {
                    mLabel.setVisibility(View.GONE);
                    mIndentedLabel.setText(mFilter.title);
                    mIndentedLabel.setVisibility(View.VISIBLE);
                } else {
                    mLabel.setText(mFilter.title);
                    mLabel.setVisibility(View.VISIBLE);
                    if (dropdown) {
                        mIndentedLabel.setVisibility(View.GONE);
                    }
                }
                break;
            }
        }
    }

    private void bindView(int iconResource, int textResource) {
        if (mIcon != null) {
            if (iconResource != 0) {
                mIcon.setVisibility(View.VISIBLE);
                mIcon.setImageResource(iconResource);
            } else {
                mIcon.setVisibility(View.INVISIBLE);
            }
        }

        mLabel.setText(textResource);
        mLabel.setVisibility(View.VISIBLE);

        if (mIndentedLabel != null) {
            mIndentedLabel.setVisibility(View.GONE);
        }
    }
}
