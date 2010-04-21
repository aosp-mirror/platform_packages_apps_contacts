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

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * This class is used to keep the user's selection in MODE_PICK_MULTIPLE_PHONES mode.
 */
public class MultiplePhoneSelection {

    private final MultiplePhonePickerActivity mMultiplePhoneSelectionActivity;

    private static final String TEL_SCHEME = "tel";

    public static final String EXTRA_SELECTION =
        "com.android.contacts.MultiplePhoneSelectionActivity.UserSelection.extra.SELECTION";
    private static final String SELECTED_UNKNOWN_PHONES_KEY = "selected_unknown_phones";
    private static final String SELECTED_PHONE_IDS_KEY = "selected_phone_id";

    /** The PHONE_ID of selected number in user contacts*/
    private HashSet<Long> mSelectedPhoneIds = new HashSet<Long>();

    /** The selected phone numbers in the PhoneNumberAdapter */
    private HashSet<String> mSelectedPhoneNumbers = new HashSet<String>();

    public MultiplePhoneSelection(MultiplePhonePickerActivity multiplePhonePickerActivity) {
        this.mMultiplePhoneSelectionActivity = multiplePhonePickerActivity;
    }

    public void saveInstanceState(Bundle icicle) {
        int selectedUnknownsCount = mSelectedPhoneNumbers.size();
        if (selectedUnknownsCount > 0) {
            String[] selectedUnknows = new String[selectedUnknownsCount];
            icicle.putStringArray(SELECTED_UNKNOWN_PHONES_KEY,
                    mSelectedPhoneNumbers.toArray(selectedUnknows));
        }
        int selectedKnownsCount = mSelectedPhoneIds.size();
        if (selectedKnownsCount > 0) {
            long[] selectedPhoneIds = new long [selectedKnownsCount];
            int index = 0;
            for (Long phoneId : mSelectedPhoneIds) {
                selectedPhoneIds[index++] = phoneId.longValue();
            }
            icicle.putLongArray(SELECTED_PHONE_IDS_KEY, selectedPhoneIds);

        }
    }

    public void restoreInstanceState(Bundle icicle) {
        if (icicle != null) {
            setSelection(icicle.getStringArray(SELECTED_UNKNOWN_PHONES_KEY),
                    icicle.getLongArray(SELECTED_PHONE_IDS_KEY));
        }
    }

    public void setSelection(final String[] selecedUnknownNumbers, final long[] selectedPhoneIds) {
        if (selecedUnknownNumbers != null) {
            for (String number : selecedUnknownNumbers) {
                setPhoneSelected(number, true);
            }
        }
        if (selectedPhoneIds != null) {
            for (long id : selectedPhoneIds) {
                setPhoneSelected(id, true);
            }
        }
    }

    public void setSelection(final List<String> selecedUnknownNumbers,
            final List<Long> selectedPhoneIds) {
        if (selecedUnknownNumbers != null) {
            setPhoneNumbersSelected(selecedUnknownNumbers, true);
        }
        if (selectedPhoneIds != null) {
            setPhoneIdsSelected(selectedPhoneIds, true);
        }
    }

    private void setPhoneNumbersSelected(final List<String> phoneNumbers, boolean selected) {
        if (selected) {
            mSelectedPhoneNumbers.addAll(phoneNumbers);
        } else {
            mSelectedPhoneNumbers.removeAll(phoneNumbers);
        }
    }

    private void setPhoneIdsSelected(final List<Long> phoneIds, boolean selected) {
        if (selected) {
            mSelectedPhoneIds.addAll(phoneIds);
        } else {
            mSelectedPhoneIds.removeAll(phoneIds);
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
    }

    public void setPhoneSelected(long phoneId, boolean selected) {
        if (selected) {
            mSelectedPhoneIds.add(phoneId);
        } else {
            mSelectedPhoneIds.remove(phoneId);
        }
    }

    public boolean isSelected(long phoneId) {
        return mSelectedPhoneIds.contains(phoneId);
    }

    public boolean isSelected(final String phoneNumber) {
        return mSelectedPhoneNumbers.contains(phoneNumber);
    }

    public void setAllPhonesSelected(boolean selected) {
        if (selected) {
            Cursor cursor = this.mMultiplePhoneSelectionActivity.mAdapter.getCursor();
            if (cursor != null) {
                int backupPos = cursor.getPosition();
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    setPhoneSelected(cursor
                            .getLong(MultiplePhonePickerActivity.PHONE_ID_COLUMN_INDEX), true);
                }
                cursor.moveToPosition(backupPos);
            }
            for (String number : this.mMultiplePhoneSelectionActivity.mPhoneNumberAdapter
                    .getFilteredPhoneNumbers()) {
                setPhoneSelected(number, true);
            }
        } else {
            mSelectedPhoneIds.clear();
            mSelectedPhoneNumbers.clear();
        }
    }

    public boolean isAllSelected() {
        return selectedCount() == this.mMultiplePhoneSelectionActivity.mPhoneNumberAdapter
                .getFilteredPhoneNumbers().size()
                + this.mMultiplePhoneSelectionActivity.mAdapter.getCount();
    }

    public int selectedCount() {
        return mSelectedPhoneNumbers.size() + mSelectedPhoneIds.size();
    }

    public Iterator<Long> getSelectedPhonIds() {
        return mSelectedPhoneIds.iterator();
    }

    private int fillSelectedNumbers(Uri[] uris, int from) {
        int count = mSelectedPhoneNumbers.size();
        if (count == 0)
            return from;
        // Below loop keeps phone numbers by initial order.
        List<String> phoneNumbers = this.mMultiplePhoneSelectionActivity.mPhoneNumberAdapter
                .getPhoneNumbers();
        for (String phoneNumber : phoneNumbers) {
            if (isSelected(phoneNumber)) {
                Uri.Builder ub = new Uri.Builder();
                ub.scheme(TEL_SCHEME);
                ub.encodedOpaquePart(phoneNumber);
                uris[from++] = ub.build();
            }
        }
        return from;
    }

    private int fillSelectedPhoneIds(Uri[] uris, int from) {
        int count = mSelectedPhoneIds.size();
        if (count == 0)
            return from;
        Iterator<Long> it = mSelectedPhoneIds.iterator();
        while (it.hasNext()) {
            uris[from++] = ContentUris.withAppendedId(Phone.CONTENT_URI, it.next());
        }
        return from;
    }

    private Uri[] getSelected() {
        Uri[] uris = new Uri[mSelectedPhoneNumbers.size() + mSelectedPhoneIds.size()];
        int from  = fillSelectedNumbers(uris, 0);
        fillSelectedPhoneIds(uris, from);
        return uris;
    }

    public Intent createSelectionIntent() {
        Intent intent = new Intent();
        intent.putExtra(Intents.EXTRA_PHONE_URIS, getSelected());

        return intent;
    }

    public void fillSelectionForSearchMode(Bundle bundle) {
        bundle.putParcelableArray(EXTRA_SELECTION, getSelected());
    }
}