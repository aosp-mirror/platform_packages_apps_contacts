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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * List adapter for the multiple phone picker.
 */
public class MultiplePhonePickerAdapter extends PhoneNumberListAdapter {

    public interface OnSelectionChangeListener {
        void onSelectionChange();
    }

    private static final int[] CHIP_COLOR_ARRAY = {
        R.drawable.appointment_indicator_leftside_1,
        R.drawable.appointment_indicator_leftside_2,
        R.drawable.appointment_indicator_leftside_3,
        R.drawable.appointment_indicator_leftside_4,
        R.drawable.appointment_indicator_leftside_5,
        R.drawable.appointment_indicator_leftside_6,
        R.drawable.appointment_indicator_leftside_7,
        R.drawable.appointment_indicator_leftside_8,
        R.drawable.appointment_indicator_leftside_9,
        R.drawable.appointment_indicator_leftside_10,
        R.drawable.appointment_indicator_leftside_11,
        R.drawable.appointment_indicator_leftside_12,
        R.drawable.appointment_indicator_leftside_13,
        R.drawable.appointment_indicator_leftside_14,
        R.drawable.appointment_indicator_leftside_15,
        R.drawable.appointment_indicator_leftside_16,
        R.drawable.appointment_indicator_leftside_17,
        R.drawable.appointment_indicator_leftside_18,
        R.drawable.appointment_indicator_leftside_19,
        R.drawable.appointment_indicator_leftside_20,
        R.drawable.appointment_indicator_leftside_21,
    };

    public static final long INVALID_PHONE_ID = -1;

    /** The phone numbers */
    private ArrayList<String> mPhoneNumbers = new ArrayList<String>();

    /** The selected phone numbers in the PhoneNumberAdapter */
    private HashSet<String> mSelectedPhoneNumbers = new HashSet<String>();

    /** The phone numbers after the filtering */
    private ArrayList<String> mFilteredPhoneNumbers = new ArrayList<String>();

    /** The PHONE_ID of selected number in user contacts*/
    private HashSet<Long> mSelectedPhoneIds = new HashSet<Long>();

    private boolean mSelectionChanged;

    private OnSelectionChangeListener mSelectionChangeListener;

    /**
     * This is a map from contact ID to color index. A colored chip is used to
     * indicate the number of phone numbers belong to one contact
     */
    private SparseIntArray mContactColor = new SparseIntArray();

    public MultiplePhonePickerAdapter(Context context) {
        super(context);
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.mSelectionChangeListener = listener;
    }

    public void setPhoneNumbers(ArrayList<String> phoneNumbers) {
        mPhoneNumbers.clear();
        mPhoneNumbers.addAll(phoneNumbers);
    }

    public int getSelectedCount() {
        return mSelectedPhoneNumbers.size() + mSelectedPhoneIds.size();
    }

    public Uri[] getSelectedUris() {
        Uri[] uris = new Uri[mSelectedPhoneNumbers.size() + mSelectedPhoneIds.size()];
        int count = mPhoneNumbers.size();
        int index = 0;
        for (int i = 0; i < count; i++) {
            String phoneNumber = mPhoneNumbers.get(i);
            if (isSelected(phoneNumber)) {
                uris[index++] = Uri.parse("tel:" + phoneNumber);
            }
        }
        for (Long contactId : mSelectedPhoneIds) {
            uris[index++] = ContentUris.withAppendedId(Phone.CONTENT_URI, contactId);
        }
        return uris;
    }

    public void setSelectedUris(Uri[] uris) {
        mSelectedPhoneNumbers.clear();
        mSelectedPhoneIds.clear();
        if (uris != null) {
            for (Uri uri : uris) {
                String scheme = uri.getScheme();
                if ("tel".equals(scheme)) {
                    String phoneNumber = uri.getSchemeSpecificPart();
                    if (!mPhoneNumbers.contains(phoneNumber)) {
                        mPhoneNumbers.add(phoneNumber);
                    }
                    mSelectedPhoneNumbers.add(phoneNumber);
            } else if ("content".equals(scheme)) {
                mSelectedPhoneIds.add(ContentUris.parseId(uri));
            }
            }
        }
        mFilteredPhoneNumbers.clear();
        mFilteredPhoneNumbers.addAll(mPhoneNumbers);
    }

