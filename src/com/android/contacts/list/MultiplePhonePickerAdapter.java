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

import com.android.contacts.ContactsListActivity;
import com.android.contacts.MultiplePhonePickerActivity;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class MultiplePhonePickerAdapter extends ContactItemListAdapter {

    private final MultiplePhonePickerActivity mMultiplePhonePickerActivity;
    private MultiplePhoneExtraAdapter mExtraAdapter;

    public MultiplePhonePickerAdapter(MultiplePhonePickerActivity multiplePhonePickerActivity) {
        super(multiplePhonePickerActivity);
        this.mMultiplePhonePickerActivity = multiplePhonePickerActivity;
    }

    public void setExtraAdapter(MultiplePhoneExtraAdapter extraAdapter) {
        mExtraAdapter = extraAdapter;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && mMultiplePhonePickerActivity.mShowNumberOfContacts) {
            return IGNORE_ITEM_VIEW_TYPE;
        }

        if (position < mExtraAdapter.getCount()) {
            return mExtraAdapter.getItemViewType(position);
        }

        return super.getItemViewType(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0 && mMultiplePhonePickerActivity.mShowNumberOfContacts) {
            return super.getView(position, convertView, parent);
        }

        if (position < mExtraAdapter.getCount()) {
            return mExtraAdapter.getView(position,
                    convertView, parent);
        }
        return super.getView(position, convertView, parent);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final MultiplePhonePickerItemView view = new MultiplePhonePickerItemView(context, null);
        view.setOnCallButtonClickListener(mMultiplePhonePickerActivity);
        view.setOnCheckBoxClickListener(mMultiplePhonePickerActivity.mCheckBoxClickerListener);
        return view;
    }

    @Override
    public void bindView(View itemView, Context context, Cursor cursor) {
        final MultiplePhonePickerItemView view = (MultiplePhonePickerItemView)itemView;

        int typeColumnIndex;
        int dataColumnIndex;
        int labelColumnIndex;
        int defaultType;
        int nameColumnIndex;
        int phoneticNameColumnIndex;
        int photoColumnIndex = ContactsListActivity.SUMMARY_PHOTO_ID_COLUMN_INDEX;
        boolean displayAdditionalData = mDisplayAdditionalData;
        nameColumnIndex = ContactsListActivity.PHONE_DISPLAY_NAME_COLUMN_INDEX;
        phoneticNameColumnIndex = -1;
        dataColumnIndex = ContactsListActivity.PHONE_NUMBER_COLUMN_INDEX;
        typeColumnIndex = ContactsListActivity.PHONE_TYPE_COLUMN_INDEX;
        labelColumnIndex = ContactsListActivity.PHONE_LABEL_COLUMN_INDEX;
        defaultType = Phone.TYPE_HOME;
        photoColumnIndex = ContactsListActivity.PHONE_PHOTO_ID_COLUMN_INDEX;

        view.phoneId = Long.valueOf(cursor.getLong(ContactsListActivity.PHONE_ID_COLUMN_INDEX));
        CheckBox checkBox = view.getCheckBoxView();
        checkBox.setChecked(mMultiplePhonePickerActivity.mUserSelection.isSelected(view.phoneId));
        int color = mMultiplePhonePickerActivity.getChipColor(cursor
                .getLong(ContactsListActivity.PHONE_CONTACT_ID_COLUMN_INDEX));
        view.getChipView().setBackgroundResource(color);

        // Set the name
        cursor.copyStringToBuffer(nameColumnIndex, view.nameBuffer);
        TextView nameView = view.getNameTextView();
        int size = view.nameBuffer.sizeCopied;
        if (size != 0) {
            nameView.setText(view.nameBuffer.data, 0, size);
        } else {
            nameView.setText(mUnknownNameText);
        }

        // Set the photo, if requested
        if (mDisplayPhotos) {
            boolean useQuickContact = false;

            long photoId = 0;
            if (!cursor.isNull(photoColumnIndex)) {
                photoId = cursor.getLong(photoColumnIndex);
            }

            ImageView viewToUse;
            if (useQuickContact) {
                // Build soft lookup reference
                final long contactId = cursor.getLong(ContactsListActivity.SUMMARY_ID_COLUMN_INDEX);
                final String lookupKey = cursor
                        .getString(ContactsListActivity.SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                QuickContactBadge quickContact = view.getQuickContact();
                quickContact.assignContactUri(Contacts.getLookupUri(contactId, lookupKey));
                viewToUse = quickContact;
            } else {
                viewToUse = view.getPhotoView();
            }

            final int position = cursor.getPosition();
            mMultiplePhonePickerActivity.mPhotoLoader.loadPhoto(viewToUse, photoId);
        }

        if (!displayAdditionalData) {
            if (phoneticNameColumnIndex != -1) {

                // Set the name
                cursor.copyStringToBuffer(phoneticNameColumnIndex, view.phoneticNameBuffer);
                int phoneticNameSize = view.phoneticNameBuffer.sizeCopied;
                if (phoneticNameSize != 0) {
                    view.setLabel(view.phoneticNameBuffer.data, phoneticNameSize);
                } else {
                    view.setLabel(null);
                }
            } else {
                view.setLabel(null);
            }
            return;
        }

        // Set the data.
        cursor.copyStringToBuffer(dataColumnIndex, view.dataBuffer);

        size = view.dataBuffer.sizeCopied;
        view.setData(view.dataBuffer.data, size);

        // Set the label.
        if (!cursor.isNull(typeColumnIndex)) {
            final int type = cursor.getInt(typeColumnIndex);
            final String label = cursor.getString(labelColumnIndex);
            view.setLabel(Phone.getTypeLabel(context.getResources(), type, label));
        } else {
            view.setLabel(null);
        }
    }

    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        mMultiplePhonePickerActivity.updateChipColor(cursor);
    }

    @Override
    protected void prepareEmptyView() {
        mMultiplePhonePickerActivity.mEmptyView.show(mMultiplePhonePickerActivity.mSearchMode,
                true, false, false, false, true, mMultiplePhonePickerActivity.mShowSelectedOnly);
    }

    @Override
    public int getCount() {
        if (!mDataValid) {
            return 0;
        }

        int count = super.getCount();
        count += mExtraAdapter.getCount();
        return count;
    }

    @Override
    protected int getRealPosition(int pos) {
        return pos - mExtraAdapter.getCount();
    }
}
