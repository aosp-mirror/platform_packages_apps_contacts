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

import com.android.contacts.MultiplePhonePickerActivity;
import com.android.contacts.R;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is the adapter for the phone numbers which may not be found in the contacts. It is
 * called in ContactItemListAdapter in MODE_PICK_MULTIPLE_PHONES mode and shouldn't be a adapter
 * for any View due to the missing implementation of getItem and getItemId.
 */
public class MultiplePhoneExtraAdapter extends BaseAdapter {

    private final MultiplePhonePickerActivity mMultiplePhonePickerActivity;

    public static final long INVALID_PHONE_ID = -1;

    /** The initial phone numbers */
    private List<String> mPhoneNumbers;

    /** The phone numbers after the filtering */
    private ArrayList<String> mFilteredPhoneNumbers = new ArrayList<String>();

    private final Context mContext;

    private final MultiplePhoneSelection mSelection;

    public MultiplePhoneExtraAdapter(MultiplePhonePickerActivity multiplePhonePickerActivity,
            Context context, MultiplePhoneSelection selection) {
        mContext = context;
        mMultiplePhonePickerActivity = multiplePhonePickerActivity;
        mSelection = selection;
    }

    public void setPhoneNumbers(ArrayList<String> phoneNumbers) {
        if (phoneNumbers != null) {
            mFilteredPhoneNumbers.addAll(phoneNumbers);
            mPhoneNumbers = phoneNumbers;
        } else {
            mPhoneNumbers = new ArrayList<String>();
        }
    }

    public int getCount() {
        int filteredCount = mFilteredPhoneNumbers.size();
        if (filteredCount == 0) {
            return 0;
        }
        // Count on the separator
        return 1 + filteredCount;
    }

    public Object getItem(int position) {
        // This method is not used currently.
        throw new RuntimeException("This method is not implemented");
    }

    public long getItemId(int position) {
        // This method is not used currently.
        throw new RuntimeException("This method is not implemented");
    }

    /**
     * @return the initial phone numbers, the zero length array is returned when there is no
     * initial numbers.
     */
    public final List<String> getPhoneNumbers() {
        return mPhoneNumbers;
    }

    /**
     * @return the filtered phone numbers, the zero size ArrayList is returned when there is no
     * initial numbers.
     */
    public ArrayList<String> getFilteredPhoneNumbers() {
        return mFilteredPhoneNumbers;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        int viewCount = getCount();
        if (viewCount == 0) {
            return null;
        }

        int startPos = (this.mMultiplePhonePickerActivity.mMode &
                MultiplePhonePickerActivity.MODE_MASK_SHOW_NUMBER_OF_CONTACTS) != 0 ? 1 : 0;

        // Separator
        if (position == startPos) {
            TextView view;
            if (convertView != null && convertView instanceof TextView) {
                view = (TextView) convertView;
            } else {
                LayoutInflater inflater =
                    (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = (TextView) inflater.inflate(R.layout.list_separator, parent, false);
            }
            view.setText(R.string.unknown_contacts_separator);
            return view;
        }
        // PhoneNumbers start from position of startPos + 1
        if (position >= startPos + 1 && position < startPos + viewCount) {
            View view;
            if (convertView != null) {
                view = convertView;
            } else {
                view = this.mMultiplePhonePickerActivity.mAdapter.newView(mContext, null, parent);
            }
            bindView(view, mFilteredPhoneNumbers.get(position - 1 - startPos));
            return view;
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        int startPos = (this.mMultiplePhonePickerActivity.mMode &
                MultiplePhonePickerActivity.MODE_MASK_SHOW_NUMBER_OF_CONTACTS) != 0 ? 1 : 0;

        return position == startPos ? IGNORE_ITEM_VIEW_TYPE : super.getItemViewType(position);
    }

    private void bindView(View view, final String label) {
        MultiplePhonePickerItemView itemView = (MultiplePhonePickerItemView) view;
        itemView.setDividerVisible(true);
        itemView.setSectionHeader(null);
        itemView.setLabel(null);
        itemView.setData(null, 0);
        itemView.removePhotoView();

        itemView.getNameTextView().setText(label);
        CheckBox checkBox = itemView.getCheckBoxView();
        checkBox.setChecked(mSelection.isSelected(label));
        itemView.getChipView().setBackgroundResource(0);
        itemView.phoneId = INVALID_PHONE_ID;
        itemView.phoneNumber = label;
    }

    public void doFilter(final String constraint, boolean selectedOnly) {
        if (mPhoneNumbers == null) {
            return;
        }
        mFilteredPhoneNumbers.clear();
        for (String number : mPhoneNumbers) {
            if (selectedOnly && !mSelection.isSelected(number) ||
                    !TextUtils.isEmpty(constraint) && !number.startsWith(constraint)) {
                continue;
            }
            mFilteredPhoneNumbers.add(number);
        }
    }
}