    public void toggleSelection(int position) {
        if (position < mFilteredPhoneNumbers.size()) {
            String phoneNumber = mPhoneNumbers.get(position);
            setPhoneSelected(phoneNumber, !isSelected(phoneNumber));
        } else {
            Cursor cursor = ((Cursor)getItem(position));
            cursor.moveToPosition(position - mFilteredPhoneNumbers.size());
            long phoneId = cursor.getLong(PHONE_ID_COLUMN_INDEX);
            setPhoneSelected(phoneId, !isSelected(phoneId));
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionChanged() {
        return mSelectionChanged;
    }

    public void setSelectionChanged(boolean flag) {
        mSelectionChanged = flag;
        if (mSelectionChangeListener != null) {
            mSelectionChangeListener.onSelectionChange();
        }
    }

    public void setPhoneSelected(final String phoneNumber, boolean selected) {
        if (!TextUtils.isEmpty(phoneNumber)) {
            if (selected) {
                mSelectedPhoneNumbers.add(phoneNumber);
            } else {
                mSelectedPhoneNumbers.remove(phoneNumber);
            }
        }
        setSelectionChanged(true);
    }

    public void setPhoneSelected(long phoneId, boolean selected) {
        if (selected) {
            mSelectedPhoneIds.add(phoneId);
        } else {
            mSelectedPhoneIds.remove(phoneId);
        }
        setSelectionChanged(true);
    }

    public boolean isSelected(long phoneId) {
        return mSelectedPhoneIds.contains(phoneId);
    }

    public boolean isSelected(final String phoneNumber) {
        return mSelectedPhoneNumbers.contains(phoneNumber);
    }

    public void setAllPhonesSelected(boolean selected) {
//        if (selected) {
//            Cursor cursor = this.mMultiplePhoneSelectionActivity.mAdapter.getCursor();
//            if (cursor != null) {
//                int backupPos = cursor.getPosition();
//                cursor.moveToPosition(-1);
//                while (cursor.moveToNext()) {
//                    setPhoneSelected(cursor
//                            .getLong(MultiplePhonePickerActivity.PHONE_ID_COLUMN_INDEX), true);
//                }
//                cursor.moveToPosition(backupPos);
//            }
//            for (String number : this.mMultiplePhoneSelectionActivity.mPhoneNumberAdapter
//                    .getFilteredPhoneNumbers()) {
//                setPhoneSelected(number, true);
//            }
//        } else {
//            mSelectedPhoneIds.clear();
//            mSelectedPhoneNumbers.clear();
//        }
    }

    public boolean isAllSelected() {
        return false;
//        return selectedCount() == this.mMultiplePhoneSelectionActivity.mPhoneNumberAdapter
//                .getFilteredPhoneNumbers().size()
//                + this.mMultiplePhoneSelectionActivity.mAdapter.getCount();
    }

    public Iterator<Long> getSelectedPhoneIds() {
        return mSelectedPhoneIds.iterator();
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return position < mPhoneNumbers.size() ? 0 : 1;
    }

    // TODO redo as two separate partitions
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
//        if (convertView == null || convertView.getTag() == null) {
//            view = newView(getContext(), null, parent);
//        } else {
//            view = convertView;
//        }
//
//        boolean showingSuggestion = false;
//
//        if (position < mFilteredPhoneNumbers.size()) {
//            bindExtraPhoneView(view, position);
//        } else {
//            Cursor cursor = ((Cursor)getItem(position));
//            cursor.moveToPosition(position - mFilteredPhoneNumbers.size());
//            bindView(view, getContext(), cursor);
//        }
        return view;
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        final MultiplePhonePickerItemView view = new MultiplePhonePickerItemView(context, null);
        view.setUnknownNameText(getUnknownNameText());
        view.setTextWithHighlightingFactory(getTextWithHighlightingFactory());
        return view;
    }

    private void bindExtraPhoneView(View itemView, int position) {
        final MultiplePhonePickerItemView view = (MultiplePhonePickerItemView)itemView;
        String phoneNumber = mFilteredPhoneNumbers.get(position);
        view.getNameTextView().setText(phoneNumber);
        CheckBox checkBox = view.getCheckBoxView();
        checkBox.setChecked(isSelected(phoneNumber));
        view.phoneId = INVALID_PHONE_ID;
        view.phoneNumber = phoneNumber;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);

        final MultiplePhonePickerItemView view = (MultiplePhonePickerItemView)itemView;
        view.phoneId = Long.valueOf(cursor.getLong(PHONE_ID_COLUMN_INDEX));
        CheckBox checkBox = view.getCheckBoxView();
        checkBox.setChecked(isSelected(view.phoneId));

        long contactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
        view.getChipView().setBackgroundResource(getChipColor(contactId));
    }

//    @Override
//    protected void prepareEmptyView() {
//        mMultiplePhonePickerActivity.mEmptyView.show(mMultiplePhonePickerActivity.mSearchMode,
//                true, false, false, false, true, mMultiplePhonePickerActivity.mShowSelectedOnly);
//    }

    /**
     * Get assigned chip color resource id for a given contact, 0 is returned if there is no mapped
     * resource.
     */
    public int getChipColor(long contactId) {
        return mContactColor.get((int)contactId);
    }

    // TODO filtering
//    public void doFilter(final String constraint, boolean selectedOnly) {
//        if (mPhoneNumbers == null) {
//            return;
//        }
//        mFilteredPhoneNumbers.clear();
//        for (String number : mPhoneNumbers) {
//            if (selectedOnly && !mSelection.isSelected(number) ||
//                    !TextUtils.isEmpty(constraint) && !number.startsWith(constraint)) {
//                continue;
//            }
//            mFilteredPhoneNumbers.add(number);
//        }
//    }

    @Override
    public int getCount() {
        return super.getCount() + mFilteredPhoneNumbers.size();
    }

    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        updateChipColor(cursor);
    }

    /**
     * Go through the cursor and assign the chip color to contact who has more
     * than one phone numbers. Assume the cursor is clustered by CONTACT_ID.
     */
    public void updateChipColor(Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            return;
        }
        mContactColor.clear();
        cursor.moveToFirst();
        int colorIndex = 0;
        long prevContactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
        while (cursor.moveToNext()) {
            long contactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
            if (prevContactId == contactId) {
                if (mContactColor.indexOfKey((int)contactId) < 0) {
                    mContactColor.put((int)contactId, CHIP_COLOR_ARRAY[colorIndex]);
                    colorIndex++;
                    if (colorIndex >= CHIP_COLOR_ARRAY.length) {
                        colorIndex = 0;
                    }
                }
            }
            prevContactId = contactId;
        }
    }
}
